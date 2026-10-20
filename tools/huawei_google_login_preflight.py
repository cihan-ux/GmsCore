#!/usr/bin/env python3
"""Read-only, privacy-limited Android 10 GSF / Google account login preflight.

Never prints account names, tokens, raw logs, serial numbers, or Android IDs.
Never changes device settings, permissions, packages or accounts.
"""
import re
import subprocess
import sys

PACKAGES = (
    "com.google.android.gsf",
    "com.google.android.gms",
    "com.google.android.gsf.login",
    "com.android.vending",
    "app.revanced.android.gms",
)
PERMISSIONS = (
    "android.permission.MANAGE_USERS",
    "android.permission.CREATE_USERS",
    "android.permission.INTERACT_ACROSS_USERS",
    "com.google.android.providers.gsf.permission.READ_GSERVICES",
)


def run_adb(*args):
    try:
        result = subprocess.run(
            ["adb", *args], capture_output=True, text=True, timeout=35,
            encoding="utf-8", errors="replace", check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    return result.stdout if result.returncode == 0 else None


def parse_package(dump, path_output):
    if dump is None or path_output is None or not path_output.startswith("package:"):
        return {"installed": False}
    match = re.search(r"^\s*versionCode=(\d+)", dump, re.M)
    uid = re.search(r"^\s*userId=(\d+)", dump, re.M)
    shared = re.search(r"^\s*sharedUser=SharedUserSetting\{[^\s]+\s+([^/]+)/", dump, re.M)
    private = re.search(r"^\s*privateFlags=\[([^\]]*)\]", dump, re.M)
    return {
        "installed": True,
        "version": match.group(1) if match else "UNKNOWN",
        "uid": uid.group(1) if uid else "UNKNOWN",
        "shared_user": shared.group(1) if shared else "NONE",
        "privileged": bool(private and re.search(r"\bPRIVILEGED\b", private.group(1))),
    }


def classify_gsf_crashes(raw):
    """Return fixed labels and times, never raw exception messages."""
    events = []
    for chunk in re.split(r"(?=\bFATAL EXCEPTION:)", raw or ""):
        if not re.search(r"Process:\s*com\.google\.process\.gapps(?:[,\s]|$)", chunk):
            continue
        match = re.search(r"\b\d\d-\d\d \d\d:\d\d:\d\d", chunk)
        if "SecurityException" not in chunk:
            code = "NON_SECURITY_CRASH"
        elif ("GoogleSettingsProvider.onCreate" in chunk and
              "UserManager.getProfileParent" in chunk):
            code = "GSF_PROFILE_PARENT_PERMISSION_FAILURE"
        else:
            code = "GSF_OTHER_SECURITY_FAILURE"
        events.append((match.group(0) if match else "UNKNOWN_TIME", code))
    return events[-3:]


def main():
    print("Huawei P Smart S - Google Account / GSF preflight (READ ONLY)")
    print("No account data, OAuth tokens, raw logs or device identifiers printed.")
    installed = {}
    for package in PACKAGES:
        path = run_adb("shell", "pm", "path", package)
        dump = run_adb("shell", "dumpsys", "package", package) if path else None
        info = parse_package(dump, path)
        installed[package] = info
        if info["installed"]:
            print("{}: INSTALLED version={} uid={} shared={} privileged={}".format(
                package, info["version"], info["uid"], info["shared_user"],
                "YES" if info["privileged"] else "NO",
            ))
        else:
            print("{}: NOT_SEEN".format(package))
    print("PERMISSIONS (0=GRANTED, -1=DENIED, ?=UNKNOWN)")
    for package in ("com.google.android.gsf", "com.google.android.gms", "com.android.vending"):
        if not installed[package]["installed"]:
            continue
        values = []
        for permission in PERMISSIONS:
            result = run_adb("shell", "dumpsys", "package", "check-permission",
                             permission, package, "0")
            value = result.strip() if result and result.strip() in ("0", "-1") else "?"
            values.append(permission.rsplit(".", 1)[-1] + "=" + value)
        print("{}: {}".format(package, " ".join(values)))
    crash = run_adb("logcat", "-d", "-b", "crash", "-t", "2000", "-v", "threadtime")
    print("RECENT_GSF_CRASHES")
    for time, code in classify_gsf_crashes(crash or ""):
        print("{}: {}".format(time, code))
    if not classify_gsf_crashes(crash or ""):
        print("NO_MATCH_IN_BUFFER (does not imply success)")
    gsf = installed["com.google.android.gsf"]
    if gsf["installed"] and not gsf["privileged"]:
        print("LIMIT: Installed GSF is not privileged; MANAGE_USERS cannot be added by ordinary adb.")
    print("NO_CHANGES_MADE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
