#!/usr/bin/env python3
"""One-command, read-only P Smart S Play Store trial.

Creates a private run directory, collects two safe package/session snapshots,
a live event-only log summary and a conservative evidence assessment. Neither
raw Android logs nor auth/account credentials are saved.
"""
# SPDX-License-Identifier: Apache-2.0
import argparse
import json
from pathlib import Path
import sys
import tempfile

import diagnose
import live_trace

TRIAL_SCHEMA = 1
KNOWN_EVENTS = (
    "gms_signature_rejected", "huawei_service_start", "huawei_binder_denied",
    "gms_auth_request", "gms_checkin_hint", "finsky_catalog_hint",
    "finsky_install_hint", "finsky_network_error_hint",
    "download_component_hint", "installer_component_hint",
    "install_failed_hint", "vending_permission_denial",
)


def _count(events, name):
    """None means no trustworthy count (different from an observed zero)."""
    if not isinstance(events, dict):
        return None
    value = events.get("counts", {}).get(name)
    return value if type(value) is int and value >= 0 else None


def assessment(before, after, comparison, events):
    """Only known output fields; never echo arbitrary device-derived strings."""
    if before.get("schema") != 2 or after.get("schema") != 2:
        raise ValueError("Expected schema-2 snapshots")
    if before.get("user_id") != after.get("user_id"):
        raise ValueError("Snapshots target different Android users")
    if before.get("device") != after.get("device"):
        raise ValueError("Device metadata changed")
    if comparison.get("session_comparison_available") is not True:
        visible = None
    else:
        visible = comparison.get("new_current_vending_session_ids")
        if not isinstance(visible, list) or any(type(s) is not int for s in visible):
            visible = None

    trusted_events = (events if isinstance(events, dict)
                      and events.get("schema") == 1
                      and events.get("collector_status") == "capture_window_completed"
                      else None)
    counts = {name: _count(trusted_events, name) for name in KNOWN_EVENTS}
    any_download = any((counts[name] or 0) > 0 for name in
                       ("finsky_install_hint", "download_component_hint"))
    any_installer = any((counts[name] or 0) > 0 for name in
                        ("installer_component_hint", "install_failed_hint"))
    if visible is not None and visible:
        stage = "current_vending_installer_session_observed"
    elif any_installer:
        stage = "installer_component_log_hint_only"
    elif any_download:
        stage = "delivery_or_download_log_hint_only"
    elif any((counts[name] or 0) > 0 for name in
             ("gms_auth_request", "finsky_catalog_hint", "gms_signature_rejected")):
        stage = "catalog_auth_or_signature_events_only"
    else:
        stage = "no_discriminating_events_observed"

    gms = after.get("packages", {}).get("com.google.android.gms", {})
    vending = after.get("packages", {}).get("com.android.vending", {})
    return {
        "schema": TRIAL_SCHEMA,
        "observation_stage": stage,
        "session_comparison_available": visible is not None,
        "new_current_vending_session_count": len(visible) if visible is not None else None,
        "current_vending_uid_changed": comparison.get("vending_uid_changed")
                                      if type(comparison.get("vending_uid_changed")) is bool else None,
        "event_counts": counts,
        "huawei_service_component_declared": (
            after.get("huawei_signature_components_in_gms_apk", {})
            .get("com.huawei.signature.diff.SignatureService") is True
        ),
        "huawei_service_running_hint": (
            after.get("huawei_signature_service_running_hint")
            if type(after.get("huawei_signature_service_running_hint")) is bool else None
        ),
        "gms_installed": gms.get("installed") if type(gms.get("installed")) is bool else None,
        "vending_installed": vending.get("installed") if type(vending.get("installed")) is bool else None,
        "huawei_firmware_integration": "unknown",
        "signature_check_caused_install_failure": "unproven",
        "playstore_delivery_response": "unknown",
        "interpretation": [
            "The strongest observed stage is NOT the first proven failure point.",
            "A log signature rejection may be unrelated to the install path.",
            "No installer session/log event does not prove no download or network request.",
            "Service presence or a running hint cannot establish Huawei firmware support.",
        ],
    }


def _new_directory(workspace):
    workspace.mkdir(mode=0o700, parents=True, exist_ok=True)
    # mkdir uses mode 0700; never overwrite older trial files.
    return Path(tempfile.mkdtemp(prefix="trial-", dir=workspace))


def run(workspace, serial=None, user=0, seconds=45, no_live=False, prompt=input):
    if user < 0 or not 10 <= seconds <= 180:
        raise ValueError("Invalid Android user or recording duration")
    runner = diagnose.Adb(serial)
    if runner("get-state").strip() != "device":
        raise RuntimeError("An authorized Android device is required")
    run_dir = _new_directory(workspace)

    print("Run directory:", run_dir)
    print("Open a free application's Play Store details; DO NOT tap Install.")
    prompt("Press ENTER when that page is ready: ")

    before = diagnose.snapshot(runner, include_logcat=False, user=user)
    diagnose.private_json(run_dir / "before.json", before)

    events = None
    if no_live:
        print("Live logcat skipped; log event counts will be unknown.")
    else:
        print("Live recording started. Tap Install ONCE now; leave the phone connected.")
        try:
            events = live_trace.capture(serial=serial, seconds=seconds)
        except (OSError, RuntimeError, ValueError):
            # A logcat failure must not erase useful before/after session evidence.
            events = {"schema": 1, "collector_status": "unavailable",
                      "raw_logs_written": False}
        live_trace.private_output(run_dir / "events.json", events)

    after = diagnose.snapshot(runner, include_logcat=False, user=user)
    diagnose.private_json(run_dir / "after.json", after)

    comparison = diagnose.compare(before, after)
    diagnose.private_json(run_dir / "comparison.json", comparison)

    summary = assessment(before, after, comparison, events)
    diagnose.private_json(run_dir / "summary.json", summary)
    print("Saved private reports under:", run_dir)
    print("Observed stage:", summary["observation_stage"])
    print("New current-UID installer sessions:",
          summary["new_current_vending_session_count"])
    print("Share only summary.json and comparison.json after reviewing them.")
    return run_dir


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=Path.home() / "psmarts-test")
    parser.add_argument("--serial", help="ADB device selector, not written to reports")
    parser.add_argument("--user", type=int, default=0)
    parser.add_argument("--seconds", type=int, default=45)
    parser.add_argument("--no-live", action="store_true",
                        help="skip logcat and compare read-only session snapshots")
    args = parser.parse_args(argv)
    try:
        run(args.workspace.expanduser(), args.serial, args.user,
            args.seconds, args.no_live)
    except (OSError, RuntimeError, ValueError, KeyboardInterrupt, EOFError):
        # Never print exceptions that may include sensitive device data.
        print("Trial interrupted or failed; inspect the private run directory.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
