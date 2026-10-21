#!/usr/bin/env python3
"""Read-only, privacy-minimizing Android 10 Play Store diagnostics.

This tool never grants permissions, clears logs, changes apps, or stores raw
logcat output. It reports selected booleans, version metadata and session IDs.
"""
# SPDX-License-Identifier: Apache-2.0
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys

PACKAGES = ("com.google.android.gms", "com.android.vending", "com.google.android.gsf")
INSTALL_PERMS = ("android.permission.INSTALL_PACKAGES",
                 "android.permission.REQUEST_INSTALL_PACKAGES")
SYSTEM_PACKAGES = ("com.android.providers.downloads",
                   "com.android.providers.downloads.ui",
                   "com.android.packageinstaller")
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
}

def adb(*parts):
    """Return only output for an explicitly listed read-only ADB command."""
    try:
        result = subprocess.run(["adb", *parts], text=True, errors="replace",
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=25, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError("ADB unavailable or timed out") from exc
    if result.returncode:
        # stderr may include identifying device data; never print or save it.
        raise RuntimeError("ADB command failed: " + " ".join(parts[:2]))
    return result.stdout

def _match(pattern, body):
    m = re.search(pattern, body)
    return m.group(1) if m else None

def parse_package(body):
    """Discard full dumpsys text; return only allowlisted package metadata."""
    requested = body.split("requested permissions:", 1)
    requested_text = (requested[1].split("install permissions:", 1)[0]
                      if len(requested) > 1 else "")
    location = _match(r"(?m)^\s*codePath=(\S+)", body) or ""
    if location.startswith("/data/app"):
        location_class = "user_app"
    elif location.startswith(("/system/priv-app", "/product/priv-app",
                              "/system_ext/priv-app")):
        location_class = "priv_app"
    elif location.startswith(("/system/", "/product/", "/vendor/", "/system_ext/")):
        location_class = "system_partition"
    else:
        location_class = "unknown"
    flags = _match(r"(?m)^\s*privateFlags=\[([^\]]*)\]", body) or ""
    return {
        "version": _match(r"(?m)^\s*versionName=(.+)$", body),
        "version_code": _match(r"\bversionCode=(\d+)", body),
        "location_class": location_class,
        "privileged_flag": "PRIVILEGED" in flags.split(),
        "requested_install_permissions": {
            perm: bool(re.search(r"(?m)^\s*" + re.escape(perm) + r"\s*$",
                                 requested_text)) for perm in INSTALL_PERMS
        },
    }

def parse_sessions(body):
    """Keep only session IDs, installer UID and non-identifying state."""
    chunks = re.split(r"(?m)^\s*Session (\d+):\s*$", body)
    sessions = []
    for idx in range(1, len(chunks), 2):
        detail = chunks[idx + 1]
        if "mInstallerPackageName=com.android.vending" not in detail:
            continue
        sessions.append({
            "id": int(chunks[idx]),
            "installer_uid": int(_match(r"\bmInstallerUid=(\d+)", detail) or -1),
            "progress": float(_match(r"\bmProgress=([0-9.]+)", detail) or 0),
            "committed": _match(r"\bmCommitted=(true|false)", detail) == "true",
            "sealed": _match(r"\bmSealed=(true|false)", detail) == "true",
        })
    return sorted(sessions, key=lambda session: session["id"])

def summarize_logcat(body):
    """Never return lines, tokens, usernames, URLs or any raw log text."""
    return {
        key: len(re.findall(pattern, body, re.IGNORECASE))
        for key, pattern in LOG_PATTERNS.items()
    }

def parse_uid(body, package_name):
    m = re.search(r"(?m)^package:" + re.escape(package_name) +
                  r"\s+uid:(\d+)\s*$", body)
    return int(m.group(1)) if m else None

def _get_permission(runner, package, permission):
    try:
        response = runner("shell", "dumpsys", "package", "check-permission",
                          permission, package, "0").strip()
        return {"0": "granted", "-1": "denied"}.get(response, "unknown")
    except RuntimeError:
        return "unknown"

def snapshot(runner=adb, include_logcat=True):
    """Collect current state without changing the phone."""
    properties = {}
    for label, prop in (("model", "ro.product.model"),
                        ("android", "ro.build.version.release"),
                        ("emui", "ro.build.version.emui"),
                        ("build", "ro.build.display.id")):
        properties[label] = runner("shell", "getprop", prop).strip()[:100]
    uids = runner("shell", "pm", "list", "packages", "-U")
    installed = runner("shell", "pm", "list", "packages")
    disabled = runner("shell", "pm", "list", "packages", "-d")
    installed_names = set(re.findall(r"(?m)^package:([A-Za-z0-9._]+)", installed))
    disabled_names = set(re.findall(r"(?m)^package:([A-Za-z0-9._]+)", disabled))
    packages = {}
    gms_dump = ""
    for package in PACKAGES:
        info = {"installed": package in installed_names,
                "uid": parse_uid(uids, package)}
        if info["installed"]:
            raw = runner("shell", "dumpsys", "package", package)
            info.update(parse_package(raw))
            if package == "com.google.android.gms":
                gms_dump = raw
        packages[package] = info
    play = PACKAGES[1]
    packages[play]["install_permissions_effective"] = {
        perm: _get_permission(runner, play, perm) for perm in INSTALL_PERMS
    } if packages[play]["installed"] else {}
    appops = runner("shell", "cmd", "appops", "get",
                    play, "REQUEST_INSTALL_PACKAGES")
    appops_match = re.search(r"REQUEST_INSTALL_PACKAGES:\s*(allow|deny|ignore|default)", appops)
    appops_mode = appops_match.group(1) if appops_match else "default_or_unavailable"
    sessions = parse_sessions(runner("shell", "dumpsys", "package", "installs"))
    logcounts = summarize_logcat(
        runner("logcat", "-d", "-b", "main", "-b", "system", "-t", "3000", "-v", "brief")
    ) if include_logcat else None
    return {
        "schema": 1,
        "collected_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "device": properties,
        "packages": packages,
        "system_installer_components": {
            pkg: {"installed": pkg in installed_names, "disabled": pkg in disabled_names}
            for pkg in SYSTEM_PACKAGES
        },
        "huawei_signature_components_in_gms_apk": {
            name: name in gms_dump for name in HUAWEI_COMPONENTS
        },
        "vending_request_install_appop": appops_mode,
        "vending_installer_sessions": sessions,
        "log_event_counts": logcounts,
        "cautions": [
            "Huawei service entries inside microG do not prove firmware integration.",
            "A rejected INSTALL_PACKAGES permission does not by itself prove the download failure.",
            "Session creation or commit does not establish a successful APK install.",
            "Log counts are not causal proof; only Android device testing can confirm behavior.",
        ],
    }

def compare(before, after):
    old = {s["id"]: s for s in before.get("vending_installer_sessions", [])}
    new = {s["id"]: s for s in after.get("vending_installer_sessions", [])}
    uid = after.get("packages", {}).get("com.android.vending", {}).get("uid")
    return {
        "new_session_ids": sorted(set(new) - set(old)),
        "removed_session_ids": sorted(set(old) - set(new)),
        "changed_sessions": [
            {"id": sid, "before": old[sid], "after": new[sid]}
            for sid in sorted(set(old) & set(new)) if old[sid] != new[sid]
        ],
        "current_vending_uid": uid,
        "old_uid_session_ids": [
            sid for sid, value in sorted(new.items())
            if uid is not None and value["installer_uid"] != uid
        ],
        "interpretation": "No new session means none was observed, not proof of why it failed.",
    }

def private_json(path, value):
    """Restrict owner access even if the user's umask is permissive."""
    target = Path(path)
    if target.is_symlink():
        raise ValueError("Refusing to overwrite a symlink")
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, ensure_ascii=False)
            stream.write("\n")
    except BaseException:
        try:
            os.close(fd)
        except OSError:
            pass
        raise

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    take = sub.add_parser("snapshot", help="read-only snapshot; no raw logs stored")
    take.add_argument("--output", required=True)
    take.add_argument("--no-logcat", action="store_true")
    diff = sub.add_parser("compare", help="compare two sanitized snapshots")
    diff.add_argument("--before", required=True)
    diff.add_argument("--after", required=True)
    diff.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "snapshot":
            value = snapshot(include_logcat=not args.no_logcat)
            private_json(args.output, value)
        else:
            with open(args.before, encoding="utf-8") as f:
                before = json.load(f)
            with open(args.after, encoding="utf-8") as f:
                after = json.load(f)
            private_json(args.output, compare(before, after))
    except (RuntimeError, OSError, ValueError) as exc:
        print("Error: " + str(exc), file=sys.stderr)
        return 1
    print("Saved sanitized report:", args.output)
    return 0

if __name__ == "__main__":
    sys.exit(main())
