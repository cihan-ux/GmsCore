# P Smart S Play Store Diagnostics (read-only)

Targets **Huawei P Smart S AQM-L21A**, Android 10 / EMUI 12.0.0.226.
Only Python 3 and ADB (USB debugging authorized) are required. Does not
install apps, grant privileges, clear logcat, launch Play Store or change accounts.

## Capture before/after one install attempt

From the repository root on Linux Mint:

```bash
python3 tools/psmarts-diagnostics/diagnose.py snapshot --output /tmp/psmarts-before.json
# Now tap Install once on the phone, then return to the terminal.
python3 tools/psmarts-diagnostics/diagnose.py snapshot --output /tmp/psmarts-after.json
python3 tools/psmarts-diagnostics/diagnose.py compare   --before /tmp/psmarts-before.json   --after /tmp/psmarts-after.json   --output /tmp/psmarts-diff.json
cat /tmp/psmarts-diff.json
```

If USB debugging or logcat is unavailable, run snapshot with `--no-logcat`.

**Security:** The tool does **not** write raw logcat data, Google OAuth tokens,
account names, app labels, arbitrary log messages or request URLs. It records only
counts of predefined error patterns. Reports are created with file permissions
`0600`. It does include the model, firmware, installed GMS/GSF/Vending package
versions, Android UIDs and installer session IDs; inspect before public sharing.
**Never upload** `adb logcat` raw output to a public GitHub issue or PR.

## Interpreting results

- `packages.com.android.vending.install_permissions_effective` distinguishes
  requested permissions from actual Android permission state.
- `packages.com.android.vending.location_class=user_app` is not a privileged
  system installation.
- `huawei_signature_components_in_gms_apk=true` means the component is
  listed by the installed APK, **not** that Huawei firmware implements the
  differentiated-signature integration.
- `new_session_ids=[]` means no new *visible* Vending session appeared
  between snapshots; this alone cannot identify whether Finsky, network,
  auth, download delivery or the installer caused it.
- `old_uid_session_ids` compares historical installer session ownership
  to the current Play Store UID; old sessions may survive app reinstall.
- `gms_adid_unavailable` is an advertising-ID symptom, **not a proven
  Play Store download blocker**.
- The tool does not establish CTS certification, successful spoofing,
  downloaded APK bytes or installation success.

## Offline tests

```bash
python3 -m unittest discover -s tools/psmarts-diagnostics/tests -v
```

See [technical findings](../../docs/PLAYSTORE-ROOTLESS-ANALYSIS.md).
