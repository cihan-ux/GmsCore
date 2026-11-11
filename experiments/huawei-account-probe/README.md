# Huawei microG — Rootless AccountManager visibility probe

**Standalone APK:** `dev.cihan.microg.accountprobe`

Purpose: distinguish a missing `com.google` authenticator from an account that
Android does not expose to an independent application, and identify Chrome-related
package presence without changing accounts or microG. This is a **diagnostic
baseline**, not a fix or a test of Chrome's internal account-picker API.

## Safety
- Different app ID from `com.google.android.gms`: does not replace or uninstall microG.
- Requests only `GET_ACCOUNTS`, at runtime and with explicit user action.
- No `INTERNET` permission, network calls, account modifications, or Google token reads.
- Never displays, stores, or logs account email addresses, names, passwords or tokens.
- Only reports the number of Google accounts visible **to the probe's own package**.
  It **cannot** determine how many accounts are visible specifically to Chrome.
- Works on Android 10 / EMUI 12 (minSdk=29); also suitable for comparing Android 11 LineageOS.
- v0.2 additionally checks stable, Beta, Dev, Canary and Chromium package names and
  lists local packages that can handle an HTTPS view intent. Some HTTPS handlers may not
  be browsers; this list is **not** a proof of Chrome being installed.
- On Android 10 with targetSdk 29, standard Android 11 package-visibility filtering
  should not hide Chrome from PackageManager; Huawei vendor behavior can still differ.
- The Google account count is only for this probe's package; neither the count nor the
  HTTPS handlers list directly tests Chrome's account visibility.
- Does **not** add signature spoofing to EMUI and cannot prove Chrome login will work.

## How to run
1. Install the `app-debug.apk` produced by the
   `Huawei microG account visibility (read-only probe)` Actions workflow.
   Verify the ZIP and APK SHA-256 from the build logs.
2. Open **microG Account Probe**.
3. Tap **Hesap iznini iste** if Android offers a permission prompt.
4. Tap **Yeniden kontrol et** and read the account *count*, Google authenticator
   presence, Chrome variant checks, and HTTPS handlers.
5. Compare these observations on the Huawei P Smart S and the Samsung A7.
6. Uninstall this standalone probe whenever desired; it does not remove Google
   accounts or change microG settings.

**Interpretation caveat:** A zero count for the probe can coexist with a Google
account visible to Chrome, because AccountManager visibility may differ by
application package. Successful account enumeration does not prove that OAuth
tokens, Chrome synchronization, or signature spoofing work.

## Build locally
```bash
gradle -p experiments/huawei-account-probe :app:assembleDebug
```
The GitHub Actions workflow uses Java 17, Gradle 8.9, Android SDK 35.

## v0.2 upgrade note

The CI debug signing key may be different between workflow runs. If Android reports
an incompatible package signature when updating v0.1 to v0.2, it is safe to uninstall
**only this independent Account Probe app** and install the v0.2 APK. The probe has
no stored Google credentials. Never uninstall or clear data for microG itself.

## v0.3 Chrome permissions diagnostics (2026-11-12)

The user's Huawei AQM-LX1, Android API 29, reports one `com.google` account
visible to the independent v0.1 probe, while Chrome 157 shows the Google
services settings screen rather than an account picker when the profile icon
is tapped. **These observations do not prove that Chrome has access to the
account or that Chrome even sends an auth request.**

This update prints the package hosting Android's `com.google` authenticator
and checks whether the installed stable Chrome package **requests** and is
**granted** `android.permission.GET_ACCOUNTS` and
`android.permission.GET_ACCOUNTS_PRIVILEGED`. It also retains the v0.2
package/HTTPS handler inventory.

Interpretation:
- If Chrome does not request a permission, this is **not automatically a bug**:
  modern Android account visibility may be provided by the authenticator or
  through user approval, independent of GET_ACCOUNTS.
- Even when GET_ACCOUNTS is granted, the result **does not show which accounts
  Chrome sees**. Chrome's own account selection, OAuth, and native sync remain
  separate from permission grants.
- This diagnostic does not install or patch the experimental microG APK,
  apply framework signature spoofing, or modify the user's Google account.
- On Android 10/EMUI, package permissions can be inspected without root,
  but some OEM-specific policies remain inaccessible to third-party apps.

To update: install the v0.3 `app-debug.apk` from the matching successful CI
artifact. If Android refuses an in-place update due to a different CI signing
key, remove **only** `dev.cihan.microg.accountprobe` (not microG or Chrome)
and install v0.3. Do not clear Google/Chrome/microG data.
