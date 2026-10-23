# P Smart S Signature Inspector

Standalone read-only Android 10 test app, independent of GmsCore's APK. Reads the **calling app's** Android PackageManager view for GMS, Play Store and GSF, using GET_SIGNING_CERTIFICATES on API 28+. Computes SHA-1/SHA-256 of the reported X.509 certificate bytes. Compares the GMS SHA-1 with Google's published Android app signing certificate SHA-1 (38918a453d07199354f8b19af05ec6562ced5788). This is a diagnostic reference, not a proof of Google Play certification or that the Play Store sees the same signature.

The **on-disk** APK signer is distinct: inspect it with the existing opt-in `tools/psmarts-diagnostics/diagnose.py snapshot --inspect-apks` and a trusted Android SDK apksigner. The app never reads APK files directly. The Huawei service declaration check is not evidence of Binder invocation or ROM integration. A mismatch is not conclusive evidence of a specific root cause for Play Store download failures.

## Build

Use JDK 17, Android SDK 35 and Gradle 8.9+: `gradle --no-daemon :app:assembleDebug` from this directory. Or download the APK from the GitHub Actions workflow artifact `psmarts-signature-inspector-debug-apk`. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

No INTERNET permission, no account access, no root, no modification to installed packages. Target SDK 29 intentionally matches the tested Android 10 device and avoids newer package-visibility restrictions; min SDK 26. For production Android versions beyond 10, package visibility and compatibility must be revisited.
