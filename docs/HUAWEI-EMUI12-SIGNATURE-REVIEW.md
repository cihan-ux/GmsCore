# Huawei P Smart S: signature-chain source audit (read-only)

Scope: AQM-L21A/AQM-LX1, Android 10/API 29, EMUI 12.0.0.226(C432E5R5P3),
rootless, locked bootloader. This is **source-level reasoning**, not proof that
Huawei's modified `PackageManagerService` on this handset is identical to AOSP.

## Chain traced from checked-in GmsCore source

1. `fake-signature/src/huawei/AndroidManifest.xml` declares
   `InitProvider`, `InitReceiver`, and the exported `SignatureService`.
2. `InitProvider.onCreate` can call `startService` directly; `InitReceiver`
   does the same after selected broadcasts. Neither requires a firmware-side
   `querySignature` request. Both suppress startup exceptions.
3. `SignatureService.onCreate` opens `app_list.db`; its `onStartCommand`
   writes `SignatureService: onStartCommand` to logcat. Running/started
   service is therefore **not evidence of signature spoofing**.
4. The `ISignatureService.aidl` API exposes
   `String[] querySignature(String packageName, boolean suggested)`.
   `SignatureService.onTransact` rejects callers with UID > 10000. A normal
   user app (or ADB shell UID 2000? **2000 is allowed by this guard**,
   but still not a reliable Play Store caller or Huawei framework client)
   must not be used to claim successful firmware integration.
5. The service reads `app_list.db` and returns its `fake_signature` when
   configured; its default table is populated from
   `fake-signature/src/main/res/values/arrays.xml`. Both
   `com.google.android.gms` and `com.android.vending` are **already listed**.
6. In unmodified AOSP Android 10, `PackageManagerService.checkSignatures`
   compares package signing details stored by system PackageManager. A
   Huawei-specific bridge would need to be present in the firmware path
   relevant to Google Play Store's actual signature verification.

### Caution: UID ranges

`Binder.getCallingUid() > 10000` is the actual source condition. This
permits root/system/shell UID values below 10001 in principle, not just UID
1000. Nevertheless, calling the signature service as `adb shell` is not a
test of Huawei framework integration or the signature seen by the Play Store.

## Observed on this device (October 2026 manual trial)

| Evidence | Observation | What it proves |
| --- | --- | --- |
| Package component declaration | All three Huawei classes found | microG-HW APK has the service API |
| Activity service dump | Running hint true | A service record was observed |
| Play Store GooglePlayServicesUtil | Signature invalid warnings | At least one GMS signature validation path fails |
| Vending UID / permissions | 10136, data app, nonprivileged; INSTALL_PACKAGES denied | This Play Store lacks privileged package-install permission |
| Installer session comparison | No new current-UID session | No new session visible in two snapshots |
| GSF / delivery eligibility | Unknown | Account/catalog visibility alone is insufficient |
| Firmware `querySignature` invocation | Not observed | Remains unknown; a running service is insufficient |

The above warning counts and installer evidence do not prove a causal
relationship. The failure may also occur during device registration,
delivery entitlement/network negotiation, or the installation stage.

## Additional source-informed check: installed APK signer

microG/GmsCore issue #3500 (reported on a **different device**) states that
some Huawei releases recognize official-signed `-hw` packages but may not
recognize locally rebuilt/re-signed APKs. This claim is conditional and
**must not be generalized to P Smart S** without firmware evidence.

Use the existing opt-in `diagnose.py snapshot --inspect-apks` feature with a
trusted Android SDK `apksigner` to retain the **signer certificate SHA-1 and
SHA-256** digest for selected APKs. The verifier also computes the SHA-256
of APK bytes, does not capture certificate subjects, and removes pulled
temporary files after normal completion. A signer match can show a build is
signed with the same certificate, but not whether Huawei firmware recognizes
the package or what `com.android.vending` sees via PackageManager.

Do not attempt Binder transactions, permission grants, downgrades or any
signature patch based on this report. Especially do not infer that adjusting
the `signature_want_fake` array or `SignatureService` code can change
firmware behavior on EMUI 12.

## Primary references

- GmsCore source: `fake-signature/src/huawei/java/com/huawei/signature/diff/`
- GmsCore source: `fake-signature/src/huawei/aidl/com/huawei/signature/diff/ISignatureService.aidl`
- GmsCore source: `fake-signature/src/main/res/values/arrays.xml`
- AOSP Android 10: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/pm/PackageManagerService.java
- microG spoofing wiki: https://github.com/microg/GmsCore/wiki/Signature-Spoofing
- microG Huawei developer discussion: https://github.com/microg/GmsCore/issues/2141
- Huawei release signer anecdote: https://github.com/microg/GmsCore/issues/3500
- Existing investigation: `docs/PLAYSTORE-ROOTLESS-ANALYSIS.md`
