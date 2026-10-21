# Live event trace: Play Store and Huawei signature hints

This is a **supplement** to the existing `diagnose.py` snapshot collector in the Codex branch.
It does not modify microG, Play Store, Google accounts, system properties,
permissions, installer sessions or logcat buffers. Linux Mint, Python 3.10+,
and trusted Android SDK ADB are required.

## One controlled trial

1. Open a free application's detail page in Play Store; do **not** tap Install.
2. In the GmsCore checkout, on branch `experiment/psmarts-live-events`, run:

```bash
mkdir -m 700 "$HOME/psmarts-trial-1"
python3 tools/psmarts-diagnostics/diagnose.py snapshot --user 0 \
  --no-logcat --output "$HOME/psmarts-trial-1/before.json"
python3 tools/psmarts-diagnostics/live_trace.py \
  --seconds 45 --output "$HOME/psmarts-trial-1/events.json"
# Tap Install ONCE while live_trace.py is running; do not repeat or reboot.
python3 tools/psmarts-diagnostics/diagnose.py snapshot --user 0 \
  --no-logcat --output "$HOME/psmarts-trial-1/after.json"
python3 tools/psmarts-diagnostics/diagnose.py compare \
  --before "$HOME/psmarts-trial-1/before.json" \
  --after "$HOME/psmarts-trial-1/after.json" \
  --output "$HOME/psmarts-trial-1/comparison.json"
```

Use `--serial YOUR_SERIAL` for both snapshot and live capture when multiple
ADB devices are attached. The serial is never saved to the report.
`--seconds` accepts 10 to 180 seconds; new runs require new output file paths.

## Privacy and interpretation

`live_trace.py` uses `adb logcat` without `-c`, reads the stream into volatile
memory and classifies **allowlisted** event kinds. Output contains only event
kind, approximate elapsed time, five-second buckets and aggregate counts.
Raw log messages, OAuth tokens, account names, app names, network URLs and
Binder payloads are never written to the report. Inspect all reports before
sharing; the separate snapshots include UIDs, device build and session IDs.

`huawei_service_start` and `huawei_binder_denied` are hints from the APK's
SignatureService, **not evidence** that Huawei firmware called querySignature.
Successful querySignature calls have no diagnostic log in the reviewed service
implementation. Absence of log hints does not prove missing firmware support.

`finsky_install_hint` only reflects a keyword in Play Store logs, and is not
evidence that APK bytes transferred. A download/installer-component message
cannot be attributed to the Play Store attempt without more data. Likewise,
event timestamps alone cannot prove what caused a failed installation.
Compare the live event report with the before/after session comparison and
record the screen error separately.

OEM log filtering or buffering may hide events. Check `collector_status`:
`logcat_stopped_early` is inconclusive. Android's `-T 1` is a best-effort tail;
one earlier line may still appear in the initial sample.

Known constraints: Play Store has an observed GMS signature-invalid warning;
its observed `INSTALL_PACKAGES` permission is denied; Huawei firmware support
for differentiated signatures remains unverified. The first failed delivery
call is still not known.
