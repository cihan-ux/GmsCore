# GMT Google Login APK

Build from the current microG GmsCore source, not the 2020-era cloned APK.

- APK application ID: `com.google.android.gmt`
- Android AccountAuthenticator account type: `com.google`
- Login implementation: `play-services-core/src/main/java/org/microg/gms/auth/login/LoginActivity.java`
- Variant: `hmsHuaweiDebug` (signed with GitHub Actions' ephemeral Android debug key).
- Find **GMT-Google-Login-Updated-APK** under Actions build artifacts for this branch.

This creates a **new-signed replacement** instead of an in-place update to the App Cloner build.
Back up important data before uninstalling the old GMT application. The new signature
will not match the third-party APK signature and an install -r update can fail.

**Limit:** EMUI 12 does not automatically grant signature spoofing to microG-HW.
The Google sign-in page and account manager may work while Play Store/GMS certification
remains unsupported. Do not confuse functional account login with Play Store compatibility.
