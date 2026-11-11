# Experimental Huawei microG account-visibility fix (v1)

## Scope
This branch is based on the `cihan-ux/GmsCore` master branch and is **separate** from
the existing FakeGApps signature-diagnostics PR #8. It changes only microG's existing-account
visibility update on Android 8+ when the `Auth.VISIBLE` account-finding preference is changed.

microG currently sets `PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE` during the account login
process, but not when the setting is enabled later. Accounts created before enabling
the setting may remain invisible to applications that enumerate `com.google` accounts.

Implementation:
- For the opt-in `Auth.VISIBLE` toggle, also set explicit per-package account visibility
  for the stable Android Chrome package (`com.android.chrome`). Modern Chrome may not
  rely on the pre-Android-O legacy visibility key. This is experimental and limited to
  Chrome, not a grant to all installed applications.
- Setting enabled: set `VISIBILITY_USER_MANAGED_VISIBLE` for pre-existing `com.google` accounts.
- Setting disabled: restore `VISIBILITY_UNDEFINED` (the platform default) for the legacy and Chrome keys.
- Skip Android versions before API 26.
- Log AccountManager errors without logging account names or credentials.

Related upstream report: https://github.com/microg/GmsCore/issues/3805

## Safety and limitations
- This does **not** add signature spoofing to EMUI 12.
- This does **not** guarantee Chrome login, Gmail/Drive sign-in, or synchronization.
- The test APK is **not signed with the user's existing microG key** and may be impossible
  to install over the currently installed microG package.
- Do **not** uninstall the working microG installation on the Huawei merely to test this APK.
  Removing it may disrupt the Google account and working notification registrations.
- No changes to the phone, firmware, bootloader, kernel or partition contents were made.
- The build job is compilation-only, not on-device verification.

## Regression-test plan
1. Use a spare test environment with compatible microG signing, not the user's main Huawei.
2. Add a `com.google` account while **Allow apps to find accounts** is disabled.
3. Confirm a test app relying on `getAccountsByType("com.google")` cannot enumerate it.
4. Enable **Allow apps to find accounts** after account creation.
   Check Android 8+ account visibility separately for the legacy key and `com.android.chrome`.
   Do not log email addresses or access tokens.
5. Confirm the test app can now enumerate the account without removing/re-adding it.
6. Disable the setting. Confirm visibility returns to the platform default.
7. Repeat on Android 8+ and verify Android 7 or older does not call the new API.
8. Compare separately with Chrome account picker and microG Self-Check on the test device.
9. Confirm that if Chrome is not installed, the setting still updates the legacy key and does not crash.
10. Confirm that disabling the toggle resets the Chrome-specific visibility override.

## Build
GitHub Actions workflow:
`.github/workflows/huawei-account-visibility-build.yml`

Gradle task:
`./gradlew :play-services-core:assembleMapboxHuaweiDebug`

The workflow attempts to upload a debug APK under the artifact name
`microg-huawei-account-visibility-debug` after successful compilation.

## 2026-11-09: build and signing audit

The `mapboxHuaweiDebug` APK was successfully built in GitHub Actions
run 42615774413, and the variant was confirmed in its build log.
Run 42616836998 produced a ZIP with a manifest containing the application ID
and APK SHA-256. These prove packaging, **not** that Google Chrome login works.

**The independent read-only signature diagnostics APK already exists in PR #8**:
https://github.com/cihan-ux/GmsCore/pull/8
Its own successful workflow is:
https://github.com/cihan-ux/GmsCore/actions/runs/42434974136
Download the `fakegapps-p-smart-s-experimental-apks` artifact and use **only**
`diagnostics/build/outputs/apk/debug/diagnostics-debug.apk` for this audit.
Do not install `module-debug.apk` or `probe-debug.apk` merely to do the audit.

### Safe order for a device (root/bootloader unlock NOT required)
1. **Do not uninstall or clear data for the installed microG**. Keep the account
   and current notifications intact.
2. Optionally install the separate `dev.cihan.fakegapps.diagnostics` read-only
   diagnostics APK from PR #8. It uses its *own* application ID, and does not replace
   `com.google.android.gms`. Inspect its source before installation.
3. Read `GET_SIGNING_CERTIFICATES` for installed
   `com.google.android.gms` and record the SHA-256 certificate fingerprint.
   This fingerprint is **not** the APK file's SHA-256; they are different values.
4. Compare it with `signerCertificateSha256` in the *same build's* experimental
   ZIP `build-manifest.json`. Do not compare to the fingerprint from another run:
   ephemeral CI debug keys can change from run to run.
5. If signing certificates do not match, an Android package update over the
   existing microG is refused. **Stop here; do not uninstall or bypass this.**
6. If they match, the APK still has not been verified on AQM-LX1; the Chrome
   account picker and signature spoofing require separate device-level tests.

### Interpretation
- A signature mismatch is a **package update blocker**, not evidence that the
  Android Framework's signature spoofing works or does not work.
- The PR #8 diagnostic APK measures package signatures and PackageManager API
  behavior. It does **not** read account names, emails or Google tokens, and
  it does **not** directly measure `AccountManager`'s per-app visibility for Chrome.
- The v1 patch in PR #11 updates `AccountManager` visibility but does **not**
  implement privileged EMUI framework signature spoofing.
- Do not claim Chrome login is fixed until observed on a test device.

## 2026-11-12: Huawei AQM-LX1 Account Probe v0.3 observations

User-supplied read-only results (no account identifiers recorded):

- Device: Huawei AQM-LX1, Android API 29 (EMUI 12).
- Installed GMS package: `com.google.android.gms` v0.3.17.252432-hw, enabled.
- Stable Chrome: `com.android.chrome` v157.0.8103.50, enabled.
- Play Store package `com.android.vending` present and enabled.
- Google authenticator: `com.google.android.gms`.
- One Google account visible **to the independent diagnostic package**
  `dev.cihan.microg.accountprobe`, with its own `GET_ACCOUNTS` permission granted.
- Chrome `GET_ACCOUNTS`: declared but not granted; `GET_ACCOUNTS_PRIVILEGED`:
  not requested.
- HTTPS handler inventory from this independent probe listed
  `com.brave.browser`; that does **not** mean Chrome was absent, because
  a separate PackageManager query confirmed Chrome installed and enabled.
- Tapping the Chrome profile icon displayed Chrome's settings and Google
  services controls. **No Google account chooser appeared** in the user's
  screenshots. Chrome setting to permit sign-in was enabled.

### Interpretation and boundaries

The denied `GET_ACCOUNTS` permission is a **diagnostic observation, not a
confirmed root cause**. Android's documented `AccountManager.getAccountsByType`
behavior for apps targeting Android O (API 26) or newer does not use this
permission to determine which accounts are returned; per-app visibility set
by the authenticator or user authorization governs the result.
Source: https://developer.android.com/reference/android/accounts/AccountManager

- Probe visibility of one account **does not** establish visibility to Chrome.
- Independent apps cannot call `getAccountVisibility(account, "com.android.chrome")`
  or `getAccountsAndVisibilityForPackage` as the real `com.google`
  authenticator; these APIs require signature matching with the authenticator.
- The patch in this PR supplies an *experimental* Chrome-specific
  `setAccountVisibility` update from microG settings, but the test build
  is signed with a CI debug key. It is not a verified in-place replacement
  for the existing microG installation.
- An app-visible account, Chrome account picker, token acquisition, and
  framework signature spoofing are distinct test targets.
- Do not suggest forcing `pm grant` or uninstalling/reinstalling microG
  solely on the basis of Chrome's denied `GET_ACCOUNTS` permission.

### Next safe investigation

1. Record (without email or tokens) whether microG's own **Allow apps to
   find accounts** setting is enabled on the unchanged installation.
2. If available, use a *redacted* Android log around Chrome's attempted
   sign-in to differentiate "no attempt made" from `AccountManager` or
   `PackageManager` access-denied events. Do not share raw logcat dumps
   containing URLs, emails, cookies, or auth tokens.
3. With a disposable test ROM/environment where the microG signing key is
   controlled, run the Chrome-specific visibility patch and compare
   permission-independent account visibility and Chrome's sign-in behavior.
4. Independently confirm signature spoofing in microG Self-Check. A failed
   spoofing check is not fixed by the AccountManager patch alone.

### Do not overstate

**Chrome login remains unverified and unresolved on the reported Huawei.**
The goal of the next test is to identify which component is failing without
touching the user's functional microG installation.
