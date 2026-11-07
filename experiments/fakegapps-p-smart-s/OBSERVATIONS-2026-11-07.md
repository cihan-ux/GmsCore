# Huawei P Smart S: device observations (2026-11-07)

Device: HUAWEI AQM-LX1, Android API 29 (EMUI 12). Source: user-provided text from the independent v0.4 diagnostics app and a screenshot of the standalone Signature Probe. Only non-personal test output is transcribed; screenshot is not uploaded.

## Independent, unpatched v0.4 diagnostics

| Measurement | com.google.android.gms | com.android.vending |
| --- | --- | --- |
| GET_SIGNATURES SHA-256 | `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165` | `f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83` |
| GET_SIGNING_CERTIFICATES SHA-256 | Same as GET_SIGNATURES | Same as GET_SIGNATURES |
| On-disk APK archive certificate SHA-256 | Same as GET_SIGNATURES | Same as GET_SIGNATURES |

- `PackageManager.checkSignatures(GMS, Play)`: **NO_MATCH (-3)**.
- `hasSigningCertificate(Play, Play cert)`: **true** (control).
- `hasSigningCertificate(GMS, Play cert)`: **false**.

**Supported result:** In this independent process and device state, the platform-facing checks do not recognize the installed microG-HW GMS APK as signed by the same certificate as Play Store.

**Not established:** Play Store's internal call path, root cause of an installation/download failure, availability of Huawei-specific privileged signature service, or any universal claim about all microG/Huawei versions.

## Separate Signature Probe screenshot

- `LOCAL XPOSED HOOK: NOT DETECTED`.
- `LOCAL SIGNATURE CONTROL: NO LOCAL OVERRIDE DETECTED`.
- The probe's PackageManager certificate fingerprint matches its on-disk APK certificate fingerprint.
- The probe sees GMS `9bd06727...` and Vending `f0fd6c5b...`, consistent with the independent v0.4 app.

**Interpretation:** No app-process override was demonstrated *in this screenshot*. This alone cannot distinguish an unpatched Probe APK, an unconfigured LSPatch scope, a module-loading failure, or a version mismatch. Historical v0.3 evidence of a successful probe-only canary is not negated; these may be different Probe installations or states.

## Next test (isolated, no system changes)

1. First decide whether only platform signature diagnosis is needed. The independent v0.4 result above already answers that narrow question; **no more patching is required to establish NO_MATCH**.
2. If investigating the probe's `NOT DETECTED` label, verify which Probe APK is installed and whether that *probe only* was LSPatch-patched with the module enabled in its scope. Do **not** patch `com.google.android.gms`, `com.android.vending`, Gmail, Android System, or another user app.
3. A new correctly patched probe-only run should display `LOCAL XPOSED HOOK: ACTIVE (PROBE PROCESS ONLY)`; then inspect the independent positive control `LOCAL SIGNATURE CONTROL: DIFFERENT (LOCAL OVERRIDE OBSERVED)`.
4. Neither positive control would establish system-wide spoofing. Do not reset, root, downgrade, grant privileged permissions, or remove the user's installed GMS/Play Store packages.

**Research decision:** Signature spoofing needed by independent app or platform verification cannot be concluded from APK-only microG modifications or a probe-scoped LSPatch module. Any proposal to bridge Huawei's proprietary signature facility requires separate reproducible evidence before code changes.
