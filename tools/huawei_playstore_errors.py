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



def scan_coverage(raw):
    """Count captured log lines and known tags; never return raw log text."""
    coverage = Counter()
    seen_times = []
    for line in raw.splitlines():
        coverage["all_lines"] += 1
        if re.search(r"\bFinsky(?::background)?\b", line):
            coverage["finsky_lines"] += 1
        if "PackageInstaller" in line:
            coverage["installer_lines"] += 1
        if "com.android.vending" in line:
            coverage["vending_mentions"] += 1
        timestamp = STAMP.search(line)
        if timestamp:
            if not seen_times:
                seen_times.append(timestamp.group(1))
            coverage["timestamped_lines"] += 1
            # Keep only the last observed timestamp, not complete log lines.
            last_seen = timestamp.group(1)
    return coverage, (seen_times[0] if seen_times else "not available"), (
        last_seen if seen_times else "not available"
    )


def build_coverage_report(coverage, first_time, last_time):
    """Summarize what was actually captured without claiming log completeness."""
    lines = [
        "",
        "LOG COVERAGE (independent of matching errors)",
        f"Captured lines: {coverage['all_lines']}",
        f"Timestamped lines: {coverage['timestamped_lines']}",
        f"First observed log timestamp: {first_time}",
        f"Last observed log timestamp: {last_time}",
        f"Finsky-tagged lines: {coverage['finsky_lines']}",
        f"PackageInstaller-tagged lines: {coverage['installer_lines']}",
        f"com.android.vending mentions: {coverage['vending_mentions']}",
    ]
    if not coverage["all_lines"]:
        lines.append(
            "Coverage: EMPTY. No logs captured; zero matching errors is inconclusive."
        )
    elif not coverage["finsky_lines"]:
        lines.append(
            "Coverage: NO FINSKY LOGS. Zero Finsky errors cannot exclude"
            " a Play Store problem."
        )
    else:
        lines.append(
            "Coverage: Finsky logs are present, but this scan recognizes only"
            " selected error patterns."
        )
    lines.append(
        "Compare observed timestamps with the time of the latest Play Store test."
    )
    return "\n".join(lines) + "\n"


def build_logcat_command(max_lines, focused=False):
    """Read only, bounded logcat. Focused mode filters Android log TAGS."""
    command = (
        "adb", "logcat", "-d", "-b", "main", "-b", "system",
        "-t", str(max_lines), "-v", "threadtime",
    )
    if focused:
        # Tag-level filtering happens before the -t tail count.
        # Finsky:background uses a separate tag and may be absent here.
        command += ("-s", "Finsky:V", "PackageInstaller:V", "PackageManager:W")
    return command


def query_adb_log(max_lines, timeout=55, focused=False):
    """Return raw log text to caller memory only; errors are fixed enums."""
    try:
        result = subprocess.run(
            build_logcat_command(max_lines, focused=focused),
            capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=timeout, check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("ADB_NOT_FOUND") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("ADB_TIMEOUT") from exc
    except OSError as exc:
        raise RuntimeError("ADB_EXECUTION_FAILED") from exc
    if result.returncode:
        # stderr can contain device IDs or other private details.
        raise RuntimeError("ADB_COMMAND_FAILED")
    return result.stdout


def read_adb_log(max_lines=3000, focused=False):
    """Use a bounded capture and smaller fallback on transient ADB failures.

    Never writes, displays, uploads or clears raw logcat data.
    """
    mode = "FOCUSED" if focused else "STANDARD"
    try:
        return (
            query_adb_log(max_lines, focused=focused),
            f"{mode} ({max_lines} lines requested)",
        )
    except RuntimeError as first:
        if str(first) in ("ADB_NOT_FOUND", "ADB_EXECUTION_FAILED"):
            raise
        fallback_lines = min(300, max_lines)
        if fallback_lines == max_lines:
            raise
        try:
            return (
                query_adb_log(fallback_lines, timeout=25, focused=focused),
                f"{mode} FALLBACK ({fallback_lines} lines requested after {first})",
            )
        except RuntimeError as second:
            raise RuntimeError(f"ADB_CAPTURE_FAILED ({first}; {second})") from second


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-file", metavar="PATH",
                        help="Read an existing local log file instead of querying ADB")
    parser.add_argument("--max-lines", type=int, default=3000,
                        help="ADB logcat line limit (300..10000; default: 3000)")
    parser.add_argument("--focused", action="store_true",
                        help="Use a read-only tag filter for Finsky and Android package installer logs")
    args = parser.parse_args()
    if not 300 <= args.max_lines <= 10000:
        parser.error("--max-lines must be between 300 and 10000")
    try:
        if args.from_file:
            raw = Path(args.from_file).read_text(encoding="utf-8", errors="replace")
            capture_mode = "EXISTING_LOCAL_FILE"
        else:
            raw, capture_mode = read_adb_log(args.max_lines, focused=args.focused)
    except OSError:
        print("Tanılama başarısız: LOCAL_FILE_UNREADABLE", file=sys.stderr)
        return 1
    except RuntimeError as exc:
        # Only our own fixed messages appear; never print raw stderr/logcat.
        print(f"Tanılama başarısız: {exc}", file=sys.stderr)
        return 1
    # Never print raw log lines, even when no patterns match.
    print(build_report(*summarize(raw)), end="")
    print(f"Log capture mode: {capture_mode}")
    print(build_coverage_report(*scan_coverage(raw)), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
