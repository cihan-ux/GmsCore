#!/usr/bin/env python3
"""Read-only Android 10 diagnostics. Raw logs/dumps are discarded; opt-in APKs are temporary."""
# SPDX-License-Identifier: Apache-2.0
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

PACKAGES = ("com.google.android.gms", "com.android.vending", "com.google.android.gsf")
INSTALL_PERMS = ("android.permission.INSTALL_PACKAGES",
                 "android.permission.REQUEST_INSTALL_PACKAGES")
SYSTEM_PACKAGES = ("com.android.providers.downloads", "com.android.providers.downloads.ui",
                   "com.android.packageinstaller", "com.google.android.packageinstaller",
                   "com.huawei.android.packageinstaller")
HUAWEI_COMPONENTS = ("com.huawei.signature.diff.InitReceiver",
                     "com.huawei.signature.diff.InitProvider",
                     "com.huawei.signature.diff.SignatureService")
LOG_PATTERNS = {
    "gms_signature_invalid": r"requires Google Play services, but their signature is invalid",
    "gms_adid_unavailable": r"Wasn't able to fetch the adId",
    "install_failed": r"INSTALL_FAILED",
    "permission_denial": r"Permission Denial|SecurityException",
    "download_failure": r"(?:download.{0,60}(?:failed|failure|error)|(?:failed|failure|error).{0,60}download)",
    "package_installer_event": r"PackageInstaller",
    "google_auth_request": r"GmsAuthenticator.{0,60}getAuthToken",
    "checkin_success_hint": r"GmsCheckinSvc.{0,60}(?:Checked in as|Device registration succeeded)",
    "huawei_service_start_hint": r"SignatureService.{0,60}onStartCommand",
    "huawei_service_rejected_caller": r"SignatureService.{0,60}Illegal access from app",
}
# Only fields produced by the collector enter reports. No arbitrary log text is redacted
# using a blacklist: it is discarded entirely, including unknown token formats.
ERROR_TEXT = re.compile(r"Permission Denial|SecurityException|Unknown command|Can't find service|Error:", re.I)


def _match(pattern, body):
    match = re.search(pattern, body or "")
    return match.group(1) if match else None


def _safe_metadata(value):
    return value if value and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._()+, \[\]-]{0,99}", value) else None


class Adb:
    """No remote command is user-supplied; serial is passed as a local adb option."""
    def __init__(self, serial=None):
        self.prefix = ["adb"] + (["-s", serial] if serial else [])

    def __call__(self, *parts):
        try:
            result = subprocess.run(self.prefix + list(parts), text=True, errors="replace",
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    timeout=30, check=False)
        except (OSError, subprocess.TimeoutExpired):
            raise RuntimeError("ADB unavailable or timed out") from None
        if result.returncode:
            # Device serials, authentication data and remote stderr must not escape.
            raise RuntimeError("ADB read failed")
        return result.stdout


def adb(*parts):
    return Adb()(*parts)


def parse_package(body):
    """Keep installed package metadata, not hidden factory-package state."""
    body = (body or "").split("Hidden system packages:", 1)[0]
    requested = body.split("requested permissions:", 1)
    requested_text = requested[1].split("install permissions:", 1)[0] if len(requested) > 1 else ""
    location = _match(r"(?m)^\s*codePath=(\S+)", body) or ""
    if location.startswith("/data/app/"):
        location_class = "data_app"  # Updated system apps can also live here.
    elif location.startswith(("/system/priv-app/", "/product/priv-app/", "/system_ext/priv-app/")):
        location_class = "priv_app"
    elif location.startswith(("/system/", "/product/", "/vendor/", "/system_ext/", "/odm/", "/oem/")):
        location_class = "system_partition"
    else:
        location_class = "unknown"
    flags = _match(r"(?m)^\s*(?:pkgFlags|flags)=\[([^\]]*)\]", body)
    private = _match(r"(?m)^\s*privateFlags=\[([^\]]*)\]", body)
    return {
        "version": _safe_metadata(_match(r"(?m)^\s*versionName=([^\r\n]+)", body)),
        "version_code": _match(r"\bversionCode=(\d+)", body),
        "target_sdk": _match(r"\btargetSdk=(\d+)", body),
        "location_class": location_class,
        "system_flag": "SYSTEM" in flags.split() if flags is not None else None,
        "updated_system_flag": "UPDATED_SYSTEM_APP" in flags.split() if flags is not None else None,
        "privileged_flag": "PRIVILEGED" in private.split() if private is not None else None,
        "requested_install_permissions": {
            perm: bool(re.search(r"(?m)^\s*" + re.escape(perm) + r"\s*$", requested_text))
            if len(requested) > 1 else None for perm in INSTALL_PERMS
        },
    }


def parse_sessions(body):
    """Do not conflate historical sessions, different users or absent fields."""
    chunks = re.split(r"(?m)^\s*Session (\d+):\s*$", body or "")
    sessions = []
    prefix = chunks[0]
    for idx in range(1, len(chunks), 2):
        detail = chunks[idx + 1]
        headings = list(re.finditer(r"(?m)^\s*(Active|Historical|Legacy) install sessions:", prefix))
        bucket = headings[-1].group(1).lower() if headings else "unknown"
        record = re.split(r"(?m)^\s*(?:Active|Historical|Legacy) install sessions:", detail)[0]
        if re.search(r"\bmInstallerPackageName=com\.android\.vending(?=\s|$)", record):
            def number(key):
                value = _match(r"\b" + key + r"=(-?\d+)(?=\s|$)", record)
                return int(value) if value is not None else None
            def boolean(key):
                value = _match(r"\b" + key + r"=(true|false)\b", record)
                return value == "true" if value is not None else None
            progress = _match(r"\bmProgress=(0(?:\.\d+)?|1(?:\.0+)?)(?=\s|$)", record)
            sessions.append({"id": int(chunks[idx]), "bucket": bucket,
                             "user_id": number("userId"), "installer_uid": number("mInstallerUid"),
                             "progress": float(progress) if progress is not None else None,
                             "committed": boolean("mCommitted"), "sealed": boolean("mSealed"),
                             "final_status": number("mFinalStatus")})
        prefix += "\n" + detail
    return sorted(sessions, key=lambda session: (session["id"], session["bucket"]))


def summarize_logcat(body):
    return {key: len(re.findall(pattern, body, re.I)) for key, pattern in LOG_PATTERNS.items()}


def parse_uid(body, package_name):
    value = _match(r"(?m)^package:" + re.escape(package_name) + r"\s+uid:(\d+)\s*$", body)
    return int(value) if value is not None else None


def _get_permission(runner, package, permission, user=0):
    try:
        response = runner("shell", "dumpsys", "package", "check-permission",
                          permission, package, str(user)).strip()
        return {"0": "granted", "-1": "denied"}.get(response, "unknown")
    except RuntimeError:
        return "unknown"


def inspect_apks(runner, package, apksigner, user=0):
    """Opt-in local APK verification; pull reads device files and never installs."""
    if not apksigner:
        return {"status": "apksigner_unavailable"}
    try:
        paths = runner("shell", "pm", "path", "--user", str(user), package).splitlines()
        paths = [line[len("package:"):] for line in paths if line.startswith("package:")]
        if not paths or any(not re.fullmatch(r"/(?:data/app|system|product|vendor|odm|oem|system_ext)/[A-Za-z0-9_./=+@~\-]+\.apk", p) for p in paths):
            return {"status": "unavailable_or_unsupported_path"}
        artifacts = []
        with tempfile.TemporaryDirectory(prefix="psmarts-apks-") as directory:
            for index, remote in enumerate(paths):
                local = Path(directory) / (str(index) + ".apk")
                runner("pull", remote, str(local))
                result = subprocess.run([apksigner, "verify", "--print-certs", str(local)],
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        text=True, errors="replace", timeout=60, check=False)
                digests = re.findall(r"(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", result.stdout)
                sha1_digests = re.findall(r"(?m)^Signer #\d+ certificate SHA-1 digest: ([0-9a-fA-F]{40})\s*$", result.stdout)
                artifacts.append({"part": index, "sha256": hashlib.sha256(local.read_bytes()).hexdigest(),
                                  "signature_verified": result.returncode == 0,
                                  "signer_sha256": sorted(set(d.lower() for d in digests)),
                                  "signer_sha1": sorted(set(d.lower() for d in sha1_digests))})
        return {"status": "inspected", "artifacts": artifacts,
                "scope": "APK bytes only; not caller-visible PackageManager signatures"}
    except (RuntimeError, OSError, subprocess.TimeoutExpired):
        return {"status": "read_or_verification_unavailable"}


def _is_failed_read(label, args, output):
    """Some successful dumpsys package outputs contain embedded error strings.

    Do not drop a complete package record just because one of its component,
    resolver or diagnostics sections contains an unrelated "Error:" or
    "SecurityException". A positive package header or core package metadata
    establishes that the package dump can still be parsed for allowlisted fields.
    """
    if label.startswith("package.") and args[:3] == ("shell", "dumpsys", "package"):
        package = label.removeprefix("package.")
        explicit_header = re.search(
            r"(?m)^\s*Package \[" + re.escape(package) + r"\](?:\s|:)", output
        )
        metadata = re.search(r"(?m)^\s*(?:codePath=|versionCode=)", output)
        if explicit_header or metadata:
            return False
    return bool(ERROR_TEXT.search(output))


def snapshot(runner=adb, include_logcat=True, user=0, inspect=False, apksigner=None):
    status = {}
    def read(label, *args):
        try:
            value = runner(*args)
        except RuntimeError:
            status[label] = "unavailable"
            return None
        if args[0] != "logcat" and _is_failed_read(label, args, value):
            status[label] = "error_response"
            return None
        status[label] = "ok"
        return value

    properties = {}
    for label, prop in (("model", "ro.product.model"), ("android", "ro.build.version.release"),
                        ("api", "ro.build.version.sdk"), ("emui", "ro.build.version.emui"),
                        ("build", "ro.build.display.id")):
        raw = read("device." + label, "shell", "getprop", prop)
        properties[label] = _safe_metadata(raw.strip()) if raw is not None else None
    uids = read("package_uids", "shell", "pm", "list", "packages", "--user", str(user), "-U")
    installed = read("package_list", "shell", "pm", "list", "packages", "--user", str(user))
    disabled = read("disabled_packages", "shell", "pm", "list", "packages", "--user", str(user), "-d")
    installed_names = set(re.findall(r"(?m)^package:([A-Za-z0-9._]+)", installed or ""))
    disabled_names = set(re.findall(r"(?m)^package:([A-Za-z0-9._]+)", disabled or ""))
    packages = {}
    gms_dump = None
    for package in PACKAGES:
        info = {"installed": package in installed_names if installed is not None else None,
                "uid": parse_uid(uids, package), "disabled": package in disabled_names if disabled is not None else None}
        if info["installed"]:
            raw = read("package." + package, "shell", "dumpsys", "package", package)
            if raw is not None:
                info.update(parse_package(raw))
            if package == PACKAGES[0]:
                gms_dump = raw
            if inspect:
                info["apk_verification"] = inspect_apks(runner, package, apksigner, user)
        packages[package] = info
    play = PACKAGES[1]
    packages[play]["install_permissions_effective"] = {
        perm: _get_permission(runner, play, perm, user) for perm in INSTALL_PERMS
    } if packages[play]["installed"] else {}
    appops = read("vending_appops", "shell", "cmd", "appops", "get", "--user", str(user), play, "REQUEST_INSTALL_PACKAGES")
    appops_mode = _match(r"REQUEST_INSTALL_PACKAGES:\s*(allow|deny|ignore|default|foreground|errored)\b", appops)
    if appops_mode is None:
        appops_mode = "not_recorded" if appops is not None and "No operations" in appops else "unknown"
    raw_sessions = read("installer_sessions", "shell", "dumpsys", "package", "installs")
    session_format_known = raw_sessions is not None and bool(re.search(r"(?:Active|Historical) install sessions:", raw_sessions))
    sessions = parse_sessions(raw_sessions)
    services = read("gms_services", "shell", "dumpsys", "activity", "services", PACKAGES[0])
    log = read("logcat", "logcat", "-d", "-b", "main", "-b", "system", "-t", "3000", "-v", "brief") if include_logcat else None
    return {
        "schema": 2, "user_id": user,
        "collected_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "device": properties, "packages": packages, "collection_status": status,
        "system_installer_components": {
            pkg: {"installed": pkg in installed_names if installed is not None else None,
                  "disabled": pkg in disabled_names if disabled is not None else None} for pkg in SYSTEM_PACKAGES},
        "huawei_signature_components_in_gms_apk": {
            name: name in gms_dump if gms_dump is not None else None for name in HUAWEI_COMPONENTS},
        "huawei_signature_service_running_hint": "com.huawei.signature.diff.SignatureService" in services if services is not None else None,
        "huawei_firmware_integration": "unknown",
        "caller_visible_gms_signature": "unknown",
        "gsf_device_registration": "unknown; package presence and log hints are not registration state",
        "vending_request_install_appop": appops_mode,
        "installer_session_visibility": "parsed" if session_format_known else "unknown",
        "vending_installer_sessions": sessions,
        "log_event_counts": summarize_logcat(log) if log is not None else None,
        "log_scope": "rolling last 3000 lines; overlapping snapshots; not a causal timeline",
        "cautions": ["Component presence or a running service does not prove firmware integration.",
                     "A permission denial alone does not prove why the download failed.",
                     "Sessions are system-wide; use user_id and installer_uid, including historical sessions.",
                     "Missing fields and unavailable reads are unknown, never negative evidence.",
                     "Signature-warning counts do not prove the Play Store install call path.",
                     "APK signature verification does not measure firmware spoofing or certification."],
    }


def _validated_sessions(report):
    sessions = []
    for raw in report.get("vending_installer_sessions", []):
        record = {}
        for key in ("id", "user_id", "installer_uid", "final_status"):
            value = raw.get(key)
            if value is not None and type(value) is not int:
                raise ValueError("Invalid numeric session field")
            record[key] = value
        if record["id"] is None:
            raise ValueError("Missing session ID")
        record["bucket"] = raw.get("bucket")
        if record["bucket"] not in ("active", "historical", "legacy", "unknown"):
            raise ValueError("Invalid session bucket")
        for key in ("committed", "sealed"):
            value = raw.get(key)
            if value is not None and type(value) is not bool:
                raise ValueError("Invalid session state")
            record[key] = value
        value = raw.get("progress")
        if value is not None and (type(value) not in (int, float) or not 0 <= value <= 1):
            raise ValueError("Invalid session progress")
        record["progress"] = value
        sessions.append(record)
    return sessions


def compare(before, after):
    if before.get("schema") != 2 or before.get("schema") != after.get("schema") or before.get("user_id") != after.get("user_id"):
        raise ValueError("Snapshots must use the same schema and Android user")
    if before.get("device") != after.get("device"):
        raise ValueError("Device metadata differs; capture on the same unchanged device")
    old = {s["id"]: s for s in _validated_sessions(before)}
    new = {s["id"]: s for s in _validated_sessions(after)}
    uid = after.get("packages", {}).get(PACKAGES[1], {}).get("uid")
    if uid is not None and type(uid) is not int:
        raise ValueError("Invalid package UID")
    visible = before.get("installer_session_visibility") == after.get("installer_session_visibility") == "parsed"
    # Schema 1 reports did not distinguish unavailable reads. Do not assert visibility.
    return {"session_comparison_available": visible,
            "new_session_ids": sorted(set(new) - set(old)) if visible else None,
            "removed_session_ids": sorted(set(old) - set(new)) if visible else None,
            "new_current_vending_session_ids": [sid for sid in sorted(set(new) - set(old))
                                                if uid is not None and new[sid]["installer_uid"] == uid
                                                and new[sid]["user_id"] == after.get("user_id")] if visible else None,
            "changed_sessions": [{"id": sid, "before": old[sid], "after": new[sid]}
                                 for sid in sorted(set(old) & set(new)) if old[sid] != new[sid]] if visible else None,
            "current_vending_uid": uid,
            "vending_uid_changed": before.get("packages", {}).get(PACKAGES[1], {}).get("uid") != uid,
            "old_uid_session_ids": [sid for sid, value in sorted(new.items())
                                    if uid is not None and value.get("installer_uid") is not None
                                    and value["installer_uid"] != uid
                                    and value.get("user_id") == after.get("user_id")],
            "other_user_session_ids": [sid for sid, value in sorted(new.items())
                                       if value.get("user_id") is not None and value["user_id"] != after.get("user_id")],
            "interpretation": "No new visible ID is not proof of no download or its cause. Active-to-history transitions are not new sessions. Verify same physical device manually; metadata equality is not device identity."}


def private_json(path, value):
    """Create exclusively: do not truncate files, follow symlinks or change hardlinks."""
    fd = os.open(Path(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False, allow_nan=False)
        stream.write("\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    take = sub.add_parser("snapshot", help="read-only; never saves raw logs")
    take.add_argument("--output", required=True)
    take.add_argument("--no-logcat", action="store_true")
    take.add_argument("--serial", help="select one already authorized ADB device")
    take.add_argument("--user", type=int, default=0, help="Android user (default: 0)")
    take.add_argument("--inspect-apks", action="store_true", help="read APKs into a temporary private host directory, then delete")
    take.add_argument("--apksigner", default=shutil.which("apksigner"), help="trusted local Android SDK apksigner executable")
    diff = sub.add_parser("compare", help="compare sanitized snapshots from the same device")
    diff.add_argument("--before", required=True)
    diff.add_argument("--after", required=True)
    diff.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "snapshot":
            if args.user < 0:
                raise ValueError("Android user must be non-negative")
            runner = Adb(args.serial)
            if runner("get-state").strip() != "device":
                raise RuntimeError("Select one connected, authorized Android device")
            value = snapshot(runner, not args.no_logcat, args.user, args.inspect_apks, args.apksigner)
        else:
            with open(args.before, encoding="utf-8") as stream:
                before = json.load(stream)
            with open(args.after, encoding="utf-8") as stream:
                after = json.load(stream)
            value = compare(before, after)
        private_json(args.output, value)
    except (RuntimeError, OSError, ValueError, KeyError, TypeError):
        # Exceptions can contain malformed input or sensitive remote details.
        print("Diagnostics failed: check device selection, compatible input and a new writable output path.", file=sys.stderr)
        return 1
    print("Saved sanitized report (private file; review before sharing).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
