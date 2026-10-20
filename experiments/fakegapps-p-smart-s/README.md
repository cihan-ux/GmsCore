# FakeGApps P Smart S Edition (experimental)

**Android 10 / EMUI 12 | Huawei P Smart S AQM-L21A / AQM-LX1 | ROOTLESS PROOF OF CONCEPT**

This is a distinct, experimental GPLv3 adaptation of [FakeGApps 6.6](https://github.com/whew-inc/FakeGApps/tree/6.6). It explores **app-process-only** Xposed hooks under **LSPatch**, with **no system_server access**.

> **Not a working system-wide spoofing solution.** This edition **will not** make the original Google Play Store work, pass microG's “System spoofs signature” self-check, certify Play Protect, or grant Google login by itself. It is a controlled way to demonstrate why the original FakeGApps system hook fails under rootless LSPatch.

## What is changed?

* Original FakeGApps 6.6 hooks `com.android.server.pm.PackageManagerService.generatePackageInfo` in the **android** process.
* This experimental edition instead hooks the local `android.app.ApplicationPackageManager.getPackageInfo(...)` **only inside** `dev.cihan.fakegapps.probe` (an isolated test app). It modifies `PackageInfo.signatures` and, when reflection permits, `PackageInfo.signingInfo` for requests concerning `com.google.android.gms` and `com.android.vending`.
* Deliberately **does not** hook `PackageManager.checkSignatures`, Binder `IPackageManager`, APK installer, account services, or other system paths.
* `xposedscope` defaults **only to the probe** — NEVER to Play Store, GMS, Android System, or real-user apps.
* Uses a different application ID from the official FakeGApps so it does not overwrite it.

## Limitations & risk

* A local `getPackageInfo` response seen by the probe **does not imply** that PackageManagerService, other applications, Google account services, install-time checks, or Play Protect see the same signature.
* On Android 10, `SigningInfo` is constructed with non-SDK reflection, so this path **may fail** even if the legacy `signatures` field changes. Errors go to Xposed logs.
* **LSPatch repacks and re-signs patched APKs**. Only patch the throwaway `probe` APK; do not patch or uninstall `com.google.android.gms`, `com.android.vending`, or user apps. This project does not alter existing Google account data.
* A missing GMS package is reported as missing; no reinstall/restore is attempted.
* Tested status: **source prepared, NOT device-tested or build-verified yet**. Do not mark the experiment successful without before/after fingerprints and logs.

## Project layout

* `module/`: installable Xposed module for a **probe-only** app-side hook.
* `probe/`: standalone Android activity displaying legacy and modern signature SHA-256 hashes for GMS and Play Store.
* `scripts/inspect.sh`: read-only ADB diagnostics.

## Building

Open this directory as a **separate project** in Android Studio, or run:

```bash
gradle :module:assembleDebug :probe:assembleDebug
```

Requires JDK 17, Gradle 8.9+, Android SDK platform 35 and Google Maven; Gradle downloads the Xposed compile-only API from `api.xposed.info`. The GitHub Actions workflow (if enabled) builds both APKs.

Outputs:
* `module/build/outputs/apk/debug/module-debug.apk`
* `probe/build/outputs/apk/debug/probe-debug.apk`

## Safe test procedure (Huawei P Smart S, Android 10)

1. Check that ADB + Shizuku run. No root, no bootloader operations.
2. `adb install probe/build/outputs/apk/debug/probe-debug.apk` and launch **P Smart S Signature Probe**. Save baseline GMS/Play Store hashes (the GMS package may be missing).
3. Install `module-debug.apk`, a **separate** Xposed module from original FakeGApps 6.6.
4. In LSPatch, patch **only the probe APK** in Manager mode and enable **FakeGApps P Smart S Edition** in the probe's module scope. Do not patch any Google package. Installation may require removing the **probe only** because LSPatch changes its signature; no accounts are associated with the probe.
5. Open the patched probe and record its fingerprints. Read logcat with `adb logcat -d -s Xposed:* | grep -i 'PSmartS\|FakeGApps'` (logs may not be visible on every build).
6. Compare before/after. Local hash change => app-process hook demonstrated; it **does not** prove system spoofing. No hash change => inspect scope, load timing, errors.
7. If desired, uninstall **only the probe and this edition's module**. Leave real microG-HW and Play Store untouched.

## License and attribution

This module is based on FakeGApps 6.6 by its upstream contributors and carries the **GNU GPL v3**. This modification was prepared on 2026-10-20. Refer to [LICENSE](LICENSE) and [upstream project](https://github.com/whew-inc/FakeGApps).

This is a research experiment, not an endorsed Google, Huawei, microG, Xposed or LSPosed application.
