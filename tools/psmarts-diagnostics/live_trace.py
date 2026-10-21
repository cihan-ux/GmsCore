#!/usr/bin/env python3
"""Capture privacy-safe, time-ordered Play Store / Huawei event hints from ADB.

Captures ONLY classifications and relative time buckets; never writes raw logcat.
Read-only: does not call logcat -c, modify app permissions, or launch applications.
"""
# SPDX-License-Identifier: Apache-2.0
import argparse
import collections
import json
import os
from pathlib import Path
import re
import selectors
import subprocess
import sys
import time

MAX_SAMPLE_EVENTS = 120
MAX_SAMPLES_PER_KIND = 12
MAX_PENDING_BYTES = 131072
LOG_TAG = re.compile(r"\b[VDIWEF]/([A-Za-z0-9_.-]+)\s*\(\s*\d+\s*\):")
VALID_KINDS = (
    "gms_signature_rejected", "huawei_service_start", "huawei_binder_denied",
    "gms_auth_request", "gms_checkin_hint", "finsky_catalog_hint",
    "finsky_install_hint", "finsky_network_error_hint", "download_component_hint",
    "installer_component_hint", "install_failed_hint", "vending_permission_denial"
)


def classify_log_line(line):
    """Use only fixed patterns/tags; no excerpts, identities, tokens or URL values."""
    match = LOG_TAG.search(line)
    if not match:
        return None
    tag = match.group(1)
    # Do not trust even a tag until it matches a known allowlist.
    if "requires Google Play services, but their signature is invalid" in line:
        if tag == "GooglePlayServicesUtil":
            return "gms_signature_rejected"
    if tag == "SignatureService":
        if "Illegal access from app" in line:
            return "huawei_binder_denied"
        if "onStartCommand" in line:
            return "huawei_service_start"
    if tag == "GmsAuthenticator" and "getAuthToken" in line:
        return "gms_auth_request"
    if tag in ("GmsCheckinSvc", "CheckinManager", "CheckinService"):
        if re.search(r"Checked in as|registration succeeded", line, re.I):
            return "gms_checkin_hint"
    if tag in ("Finsky", "Phonesky", "PlayCommon"):
        if re.search(r"(?i)\b(?:VolleyError|HTTP 4\d\d|HTTP 5\d\d|TimeoutError|NetworkError)\b", line):
            return "finsky_network_error_hint"
        if re.search(r"(?i)\b(?:install|downloading|download|delivery|acquire|acquisition)\b", line):
            return "finsky_install_hint"
        if re.search(r"(?i)\b(?:Page .*loaded|Selecting account)\b", line):
            return "finsky_catalog_hint"
    if re.search(r"\bINSTALL_FAILED(?:_[A-Z_]+)?\b", line):
        if tag in ("PackageManager", "PackageInstaller", "PackageInstallerSession", "Finsky", "Phonesky"):
            return "install_failed_hint"
    if tag in ("DownloadManager", "DownloadProvider", "DownloadThread", "DownloadService"):
        return "download_component_hint"
    if tag in ("PackageInstaller", "PackageManager", "PackageInstallerSession", "PackageInstallerService"):
        if re.search(r"(?i)\b(?:install|session|commit|failure|failed)\b", line):
            return "installer_component_hint"
    if tag in ("Finsky", "Phonesky", "PackageManager", "PackageInstaller"):
        if ("Permission Denial" in line or "SecurityException" in line) and (
            "com.android.vending" in line or tag in ("Finsky", "Phonesky")
        ):
            return "vending_permission_denial"
    return None


class SafeTimeline:
    def __init__(self):
        self.counts = collections.Counter()
        self.bins = {}
        self.samples = []
        self.sample_counts = collections.Counter()

    def add(self, line, elapsed):
        kind = classify_log_line(line)
        if kind is None:
            return
        # Defensive guard against untrusted input causing arbitrary output keys.
        if kind not in VALID_KINDS:
            raise ValueError("Unexpected event kind")
        elapsed = max(0.0, float(elapsed))
        self.counts[kind] += 1
        bucket = str(int(elapsed // 5) * 5)
        self.bins.setdefault(bucket, collections.Counter())[kind] += 1
        if (len(self.samples) < MAX_SAMPLE_EVENTS and
                self.sample_counts[kind] < MAX_SAMPLES_PER_KIND):
            self.samples.append({"relative_seconds": round(elapsed, 1), "kind": kind})
            self.sample_counts[kind] += 1

    def result(self):
        return {
            "counts": {kind: self.counts.get(kind, 0) for kind in VALID_KINDS},
            "five_second_buckets": {k: dict(v) for k, v in sorted(self.bins.items(), key=lambda x: int(x[0]))},
            "event_samples": self.samples
        }


def capture(serial=None, seconds=45, popen=subprocess.Popen, now=time.monotonic):
    """Read a time-bounded logcat stream, classify lines; discard originals."""
    if not 10 <= seconds <= 180:
        raise ValueError("seconds must be between 10 and 180")
    prefix = ["adb"] + (["-s", serial] if serial else [])
    # -T 1 starts near the live tail without clearing logcat.
    command = prefix + ["logcat", "-b", "main", "-b", "system", "-T", "1", "-v", "brief"]
    timeline = SafeTimeline()
    proc = popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)
    start = now()
    pending = b""
    premature_exit = False
    selector = selectors.DefaultSelector()
    try:
        if proc.stdout is None:
            raise RuntimeError("Logcat stream unavailable")
        selector.register(proc.stdout, selectors.EVENT_READ)
        while now() - start < seconds:
            if proc.poll() is not None:
                premature_exit = True
                break
            timeout = min(0.25, max(0.01, seconds - (now() - start)))
            if not selector.select(timeout):
                continue
            chunk = os.read(proc.stdout.fileno(), 65536)
            if not chunk:
                premature_exit = True
                break
            pending += chunk
            if len(pending) > MAX_PENDING_BYTES:
                # Never save excessive raw data. Drop an oversized fragment.
                pending = b""
                continue
            *lines, pending = pending.split(b"\n")
            elapsed = now() - start
            for line in lines:
                timeline.add(line.decode("utf-8", errors="replace"), elapsed)
    finally:
        selector.close()
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=3)
        if proc.stdout is not None:
            proc.stdout.close()
    result = timeline.result()
    result.update({
        "schema": 1,
        "collector_status": "logcat_stopped_early" if premature_exit else "capture_window_completed",
        "capture_seconds": round(max(0.0, now() - start), 1),
        "requested_seconds": seconds,
        "raw_logs_written": False,
        "huawei_firmware_integration": "unknown",
        "playstore_download_cause": "unknown",
        "cautions": [
            "A service-start hint is not proof that Huawei firmware queried signature.",
            "Events from different processes are not automatically one causal chain.",
            "Missing log events may reflect OEM filtering or buffering, not absence of activity.",
            "No new PackageInstaller log is not proof that no session was created.",
            "These log hints must be compared with before/after installer session snapshots."
        ]
    })
    return result


def preflight(serial=None):
    prefix = ["adb"] + (["-s", serial] if serial else [])
    try:
        command = prefix + ["get-state"]
        result = subprocess.run(command, capture_output=True, timeout=10,
                                check=False, text=True, errors="replace")
    except (OSError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0 and result.stdout.strip() == "device"


def private_output(path, value):
    flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    fd = os.open(Path(path), flags, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as out:
        json.dump(value, out, ensure_ascii=False, indent=2, allow_nan=False)
        out.write("\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB serial to choose among devices; never recorded")
    parser.add_argument("--seconds", type=int, default=45, help="capture window, 10–180 seconds")
    parser.add_argument("--output", required=True, help="new private JSON file path")
    args = parser.parse_args(argv)
    if not 10 <= args.seconds <= 180:
        parser.error("--seconds must be between 10 and 180")
    if not preflight(args.serial):
        print("ADB device unavailable or ambiguous; check USB debugging/serial.", file=sys.stderr)
        return 1
    print("Capturing sanitized event categories. Tap Install once on phone during this window.")
    try:
        result = capture(serial=args.serial, seconds=args.seconds)
        private_output(args.output, result)
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError):
        print("Capture failed; check ADB and use a new private output path.", file=sys.stderr)
        return 1
    print("Sanitized report saved. Check collector_status and review file before sharing.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
