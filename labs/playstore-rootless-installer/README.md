# P Smart S: rootless installer laboratory (Android 10)

**Status: experimental standalone installer proof-of-concept; NOT a Play Store fix.**

This project is deliberately **not added to the GmsCore build**. It uses the normal Android PackageInstaller session and **always needs Android's user consent**. It does not request INSTALL_PACKAGES, manage accounts, use root, alter the system, or modify any installed microG or Play Store APK.

## Why we are doing this

The P Smart S (EMUI 12 / Android 10) currently uses:
- GmsCore-hw: com.google.android.gms (NOGAPPS certificate).
- microG-RE: app.revanced.android.gms (MorpheApp certificate).
- A patched Play Store: com.android.vending (Experimental MicroG Play Store Patch certificate).

Logcat shows Play Store's request to AccountManager.getUserData(account type app.revanced) receives SecurityException (different app signing certificates). This operation is restricted by Android. The Play Store logs and UI also show downloads failing *before they start*. **That failure has not yet been conclusively traced to this AccountManager exception.** Its code catches the exception and continues. Therefore a user-approved installer alone cannot solve the early download failure.

## What this lab proves (and what it does not)

The app demonstrates a local APK install flow using SessionParams(MODE_FULL_INSTALL), openWrite(), fsync(), commit(), and STATUS_PENDING_USER_ACTION. It accepts a user-selected local APK using ACTION_OPEN_DOCUMENT. It cannot download packages from Google Play, spoof signatures, bypass installer permissions, or turn a third-party app into a privileged package.

**Never test by replacing GmsCore-hw, microG-RE, Play Store, or another important app.** Use only a disposable APK that you trust and whose installation you explicitly approve.

## Build (GitHub Actions)

On branch `playstore-rootless-installer-lab`, the separate workflow `Play Store Rootless Installer Lab` should build this app after a push. Obtain the APK in that run's **Artifacts** area if the run succeeds. This is a signed *debug build* of our own lab app, NOT a new Play Store APK.

## Build (Linux Mint, optional)

From repository root:
```bash
./gradlew -p labs/playstore-rootless-installer :app:assembleDebug --no-daemon --max-workers=2
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
6. When finished, disable the lab's unknown-app-install permission. To remove **only the disposable sample**, use `adb uninstall org.psmarts.rootlesslab.smoke`.

This verifies Android's user-approved APK installation, **NOT** Google Play's ability to download an app. Do not infer that Play Store is repaired from success of this smoke test.
