# P Smart S: rootless installer laboratory (Android 10)

**Status: experimental, fail-closed disposable APK smoke test; NOT an Aurora or Play Store replacement/fix.**

This project is deliberately **not added to the GmsCore build**. It uses the normal Android PackageInstaller session and **always needs Android's user consent**. It does not request INSTALL_PACKAGES, manage accounts, use root, alter the system, or modify any installed microG or Play Store APK.

## Why we are doing this

The P Smart S (EMUI 12 / Android 10) currently uses:
- GmsCore-hw: com.google.android.gms (NOGAPPS certificate).
- microG-RE: app.revanced.android.gms (MorpheApp certificate).
- A patched Play Store: com.android.vending (Experimental MicroG Play Store Patch certificate).

Logcat shows Play Store's request to AccountManager.getUserData(account type app.revanced) receives SecurityException (different app signing certificates). This operation is restricted by Android. The Play Store logs and UI also show downloads failing *before they start*. **That failure has not yet been conclusively traced to this AccountManager exception.** Its code catches the exception and continues. Therefore a user-approved installer alone cannot solve the early download failure.

## What this lab proves (and what it does not)

The app demonstrates a local APK install flow using SessionParams(MODE_FULL_INSTALL), openWrite(), fsync(), commit(), and STATUS_PENDING_USER_ACTION. It accepts a user-selected local APK using ACTION_OPEN_DOCUMENT, but the v0.1 safety gate accepts ONLY a NEW permissionless `org.psmarts.rootlesslab.smoke` APK, not any other app or update. It cannot download packages from Google Play, spoof signatures, bypass installer permissions, or turn a third-party app into a privileged package.

**Never test by replacing GmsCore-hw, microG-RE, Play Store, or another important app.** Use only a disposable APK that you trust and whose installation you explicitly approve.

## Build (GitHub Actions)

On branch `aurora-rootless-safe-smoke-v0.1`, the separate workflow `Play Store Rootless Installer Lab` builds JVM policy tests and both debug APKs after a push. The original `playstore-rootless-installer-lab` branch is retained unchanged. Obtain the APK in that run's **Artifacts** area if the run succeeds. This is a signed *debug build* of our own lab app, NOT a new Play Store APK.

## Build (Linux Mint, optional)

From repository root:
```bash
./gradlew -p labs/playstore-rootless-installer :app:testDebugUnitTest :app:assembleDebug :smoke-app:assembleDebug --no-daemon --max-workers=2
```

Output: `labs/playstore-rootless-installer/app/build/outputs/apk/debug/app-debug.apk`.

## Protect the existing installation

- No APK uninstall, clearing app data, signing new Play Store APK, root, or EMUI rollback.
- No proprietary Play Store APK, private signing key, account credentials, or OAuth tokens should ever be committed to this public repository.
- Any future Play Store-specific patch must be built and verified independently, and will need a safe data-preservation/installation plan due to its differing signature.

## Safe test with a disposable APK

The same workflow also builds `PSmartS-Disposable-Install-Test`: a separate application (`org.psmarts.rootlesslab.smoke`) that has **no permissions**, does not connect to the network, and displays a simple confirmation message.

1. From the successful workflow run, download both Artifacts ZIP files.
2. Unzip `PSmartS-Rootless-Installer-Lab` and install `app-debug.apk` (lab app) on the phone.
3. Extract `smoke-app-debug.apk` from `PSmartS-Disposable-Install-Test`; copy it into the phone's Downloads folder. **Do not install this test APK manually.**
4. Open Rootless Installer Lab, select the copied `smoke-app-debug.apk`, grant the lab app the Android "Install unknown apps" permission only if requested, and follow the system install confirmation.
5. Verify via `adb shell pm path org.psmarts.rootlesslab.smoke`; a returned APK path means the test package was installed. Opening the `Installer Smoke Test` app should show a success message.
6. A second smoke install is blocked while the sample exists; do not experiment with replacing it. When finished, disable the lab's unknown-app-install permission. To remove **only the disposable sample** (if you choose), use `adb uninstall org.psmarts.rootlesslab.smoke`.

This verifies Android's user-approved APK installation, **NOT** Google Play's ability to download an app. Do not infer that Play Store is repaired from success of this smoke test.

## v0.1 fail-closed protections

Before the app creates an Android PackageInstaller session, it copies the selected file to a temporary *app-private* `.apk` and checks:
- APK parses successfully; fixed maximum size 20 MiB.
- Manifest package name is exactly `org.psmarts.rootlesslab.smoke`.
- Manifest requests **zero permissions**; unreadable permission metadata is rejected.
- The smoke package is **not already installed**; updates are always rejected.
- A validated session declares the expected package identity; Android still verifies the APK and asks for installation approval.

Any failed or unavailable check blocks installation and deletes the temporary copy. Package names alone are not cryptographic authenticity checks: get both artifacts only from a verified build in **your** repo, not from messages or third-party APK sites. This lab must never be used for real app updates. Its Android 10 code path has no root, no Shizuku API and no Device Owner capability.

## Aurora Store route (separate from the lab)

Aurora Store already supports **Session Installer** (normal confirmation) and **Shizuku Installer** (Shizuku is started separately). We do not patch or re-sign Aurora, override its app signature or request Device Owner. All Huawei/Google accounts, Morphe microG and existing installed apps remain untouched.

1. First use Aurora Store's **Session** installer to confirm normal installations already work on this phone.
2. Optionally install Shizuku from an official source and grant Aurora authorization through the Shizuku app.
3. On **Android 10**, Shizuku is started with a computer, not Android 11+ wireless debugging. After connecting and authorizing USB ADB, follow the command shown inside Shizuku (commonly `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`). Restart Shizuku after each phone reboot.
4. Select **Shizuku** in Aurora Store's installer settings only if Shizuku actually reports it is running and Aurora has been authorized.
5. Check a disposable, nonessential download before depending on the workflow. There is no guarantee that EMUI will support silent installs in every scenario.

**Never run `dpm set-device-owner`, remove Huawei/Google accounts, clear ChatGPT/Morphe data, or uninstall GmsCore/GSF/Play Store for this experiment.** Shizuku is not signature spoofing or real privileged Google Play Services.

References: https://auroraoss.com/wiki ; https://shizuku.rikka.app/guide/setup/
