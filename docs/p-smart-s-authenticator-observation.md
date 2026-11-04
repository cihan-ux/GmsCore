# Huawei P Smart S authenticator observation

Observed through `adb shell dumpsys account` (2026-11-04). Email addresses intentionally omitted.

- `com.google` -> `com.google.android.gms/com.google.android.gms.auth.account.authenticator.GoogleAccountAuthenticatorService`, UID 10618.
- `app.revanced` -> `app.revanced.android.gms/org.microg.gms.auth.loginservice.GoogleLoginService`, UID 10434.
- `com.huawei.hwid` -> `com.huawei.hwid/com.huawei.hms.runtimekit.stubexplicit.HwIDHwAccMgrService`, UID 10058.
- `com.gbox` -> `com.gbox.android/com.gbox.android.components.CustomAuthenticationService`, UID 10466.
- AccountManager reported a disconnected `addAccount` session for `com.google` when captured; whether it remains active is unknown.

## Consequence
Android currently resolves `com.google` to official GMS, not to a modern microG provider. The existing microG fork's `user` flavor may install alongside official GMS but would claim the same account type; it must not be assumed to win registration. The Huawei flavor retains the `com.google.android.gms` package ID and would conflict with the official GMS installation/signature. No phone-side changes should be made before a controlled emulator test.

## Next milestone
Inspect `GoogleLoginService` source and test duplicate authenticator registration in an Android 10 emulator with synthetic accounts. Do not include email addresses, credentials, tokens, or account dumps in the repository.
