#!/usr/bin/env python3
"""Safely summarize Huawei Play Store failures without disclosing logcat data.

Read-only: adb logcat -d. Never clears logcat, changes Android settings,
installs/removes packages, or prints raw log entries.
WARNING: --from-file reads the supplied raw log locally; use only trusted files.
"""
import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys

# Intentionally narrow, fixed output messages; never interpolate log contents.
KINDS = {
    "private_account": (
        "PLAY_STORE_ACCOUNT_PRIVATE_DATA_DENIED",
        "Android AccountManager rejected private account data access. "
        "This is not solved by changing account visibility alone.",
    ),
    "account_fetch": (
        "PLAY_STORE_ACCOUNT_METADATA_FETCH_FAILED",
        "Play Store reported failure to read data from AccountManager.",
    ),
    "user_privilege": (
        "ANDROID_PROTECTED_USER_PERMISSION_DENIED",
        "A process attempted a protected user-management operation.",
    ),
    "self_update": (
        "PLAY_STORE_SELF_UPDATE_FAILED",
        "Finsky reported a Play Store self-update failure, not necessarily "
        "the attempted install of another application.",
    ),
    "installer_session": (
        "PACKAGE_INSTALLER_SESSION_CALLBACK_FAILURE",
        "PackageInstaller reported session-completion callback failure.",
    ),
    "package_install": (
        "PACKAGE_INSTALL_FAILURE",
        "The package manager reported an explicit INSTALL_FAILED result.",
    ),
    "download": (
        "FINSKY_DOWNLOAD_FAILURE",
        "Finsky reported a download failure; see timing relative to attempt.",
    ),
}
ORDER = tuple(KINDS)
STAMP = re.compile(r"\b(\d\d-\d\d \d\d:\d\d:\d\d)(?:\.\d+)?\b")
ACCOUNT_DENIAL = re.compile(
    r"cannot get user data for accounts of type:\s*(?:app\.revanced|com\.google)\b",
    re.IGNORECASE,
)
USER_PRIVILEGE = re.compile(
    r"(?:MANAGE_USERS\s+or\s+CREATE_USERS\s+permission|"
    r"(?:MANAGE_USERS|CREATE_USERS)\s+permission\s+(?:required|denied))",
    re.IGNORECASE,
)
INSTALL_RESULT = re.compile(r"\bINSTALL_FAILED_[A-Z0-9_]+\b")
SELF_UPDATE = re.compile(
    r"Install failed for com\.android\.vending with an\s+\d+",
    re.IGNORECASE,
)


def classify(line):
    """Return known category IDs; raw line is discarded by caller."""
    matches = set()
    if ACCOUNT_DENIAL.search(line) and ("SecurityException" in line or "Finsky" in line):
        matches.add("private_account")
    if "Finsky" in line and "Failed to fetch user data from account manager" in line:
        matches.add("account_fetch")
    if USER_PRIVILEGE.search(line) and (
        "SecurityException" in line or "Finsky" in line or "Permission Denial" in line
    ):
        matches.add("user_privilege")
    if "Finsky" in line and SELF_UPDATE.search(line):
        matches.add("self_update")
    if "PackageInstaller" in line and "notifySessionFinished again failed" in line:
        matches.add("installer_session")
    if INSTALL_RESULT.search(line):
        matches.add("package_install")
    if "Finsky" in line and re.search(
        r"\b(?:download|downloading)\b.{0,70}\b(?:failed|failure)\b"
        r"|\b(?:failed|failure)\b.{0,70}\bdownload\b",
        line, re.IGNORECASE
    ):
        matches.add("download")
    return matches


def summarize(raw):
    """Only allowlisted category counts and timestamps leave this function."""
    counts = Counter()
    latest = {}
    for line in raw.splitlines():
        matched = classify(line)
        if not matched:
            continue
        timestamp = STAMP.search(line)
        for key in matched:
            counts[key] += 1
            if timestamp:
                # Last *observed* timestamp, not necessarily newest chronologically.
                latest[key] = timestamp.group(1)
    return counts, latest


def build_report(counts, latest):
    lines = [
        "GmsCore-hw | Play Store errors (read-only, sanitized)",
        "No email addresses, tokens, Android IDs, package-specific paths or raw logs.",
        "WARNING: Logcat may contain OLD events from earlier attempts.",
        "A detected error does not by itself prove the cause of app install failure.",
        "",
    ]
    for key in ORDER:
        label, explanation = KINDS[key]
        count = counts.get(key, 0)
        when = latest.get(key, "not available")
        lines.append(f"{label}: {count} event(s) | last seen: {when}")
        if count:
            lines.append(f"  Interpretation: {explanation}")
    lines.extend((
        "",
        "The Android getUserData error is different from a download/install failure.",
        "Even if the account is visible, Play Store may lack permission to read",
        "another account authenticator's private data.",
        "Changing microG code cannot grant Android system/signature permissions.",
        "Do not uninstall, clear data, sign in again, or replace APKs based on this report.",
    ))
    return "\n".join(lines) + "\n"


def read_adb_log():
    try:
        result = subprocess.run(
            ("adb", "logcat", "-d", "-b", "main", "-b", "system",
             "-t", "20000", "-v", "threadtime"),
            capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=40, check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("adb bulunamadı.") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("ADB logcat zaman aşımına uğradı.") from exc
    if result.returncode:
        raise RuntimeError("ADB logcat erişimi başarısız; USB bağlantısını kontrol edin.")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-file", metavar="PATH",
                        help="Read an existing local log file instead of querying ADB")
    args = parser.parse_args()
    try:
        raw = (
            Path(args.from_file).read_text(encoding="utf-8", errors="replace")
            if args.from_file else read_adb_log()
        )
    except (OSError, RuntimeError) as exc:
        # Never output raw log errors, device identifiers or file contents.
        print("Tanılama başarısız: günlük dosyasını veya ADB bağlantısını kontrol edin.",
              file=sys.stderr)
        return 1
    print(build_report(*summarize(raw)), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
