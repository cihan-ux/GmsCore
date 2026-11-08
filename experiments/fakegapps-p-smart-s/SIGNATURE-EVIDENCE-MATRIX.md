# P Smart S signature verification: evidence decision matrix

Target: Huawei P Smart S (AQM-L21A/AQM-LX1), Android 10 / EMUI 12, without root, bootloader unlock, downgrade, or system modification.

## What the existing experiments establish

- The v0.3 LSPatch positive control runs **inside the throwaway probe process** only. Its altered PackageInfo does not establish system-wide spoofing.
- The v0.4 standalone diagnostics app is **unpatched and read-only**. It can compare legacy signatures, SigningInfo, archive certificates, checkSignatures, and hasSigningCertificate as observed by that app.
- Earlier on-device APK inspection reported microG-HW GMS SHA-256 `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165`, while Vending reported `f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`. These are historical observations, not guarantees for future installations.

## Interpretation (do not overclaim)

| Observation | Supported inference | Not established |
| --- | --- | --- |
| Probe-only local signature differs from its on-disk archive | App-process interception works in the patched probe | System-wide spoofing |
| Unpatched diagnostics GMS signature differs from Google certificate | This independent app does not see a Google-signed GMS through that queried API | Which API Gmail/Play Store actually calls |
| `checkSignatures(GMS, Vending)` reports mismatch | Platform API considers the two installed package signatures different in this caller | Cause of every login or download failure |
| `hasSigningCertificate(GMS, Google cert)` returns false | This API does not recognize that cert for installed GMS | Whether Huawei's separate signature service is available |
| Self-Check reports signature spoofing supported | microG's own test condition passed | All third-party apps accept microG |
| Google login succeeds for one app | That app's login flow works in this configuration | Global compatibility or valid Play Protect certification |

## Next safe verification gate

1. Record exact device build, microG-HW version, and installed package identities **without** account identifiers or tokens.
2. Run the existing v0.4 **unpatched** diagnostics APK and capture its complete result screen. Do not patch Play Store, Gmail, GMS, or system packages.
3. Compare legacy GET_SIGNATURES, modern SigningInfo, archive signature, checkSignatures, and hasSigningCertificate outcomes. Mark absent or inaccessible data **unknown**, not failure or success.
4. Only after obtaining those observations, select a specific source-level hypothesis and add an isolated automated regression test. No claim of Huawei firmware support based on CI alone.

## Decision boundary

If independent PackageManager queries consistently return the installed microG certificate and platform signature comparisons reject the Google certificate, changing microG's APK-only Java logic cannot by itself change those platform responses. Any proposed Huawei-specific mechanism must be supported by separately reproducible device evidence.

**Do not** reset the phone, grant privileged permissions, flash firmware, alter system_server, or remove existing Google/Huawei packages as part of this diagnostic protocol.
