# Experimental Huawei microG account-visibility fix (v1)

## Scope
This branch is based on the `cihan-ux/GmsCore` master branch and is **separate** from
the existing FakeGApps signature-diagnostics PR #8. It changes only microG's existing-account
visibility update on Android 8+ when the `Auth.VISIBLE` account-finding preference is changed.

microG currently sets `PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE` during the account login
process, but not when the setting is enabled later. Accounts created before enabling
the setting may remain invisible to applications that enumerate `com.google` accounts.

Implementation:
- Setting enabled: set `VISIBILITY_USER_MANAGED_VISIBLE` for pre-existing `com.google` accounts.
- Setting disabled: restore `VISIBILITY_UNDEFINED` (the platform default) for that key.
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
5. Confirm the test app can now enumerate the account without removing/re-adding it.
6. Disable the setting. Confirm visibility returns to the platform default.
7. Repeat on Android 8+ and verify Android 7 or older does not call the new API.
8. Compare separately with Chrome account picker and microG Self-Check on the test device.

## Build
GitHub Actions workflow:
`.github/workflows/huawei-account-visibility-build.yml`

Gradle task:
`./gradlew :play-services-core:assembleMapboxHuaweiDebug`

The workflow attempts to upload a debug APK under the artifact name
`microg-huawei-account-visibility-debug` after successful compilation.
