# P Smart S Termux package/service inventory — 2026-11-08

Device build: AQM-L21A 12.0.0.226(C432E5R5P3), reported model AQM-LX1, Android 10/API 29. User provided output from unprivileged Termux:

- `/system/bin/service list | grep -Ei 'signature|package|huawei'` displayed `package` (`android.content.pm.IPackageManager`), `package_native`, Huawei `securityserver`, `authentication_service`, and other Huawei services. No named `signature` Binder service appeared in this filtered output.
- `/system/bin/pm list packages | grep -Ei 'microg|gms|signature|huawei'` included `com.google.android.gms` and `app.revanced.android.gms`. The latter is a separate ReVanced GmsCore package and should not be conflated with the system-facing `com.google.android.gms`.
- Many Huawei packages were visible. This is a partial, keyword-filtered inventory and cannot establish whether a private firmware feature is absent.
- No package named `com.huawei.signature.diff` was shown, but that string is also an intent action / service class namespace in microG's own fake-signature Huawei flavor, **not** necessarily an installed Android package name or global ServiceManager registration.

## Evidence interpretation

Existing independent v0.4 app reported `PackageManager.checkSignatures(GMS, Play) = NO_MATCH (-3)` and `hasSigningCertificate(GMS, Play cert) = false`. The Termux inventory does not reverse or explain those results.

The fork's `fake-signature/src/huawei/AndroidManifest.xml` declares an exported Android service using action `com.huawei.signature.diff`. This is not the same namespace as `/system/bin/service list` (global Binder services); a missing entry in the latter is **not a valid negative test** for this Android component.

## Next read-only checks in Termux

```sh
/system/bin/pm path com.google.android.gms
/system/bin/dumpsys package com.google.android.gms | grep -Ei -C 3 'com.huawei.signature.diff|SignatureService|signature.diff|versionName'
```

Output may be truncated or blocked for an unprivileged caller. Avoid sharing account identifiers, tokens, or full unfiltered dumpsys. Seeing a declared service or a running service does **not** establish Huawei firmware invokes `querySignature`.

No system APK patching, privileged permission grant, root, reset, firmware downgrade, or uninstall is authorized by these findings.
