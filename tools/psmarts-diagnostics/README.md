# P Smart S rootless diagnostics

Read-only device collection for Huawei P Smart S AQM-L21A/AQM-LX1, Android 10
(API 29), EMUI 12.0.0.226(C432E5R5P3). This extends the tools merged in PR #4;
it does not install, uninstall, start/stop apps, clear logcat, grant permissions,
change AppOps, query account/token databases, or create installer sessions.
ADB can start its local host daemon as usual. Authorize USB debugging yourself.

## Capture one manual attempt

Requires Python 3.10+ and a trusted Android SDK `adb` on PATH. Select the same
physical phone and Android user for both captures. No report file may already
exist: exclusive creation prevents accidental overwrite, symlink or hardlink
truncation. Use a private directory; files are created with mode `0600` on POSIX.

```bash
mkdir -m 700 /tmp/psmarts-run-01
python3 tools/psmarts-diagnostics/diagnose.py snapshot --user 0 \
  --output /tmp/psmarts-run-01/before.json
# Manually open a free app detail page on the phone and tap Install once.
# Wait 30–60 seconds without reinstalling/changing packages or accounts.
python3 tools/psmarts-diagnostics/diagnose.py snapshot --user 0 \
  --output /tmp/psmarts-run-01/after.json
python3 tools/psmarts-diagnostics/diagnose.py compare \
  --before /tmp/psmarts-run-01/before.json \
  --after /tmp/psmarts-run-01/after.json \
  --output /tmp/psmarts-run-01/comparison.json
```

Use `snapshot --serial YOUR_DEVICE_SERIAL` when multiple devices are connected.
The serial is passed to ADB, never included in the report. If logcat is denied
or unwanted, add `--no-logcat`. Reports state unavailable/unsupported reads as
**unknown**, not absence. An unauthorized/offline/ambiguous device fails preflight.
The collector continues on individual OEM-command failures, which appear in
`collection_status`; inspect these before interpreting the report.

## Optional verification of installed APK files

Provide a trusted local SDK `apksigner`. `--inspect-apks` reads the three selected
packages' base/split APKs with `pm path` and `adb pull`, inside a private temporary
host directory, then deletes them on normal completion or handled errors. An
abrupt OS/process termination may leave temporary files; use a trusted host.
No APK, certificate subject, device path or tool stderr is included in reports.
Only file SHA-256, signer certificate SHA-256 and verification status are kept.

```bash
python3 tools/psmarts-diagnostics/diagnose.py snapshot --inspect-apks \
  --apksigner /path/to/android-sdk/build-tools/35.0.0/apksigner \
  --output /tmp/psmarts-run-01/apk-check.json
```

Missing `apksigner`, inaccessible files, unsupported paths and failed signatures
are explicit outcomes. `signature_verified=true` verifies the **APK bytes**; it
never proves that Huawei PackageManager returns Google's certificate to Play
Store. No attempt is made to bind/call Huawei's protected signature Binder.
This shell collector cannot emulate a `com.android.vending` caller or inspect
another app's private Check-in state without violating the read-only scope.

## What the report establishes

| Field | Evidence and limit |
| --- | --- |
| `packages` | GMS/GSF/Vending presence, current UID, disabled state, installed version, target SDK, flags and install permission declaration/status |
| `location_class=data_app` | APK is under `/data/app`; inspect `system_flag`/`privileged_flag` too, since updated system apps can live there |
| `vending_request_install_appop` | Recorded mode, `not_recorded`, or `unknown`; no record is not proof of permission or denial |
| `vending_installer_sessions` | Active/history bucket, installer UID, Android user, progress and states, with missing fields kept null |
| `new_current_vending_session_ids` | Newly visible session IDs attributed to the current Vending UID and selected user; no ID alone proves no download |
| `old_uid_session_ids` | Different known installer UID in the same Android user; not proof of a live session owned by today's Play Store |
| Huawei components / running hint | APK declarations and activity-service dump hints only; firmware integration and caller-visible signatures remain unknown |
| GSF/registration | GSF package metadata and Check-in log counts only; no GSF ID or security token is read, and registration state remains unknown |
| `log_event_counts` | Counts of fixed patterns in a rolling last-3000-line buffer; overlapping captures must not be subtracted as independent time windows |

Only allowlisted metadata/numeric events leave memory. Raw log lines, tokens,
accounts, URLs, arbitrary exception messages, intents and response bodies are
never saved, even for unknown secret formats. Device model/build, versions,
package UIDs, session IDs and optional APK hashes remain visible: review the
report before sharing. The tool deliberately provides no raw-log switch.

Schema 2 fixes schema 1's ambiguous missing fields and session ownership.
Recapture old reports; comparing different schemas/users/build metadata fails.
Matching metadata does not prove two captures came from the same physical phone.

## Tests and limits

```bash
# Java 17's javac and java must be on PATH as well as Python.
python3 -m unittest discover -s tools/psmarts-diagnostics/tests -v
```

The tests use synthetic ADB responses, transient files and a JVM HTTP transport
fixture. The fixture compiles the production `HttpFormClient` with host-only
Android logging/URI/stream shims; it verifies credential transport and response
parsing without credential logging, including numeric parse errors. It does not
test Android URI implementation, a real account, Huawei firmware or Play Store.
GitHub Actions additionally builds `VtmHuaweiDebug`, verifies its signature and
Huawei manifest components, and runs the existing five `CryptoTest` tests.
CI artifacts contain test XML only, never device reports or raw logs.

See [analysis and evidence table](../../docs/PLAYSTORE-ROOTLESS-ANALYSIS.md).

## Single-command, privacy-safe Play Store trial

The optional `trial.py` wrapper runs the existing schema-2 snapshot collector
and the fixed-event `live_trace.py` together. This avoids shell-variable loss
between terminal sessions and creates uniquely named private folders.

```bash
# Run from the GmsCore checkout (experiment/psmarts-live-events):
python3 tools/psmarts-diagnostics/trial.py --seconds 45
```

Open a **free** Play Store app page without touching Install. Press Enter in the
terminal when asked. After the before snapshot, the program starts the 45-second
read-only recording and asks you to tap Install **once**. It then records the
after snapshot and compares active sessions automatically.

Files are located under
`~/psmarts-test/trial-<generated>/`: `before.json`,
`events.json`, `after.json`, `comparison.json`, `summary.json`.
The program prints the full run directory; you do not need a persistent
`RUN_DIR` environment variable. Every file is created private (`0600`);
the run directory is `0700`.

`summary.json` contains only fixed event names, numeric counts, a conservative
strongest-observed-stage label and explicit unknowns. It **does not diagnose
causality**, prove signature spoofing, confirm downloaded APK bytes or mark an
installer session as a successful install. A missing/denied live log is recorded
as unknown; the before/after session comparison still runs.

For devices whose live logcat is unavailable:

```bash
python3 tools/psmarts-diagnostics/trial.py --no-live
```

Use `--user 0` for the primary Android user and `--serial SERIAL` if more
than one ADB device is connected. Never paste raw logcat / token-containing
tracebacks into a public GitHub issue. Review `summary.json` and
`comparison.json` before sharing.

