#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Run after reproducing a Play Store download failure.
# Pipes ADB logs through redaction; never saves a raw unfiltered log.
set -euo pipefail
OUT="${1:-$HOME/playstore-redacted-errors.txt}"
adb devices
printf '%s\n' 'Open Play Store on the phone; tap Install on a free, uninstalled app.'
read -r -p 'When the error appears, press ENTER to capture filtered diagnostics... '
adb logcat -d -b main -b system -v time | python3 -c '
import re,sys
keywords=re.compile(r"Finsky|Phonesky|vending|AccountManager|SecurityException|DownloadManager|PackageInstaller|INSTALL_FAILED|install.*fail|download.*fail",re.I)
for raw in sys.stdin:
    if not keywords.search(raw):
        continue
    line=re.sub(r"ya29\.[A-Za-z0-9._~-]+","[REDACTED_OAUTH_TOKEN]",raw)
    line=re.sub(r"(?i)Bearer\s+[A-Za-z0-9._~-]+","Bearer [REDACTED]",line)
    line=re.sub(r"(?i)(access_token|refresh_token)=([^\s,&}]+)",r"\1=[REDACTED]",line)
    line=re.sub(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b","[REDACTED_EMAIL]",line)
    sys.stdout.write(line)
' > "$OUT"
chmod 600 "$OUT"
printf 'Saved redacted diagnostic to %s\nReview it manually before sharing.\n' "$OUT"
