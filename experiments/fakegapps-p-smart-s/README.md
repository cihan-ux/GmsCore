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
* Tested status: **v0.2 source updated, NOT device-tested; CI build status must be checked**. Do not mark the experiment successful without before/after fingerprints and logs.

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
4. Probe v0.2 displays **LOCAL XPOSED HOOK: NOT DETECTED** before patching. In LSPatch, patch **only the probe APK** in Manager mode and enable **FakeGApps P Smart S Edition** in the probe's module scope. Do not patch any Google package. Installation may require removing the **probe only** because LSPatch changes its signature; no accounts are associated with the probe.
5. Open the patched probe and check **LOCAL XPOSED HOOK: ACTIVE (PROBE PROCESS ONLY)**. This is a deliberate, visible canary: the Xposed module changes only the local probe's reported own `versionName` (adds `-hook-active`), proving the module ran in the probe process even if GMS/Play Store APKs already have Google signature fingerprints. If the indicator stays **NOT DETECTED**, module loading is not verified; check scope, LSPatch local vs manager mode and logs. Record fingerprints separately. Read logcat with `adb logcat -d -s Xposed:* | grep -i 'PSmartS\|FakeGApps'` (logs may not be visible on every build).
6. Compare before/after. Local hash change => app-process hook demonstrated; it **does not** prove system spoofing. No hash change => inspect scope, load timing, errors.
7. If desired, uninstall **only the probe and this edition's module**. Leave real microG-HW and Play Store untouched.

## License and attribution

This module is based on FakeGApps 6.6 by its upstream contributors and carries the **GNU GPL v3**. This modification was prepared on 2026-10-20. Refer to [LICENSE](LICENSE) and [upstream project](https://github.com/whew-inc/FakeGApps).

This is a research experiment, not an endorsed Google, Huawei, microG, Xposed or LSPosed application.

## v0.2: Why the visible hook canary was added

On this P Smart S, `apksigner` reported `com.google.android.gms` **and** `com.android.vending` actually signed with the SHA-256 fingerprint `f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`, matching the prior probe view, even with the module disabled. Therefore matching fingerprint values **cannot** establish whether the module loaded. 

The canary is a local, in-memory probe-only `PackageInfo.versionName` suffix. Never use the canary for claims about system-wide signature spoofing, Play Protect or Google account support.

## v0.3: Local signature positive control (not system spoofing)

A live P Smart S AQM-LX1 (Android 10 / EMUI 12) probe confirmed
`LOCAL XPOSED HOOK: ACTIVE (PROBE PROCESS ONLY)` and the bridge log
`FakeGApps PSmartS: probe-only PackageInfo hook registered`.
Its LSPatch logs reported `Use manager: false`, indicating embedded mode;
the `dex2oat` denial did not prevent module initialization.

v0.3 introduces an intentionally narrow **positive control**:
* Only inside `dev.cihan.fakegapps.probe`, querying **the probe's own** PackageInfo with signature flags returns a substituted Google certificate.
* The probe compares that local result with the **real on-disk APK** certificate via `getPackageArchiveInfo`, which is not hooked by this module.
* `LOCAL SIGNATURE CONTROL: DIFFERENT (LOCAL OVERRIDE OBSERVED)` demonstrates the in-app result was changed; `NO LOCAL OVERRIDE DETECTED` or `INCONCLUSIVE` must not be reported as success.
* Existing GMS/Vending SHA-256 fingerprints can match the Google certificate without any module intervention, so they are not a sufficient positive test.
* This does **not** affect Android PackageManagerService, installers, signatures of installed packages, other apps, Google login, or Play Store installations.

**Test upgrade:** install newly built `module-debug.apk` and the **new patched probe**.
Because LSPatch changes an APK's signing key, an Android signature mismatch may require
uninstalling **only** `dev.cihan.fakegapps.probe` before installing the new patched probe.
Do not uninstall or patch microG, GMS, Play Store, or system packages.

## v0.4 — unpatched, side-by-side, read-only signature diagnostics

**Reason:** Direct ADB `apksigner` inspection of this P Smart S revealed that installed
microG-HW `com.google.android.gms` (0.3.17.252432-hw) is signed with
`9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165`, while
`com.android.vending` (19.5.13-all) is signed with
`f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`.
The earlier statement that both installed APKs have the Google fingerprint was mistaken:
the v0.3 app-process hook altered the *locally returned* GMS PackageInfo result.

The logs reported `GooglePlayServicesUtil: ... signature is invalid`, but did not
establish which exact signature API Play Store calls or whether that warning is the
direct cause of download failure.

**v0.4 adds a third, standalone APK:** `diagnostics-debug.apk`, application ID
`dev.cihan.fakegapps.diagnostics`. It reads and displays:
* Actual `GET_SIGNATURES` and `GET_SIGNING_CERTIFICATES` SHA-256 fingerprints for
  GMS and Play Store as seen by an **unpatched independent process**.
* Package archives when readable, using `getPackageArchiveInfo`.
* Platform `checkSignatures(com.google.android.gms, com.android.vending)`.
* Platform `hasSigningCertificate` for Play's cert, once against Play (control)
  and once against GMS.

This is a **read-only comparison** of several public Android APIs, not instrumentation
of Play Store's internal code. It cannot prove the exact Play Store verification
call path, change any signature, grant permissions, or make Play Store download apps.

**Side-by-side installation, no uninstall:**
1. Download `fakegapps-p-smart-s-experimental-apks` from the v0.4 GitHub Actions run.
2. Extract **only** `diagnostics-debug.apk`.
3. Run `adb install diagnostics-debug.apk` (or open the APK normally).
4. Launch **P Smart S Signature Diagnostics** and send its screen.
5. Do **not** patch Diagnostics with LSPatch. Keep the existing v0.3
   `dev.cihan.fakegapps.probe`, microG-HW and Play Store untouched.

No root, Shizuku, internet permission, Google account access or device write access
is needed. `module-debug.apk` and `probe-debug.apk` remain in the build for backwards
compatibility but are not needed for the v0.4 test.
