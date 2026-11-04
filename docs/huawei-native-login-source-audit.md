# Native Google login: initial source audit (2026-11-04)

Repository: cihan-ux/GmsCore; research branch only. No phone changes.

## Verified source paths
- `build.gradle`: androidMinSdk=19, androidTargetSdk=29, androidCompileSdk=35.
- `play-services-core/build.gradle`: `default`, `huawei`, and `user` target flavors. `user` changes applicationId to `org.microg.gms`; `huawei` retains default applicationId `com.google.android.gms` and adds `-hw` version suffix. Maps flavors are a separate dimension.
- `play-services-core/src/huawei/AndroidManifest.xml`: Huawei-specific feature metadata including auth manager visible, two-step verification, and checkin configuration. This is an existing flavor, not proof of compatibility with P Smart S C432.
- `play-services-core/src/main/AndroidManifest.xml`: `org.microg.gms.auth.loginservice.GoogleLoginService` advertises `android.accounts.AccountAuthenticator`; `org.microg.gms.auth.login.LoginActivity` has exported login intent.
- `play-services-core/src/main/res/xml/authenticator.xml`: accountType `com.google`, customTokens=true.

## Conflict hypothesis
- Official GMS 19.2.75 is installed as `com.google.android.gms` and GSF as `com.google.android.gsf`; both share UID 10618 on the test phone.
- `huawei` flavor shares package ID with official GMS and cannot be installed alongside it as a differently signed app.
- `user` flavor has a different package ID, but its authenticator still claims `com.google`. Android authenticator selection must be tested; coexistence does not mean this service becomes active.
- Morphe RE uses `app.revanced` account type, which must remain untouched.
- `com.google.android.gmt` third-party authenticator also claims `com.google`; it is a further potential conflict.

## Next safe validation (read-only)
On the phone, capture only authenticator registration metadata, no account names:
```sh
adb shell dumpsys account | sed -n '/RegisteredServicesCache/,/AccountManagerService/p' | head -100
```
This command may produce no useful output on EMUI; alternatively inspect the existing account dump locally and redact account names before sharing.

## Constraints
Do not install either flavor, uninstall official GMS, delete GSF, change existing accounts, or sign in with personal credentials. Next code step: trace the actual `GoogleLoginService` implementation and build a test of Android authenticator resolution in an emulator.
