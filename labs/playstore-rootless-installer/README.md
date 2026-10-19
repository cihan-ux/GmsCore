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
