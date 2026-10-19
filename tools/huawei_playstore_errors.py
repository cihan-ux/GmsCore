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



CRASH_CLASSES = (
    "SecurityException", "NullPointerException", "IllegalStateException",
    "RemoteException", "RuntimeException", "DeadObjectException",
)


def summarize_playstore_crashes(raw):
    """Count AndroidRuntime crash events from the Play Store process only."""
    lines = raw.splitlines()
    found = []
    for idx, line in enumerate(lines):
        # Match exact process, never an arbitrary log line mentioning Play Store.
        if not re.search(r"\bProcess:\s*com\.android\.vending(?:\s|,|$)", line):
            continue
        previous = "\n".join(lines[max(0, idx - 5):idx + 1])
        if "FATAL EXCEPTION" not in previous:
            continue
        crash_time = "not available"
        for candidate in reversed(lines[max(0, idx - 5):idx + 1]):
            stamp = STAMP.search(candidate)
            if stamp:
                crash_time = stamp.group(1)
                break
        category = "OTHER_OR_UNKNOWN"
        for candidate in lines[idx + 1:min(idx + 10, len(lines))]:
            if "FATAL EXCEPTION" in candidate or "Process:" in candidate:
                break
            for label in CRASH_CLASSES:
                if re.search(r"\b(?:java\.lang\.|android\.os\.)?" + label + r"\b", candidate):
                    category = label
                    break
            if category != "OTHER_OR_UNKNOWN":
                break
        found.append((crash_time, category))
    return found



def summarize_playstore_crash_causes(raw):
    """Return high-level *indicators*, not unredacted crash stack traces.

    Parse each Play Store crash block separately, stopping before another
    FATAL EXCEPTION or a Process header, to avoid attributing other apps'
    errors to Play Store. Labels are fixed, never raw exception messages.
    """
    lines = raw.splitlines()
    results = []
    for idx, line in enumerate(lines):
        if not re.search(r"\bProcess:\s*com\.android\.vending(?:\s|,|$)", line):
            continue
        if "FATAL EXCEPTION" not in "\n".join(lines[max(0, idx - 5):idx]):
            continue
        end = min(idx + 90, len(lines))
        for j in range(idx + 1, end):
            if "FATAL EXCEPTION" in lines[j] or re.search(r"\bProcess:\s*\S+", lines[j]):
                end = j
                break
        block = "\n".join(lines[idx:end])
        if ACCOUNT_DENIAL.search(block):
            cause = "ACCOUNT_PRIVATE_USERDATA_DENIED"
        elif USER_PRIVILEGE.search(block):
            cause = "PROTECTED_USER_MANAGEMENT_PERMISSION"
        elif ("SecurityException" in block and
              "AccountManager.getUserData" in block):
            cause = "ACCOUNT_USERDATA_SECURITY_EXCEPTION"
        elif "SecurityException" in block:
            cause = "OTHER_SECURITY_EXCEPTION"
        elif "NoClassDefFoundError" in block or "ClassNotFoundException" in block:
            cause = "CLASS_LOADING_FAILURE"
        elif "NullPointerException" in block:
            cause = "NULL_POINTER_EXCEPTION"
        elif "RuntimeException" in block:
            cause = "OTHER_RUNTIME_EXCEPTION"
        else:
            cause = "UNKNOWN"
        stamp = STAMP.search(line)
        results.append((stamp.group(1) if stamp else "not available", cause))
    return results



# Fixed allowlists: do not echo arbitrary exception strings or sensitive data.
CRASH_PERMISSION_MARKERS = (
    "MANAGE_USERS", "CREATE_USERS", "INTERACT_ACROSS_USERS",
    "INTERACT_ACROSS_USERS_FULL", "GET_ACCOUNTS_PRIVILEGED",
    "GET_ACCOUNTS", "MANAGE_ACCOUNTS", "AUTHENTICATE_ACCOUNTS",
    "INSTALL_PACKAGES", "DELETE_PACKAGES", "REQUEST_INSTALL_PACKAGES",
    "REQUEST_DELETE_PACKAGES", "QUERY_ALL_PACKAGES",
    "READ_PRIVILEGED_PHONE_STATE", "WRITE_SECURE_SETTINGS",
    "READ_PHONE_STATE", "PACKAGE_USAGE_STATS", "BIND_JOB_SERVICE",
)
CRASH_OPERATION_MARKERS = (
    ("ACCOUNT_MANAGER", ("AccountManager", "IAccountManager", "accounts of type")),
    ("USER_MANAGER", ("UserManager", "IUserManager", "UserHandle")),
    ("PACKAGE_MANAGER", ("PackageManager", "IPackageManager", "PackageInstaller")),
    ("CONTENT_PROVIDER", ("ContentProvider", "IContentProvider", "ContentResolver")),
    ("ACTIVITY_OR_INTENT", ("ActivityTaskManager", "startActivity", "ActivityManager")),
)


def summarize_playstore_crash_security_context(raw):
    """Report only fixed Android permission and framework-component labels.

    Match one Play Store FATAL EXCEPTION block at a time. Never print a
    SecurityException message, full stack frame, email or token.
    A marker's presence is NOT proof that this permission caused the crash.
    """
    lines = raw.splitlines()
    results = []
    for idx, line in enumerate(lines):
        if not re.search(r"\bProcess:\s*com\.android\.vending(?:\s|,|$)", line):
            continue
        if "FATAL EXCEPTION" not in "\n".join(lines[max(0, idx - 5):idx]):
            continue
        end = min(idx + 90, len(lines))
        for j in range(idx + 1, end):
            if "FATAL EXCEPTION" in lines[j] or re.search(r"\bProcess:\s*\S+", lines[j]):
                end = j
                break
        block = "\n".join(lines[idx:end])
        if "SecurityException" not in block:
            continue
        names = [
            name for name in CRASH_PERMISSION_MARKERS
            if re.search(r"\b" + re.escape(name) + r"\b", block)
        ]
        ops = [
            category for category, tokens in CRASH_OPERATION_MARKERS
            if any(t in block for t in tokens)
        ]
        stamp = STAMP.search(line)
        results.append((
            stamp.group(1) if stamp else "not available",
            tuple(names), tuple(ops),
        ))
    return results


def build_crash_report(raw, mode):
    events = summarize_playstore_crashes(raw)
    lines = [
        "",
        "PLAY STORE CRASH BUFFER (read-only, sanitized)",
        f"Crash capture mode: {mode}",
        f"Crash buffer lines scanned: {len(raw.splitlines())}",
        f"FATAL EXCEPTION events belonging to com.android.vending: {len(events)}",
    ]
    for timestamp, kind in events[-5:]:
        lines.append(f"  Play Store crash: {timestamp} | exception category: {kind}")
    causes = summarize_playstore_crash_causes(raw)
    lines.append("PLAY STORE CRASH CAUSE INDICATORS (heuristic; no root cause proven)")
    for timestamp, cause in causes[-5:]:
        lines.append(f"  {timestamp} | indicator: {cause}")
    security = summarize_playstore_crash_security_context(raw)
    lines.append("CRASH SECURITY CONTEXT (fixed known markers; not proof of cause)")
    for timestamp, perms, ops in security[-5:]:
        lines.append(
            f"  {timestamp} | permissions: {','.join(perms) if perms else 'NO_KNOWN_MARKER'}"
            f" | components: {','.join(ops) if ops else 'UNKNOWN'}"
        )
    if causes:
        lines.append(
            "These indicators are derived from fixed markers in a crash block;"
            " they may be incomplete, especially when logcat is truncated."
        )
    if not events:
        lines.append(
            "No matching crash seen in available buffer."
            " This does not rule out a UI error, ANR or an older crash."
        )
    lines.append(
        "No raw stack traces, email addresses, account names or tokens are printed."
    )
    return "\n".join(lines) + "\n"


def read_crash_buffer():
    """Read crash log buffer; never clear it or print raw entries."""
    cmd = ("adb", "logcat", "-d", "-b", "crash", "-t", "1000", "-v", "threadtime")
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=35, check=False,
        )
    except FileNotFoundError:
        return "", "ADB_NOT_FOUND"
    except subprocess.TimeoutExpired:
        return "", "ADB_TIMEOUT"
    except OSError:
        return "", "ADB_EXECUTION_FAILED"
    if result.returncode:
        return "", "ADB_CRASH_BUFFER_UNAVAILABLE"
    return result.stdout, "STANDARD (1000 lines requested)"



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-file", metavar="PATH",
                        help="Read an existing local log file instead of querying ADB")
    parser.add_argument("--max-lines", type=int, default=3000,
                        help="ADB logcat line limit (300..10000; default: 3000)")
    parser.add_argument("--focused", action="store_true",
                        help="Use a read-only tag filter for Finsky and Android package installer logs")
    parser.add_argument("--crash-check", action="store_true",
                        help="Also inspect crash buffer for Play Store FATAL EXCEPTION events")
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
    if args.crash_check:
        crash_raw, crash_mode = read_crash_buffer()
        print(build_crash_report(crash_raw, crash_mode), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
