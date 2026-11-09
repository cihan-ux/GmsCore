# Huawei microG — Rootless AccountManager visibility probe

**Standalone APK:** `dev.cihan.microg.accountprobe`

Purpose: distinguish a missing `com.google` authenticator from an account that
Android does not expose to an independent application. This is a **diagnostic
baseline**, not a fix or a test of Chrome's internal account-picker API.

## Safety
- Different app ID from `com.google.android.gms`: does not replace or uninstall microG.
- Requests only `GET_ACCOUNTS`, at runtime and with explicit user action.
- No `INTERNET` permission, network calls, account modifications, or Google token reads.
- Never displays, stores, or logs account email addresses, names, passwords or tokens.
- Only reports the number of Google accounts visible **to the probe's own package**.
  It **cannot** determine how many accounts are visible specifically to Chrome.
- Works on Android 10 / EMUI 12 (minSdk=29); also suitable for comparing Android 11 LineageOS.
- Does **not** add signature spoofing to EMUI and cannot prove Chrome login will work.

## How to run
1. Install the `app-debug.apk` produced by the
   `Huawei microG account visibility (read-only probe)` Actions workflow.
   Verify the ZIP and APK SHA-256 from the build logs.
2. Open **microG Account Probe**.
3. Tap **Hesap iznini iste** if Android offers a permission prompt.
4. Tap **Yeniden kontrol et** and read the account *count*, Google authenticator
   presence, and Chrome/microG package presence.
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
