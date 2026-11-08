# Rootless LSPatch boundary — Huawei P Smart S (AQM-LX1)

## Device finding

- Android 10 / API 29, EMUI 12.
- The user reports that LSPatch does **not** allow patching system apps. This is consistent with LSPatch's design: it repackages a selected APK and does not inject code into Android's `system_server` / `PackageManagerService`.
- **Do not try to bypass system-app protections** by uninstalling, disabling, converting, or replacing Huawei/Android system apps.
- `dev.cihan.fakegapps.probe` is a disposable user app, not a system app. Its `LOCAL XPOSED HOOK: NOT DETECTED` only describes that Probe's process during the screenshot.
- The independent unpatched v0.4 diagnostic already reports `checkSignatures(GMS, Play) = NO_MATCH (-3)` and `hasSigningCertificate(GMS, Play cert) = false`. Repatching the Probe does not change those platform results.

## Source-grounded Huawei investigation

Reviewed source in this fork:

- `fake-signature/src/huawei/AndroidManifest.xml`: exported service `com.huawei.signature.diff.SignatureService`, intent action `com.huawei.signature.diff`, running in `:signature`.
- `fake-signature/src/huawei/java/com/huawei/signature/diff/SignatureService.java`: Binder `onTransact` explicitly rejects callers with `Binder.getCallingUid() > 10000`, returning an exception.
- `querySignature(packageName, suggested)` returns strings selected by its internal allowlist/database and defaults; it does not itself replace Android PackageManager's installed signing certificate.
- `InitProvider` and `InitReceiver` attempt to start the service. A service-start event **does not prove** Huawei's proprietary firmware actually binds to or calls `querySignature`.

**Key inference:** A normal third-party unprivileged APK cannot establish availability of this Huawei integration merely by calling the service directly. The service's presence also does not demonstrate that Huawei firmware is wired into it.

## Practical next gate

1. Preserve existing microG-HW, Play Store/Companion, system apps, accounts, and firmware.
2. Treat the existing v0.4 screenshots as a documented **negative platform signature comparison** for this installation.
3. If testing LSPatch mechanics, patch/reinstall **only** the standalone Probe APK with its test module in scope; inspect the two local canaries. Do **not** represent a positive canary as global spoofing.
4. Before proposing new code, inspect **non-invasive** firmware capability evidence for this exact build (EMUI build identifier, relevant available system service information / narrowly filtered event observations without logging credentials). Distinguish `unknown`, `unsupported`, and `present but not invoked`.
5. No root, downgrade, reset, privileged grant, system APK replacement, or modification of real Google/Huawei packages.

This note is a technical constraint and follow-up research plan, **not** a working signature-spoofing implementation.
