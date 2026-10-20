# Huawei GMS Diagnostics v2.0

Huawei/EMUI, microG/GmsCore, original Google Play Services, Google Services Framework (GSF) and Play Store package compatibility diagnostics.

**Rootless, offline, read-only. No data collection, no Google account access and no automatic fixes.**

Package ID: `dev.cihan.gmshwassistant` (upgrades our earlier HW Assistant v1 app).

## Checks

- Manufacturer, model, Android/API and EMUI (where publicly readable)
- Version and signing certificate fingerprint of known GMS, GSF, Play Store, microG-HW, ReVanced microG, GMT and Huawei AppGallery components
- GSF and GMS: requested versus granted `MANAGE_USERS` and `INTERACT_ACROSS_USERS`
- Play Store: requested versus granted `READ_GSERVICES`, `WRITE_GSERVICES` and protected user-management permissions
- Google and app.revanced authenticator **type/owner only** (never emails or account names)
- Huawei signature diff service *visibility* (not evidence that signature spoofing is enabled)
- Scan again, copy report, share via Android chooser or save locally as TXT
- Open app details to inspect OS-provided package screens

**Limits:** Android prevents third-party apps from reading other packages' crash logcat. A visible authenticator does not prove a Google account can log in. A missing package may also reflect visibility restrictions. These checks cannot grant protected permissions, alter signatures or make original Play Store work.

For detailed ADB crash triage, on your trusted Linux Mint workstation use existing scripts:
- [tools/huawei_gms_diagnostics.py](../tools/huawei_gms_diagnostics.py)
- [tools/huawei_playstore_errors.py](../tools/huawei_playstore_errors.py)

Read all logs before sharing. Scrub email addresses, account identifiers, tokens and other personal information.

## Building

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle -p hw-assistant :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK: `hw-assistant/app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions automatically runs the tests and attempts an APK build on this branch. Download only from the matching workflow run. This is a development build; it has not yet been installed and validated on physical EMUI 12 hardware.

Source: `cihan-ux/GmsCore`, branch `huawei-gms-diagnostics-v2`. This implementation builds upon our existing GmsCore HW Assistant v1 and the earlier read-only ADB tools.

## Privacy and distribution

No `INTERNET`, `GET_ACCOUNTS`, `READ_LOGS`, `QUERY_ALL_PACKAGES` or storage permission requested. Only explicitly listed GMS-related packages are queried. The app never modifies installed packages or permissions. Clipboard copies are visible to other apps according to Android clipboard rules; use TXT export if appropriate.

If distributing standalone on F-Droid, first extract this independently buildable `hw-assistant/` project into a new **dedicated GitHub repository**, verify build reproducibility and release under a suitable clearly documented license compatible with any reused GmsCore code. Do not treat the licensing of all of GmsCore as automatically identical to that of the independently authored assistant.
