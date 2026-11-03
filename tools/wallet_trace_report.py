#!/usr/bin/env python3
"""Offline, read-only Wallet/Play Store event summary.

No network, ADB, phone mutation, or raw-log output. Raw files may contain
OAuth tokens: keep them local and share only this tool's summary.
"""
import argparse
from collections import Counter
from pathlib import Path
import re
import sys

PATTERNS = (
    ("MICROG_WALLET_CLIENT_TOKEN_UNSUPPORTED",
     re.compile(r"GmsWalletOwSvc.*getClientToken requested; returning INTERNAL_ERROR", re.I)),
    ("PLAY_STORE_CLIENT_TOKEN_INTERNAL_ERROR",
     re.compile(r"Failed to load token:\s*8\s*:\s*Internal error", re.I)),
    ("PLAY_STORE_ACCOUNT_PRIVATE_DATA_DENIED",
     re.compile(r"cannot get user data for accounts of type:\s*(?:app\.revanced|com\.google)\b", re.I)),
    ("PLAY_STORE_ACCOUNT_METADATA_FETCH_FAILED",
     re.compile(r"Failed to fetch user data from account manager", re.I)),
    ("ANDROID_MANAGE_USERS_DENIED",
     re.compile(r"(?:Neither user [0-9]+ nor current process has android\.permission\.MANAGE_USERS|"
                r"MANAGE_USERS or CREATE_USERS permission)", re.I)),
)
STAMP = re.compile(r"(\d\d-\d\d \d\d:\d\d:\d\d)(?:\.\d+)?")

def summarize(text):
    """Return only fixed event IDs/counts/observed timestamps."""
    counts = Counter()
    last_seen = {}
    for line in text.splitlines():
        for label, pattern in PATTERNS:
            if pattern.search(line):
                counts[label] += 1
                stamp = STAMP.search(line)
                if stamp:
                    last_seen[label] = stamp.group(1)
    return counts, last_seen

def render(counts, last_seen):
    lines = [
        "microG Wallet / Play Store: offline event summary",
        "No raw logs, accounts, tokens, email addresses or device identifiers printed.",
        "Events may be OLD; correlation is not proof of the root cause.",
    ]
    for label, _ in PATTERNS:
        lines.append("{}: {} | last observed: {}".format(
            label, counts[label], last_seen.get(label, "unknown")))
    lines.append("Missing events do not establish successful installation or service support.")
    return "\n".join(lines) + "\n"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-file", required=True, metavar="LOCAL_LOG")
    args = parser.parse_args()
    try:
        data = Path(args.from_file).read_text(encoding="utf-8", errors="replace")
    except OSError:
        print("Cannot read the local log file.", file=sys.stderr)
        return 1
    print(render(*summarize(data)), end="")
    return 0

if __name__ == "__main__":
    sys.exit(main())
