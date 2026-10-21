# Huawei P Smart S: rootless Play Store investigation

## Result and evidence boundaries

**Play Store downloads/installations are not demonstrated to work.** The current
evidence establishes an account/token path, a rejected signature check and the
absence of newly observed installer sessions. It does not identify the first
failing branch inside proprietary Play Store 19.5.13. No change in this PR makes
firmware spoofing work or grants privileged installation rights.

Target: AQM-L21A / AQM-LX1, Android 10 (API 29),
EMUI 12.0.0.226(C432E5R5P3), locked bootloader; no root or downgrade.
User-reported software: microG-HW `0.3.17.252432-hw`, Google Play Store `19.5.13`,
GSF `v0.1.0`. Device findings below were supplied by the owner; this investigation
has no attached Huawei handset, original Play Store APK or original device logs.
They are not CI observations or independently reproduced phone tests.

This work extends PR #4, already merged into `master` at `238ff245`, rather than
replacing its diagnostics or merging other experimental branches. The follow-up
branch is `psmarts-playstore-diagnostics`. Other remote branches are preserved.

## Evidence table: what is fixable?

| Evidence | Supported conclusion | APK-only fix / next evidence | Status |
| --- | --- | --- | --- |
| Account visible; catalog/detail page opens; `GmsAuthenticator` returns OAuth token | At least some account/auth/catalog paths work | Inspect delivery and registration independently; do not fabricate new tokens | Owner-reported; not an install test |
| `com.android.vending requires Google Play services, but their signature is invalid.` | At least one client-side availability check rejects the GMS signature it sees | Need caller-context PackageManager results and failing install call path; changing microG's own success constant cannot change embedded client code | Warning established by owner; download causality **unknown** |
| Huawei SignatureService/InitProvider/InitReceiver declared | Huawei-flavor APK contains the integration endpoint | App startup diagnostics can improve observability; no endpoint can install an absent firmware consumer | Presence reported; firmware consumption **unknown** |
| Service Binder rejects calling UID `> 10000` | Ordinary app UIDs cannot directly use this Binder as a spoofing service | Preserve the boundary; do not remove the UID check to simulate support | Source-confirmed; not a firmware capability test |
| Vending UID `10136`, `/data/app`, `INSTALL_PACKAGES` denied | Current package lacks that privileged installation permission | A data path alone does not classify updated system apps; inspect system/private flags too | Owner-reported; permission is not normally grantable to an ordinary user app |
| `REQUEST_INSTALL_PACKAGES` absent | Manifest prerequisite for standard modern untrusted-source install UI is absent for target SDK >=26 | Adding a declaration alone does not implement download/confirmation handling and requires re-signing a modified APK | Owner-reported; actual target SDK must be collected |
| Old sessions have other UIDs | Old installer identity must not be attributed to UID 10136 | Collector now separates active/history, Android user and current/old UID | **Fixed diagnostic ambiguity**, offline-tested |
| No newly observed session; Download Manager/Package Installer enabled | No new visible session in that capture; components exist | Does not prove absence of delivery traffic, private download implementation or short-lived sessions | Root cause **unknown** |
| Failed/unsupported ADB queries used to resemble absent data | False negative diagnoses were possible | Keep missing reads/fields unknown; expose collection status | **Fixed**, offline-tested |
| Auth/form/Check-in sources log raw bodies, token/account/Bundle or IDs | Sensitive values can enter logcat even when auth succeeds | Static event messages; no token/account/body/exception text at these sites | **Fixed separately**, JVM regression-tested; no auth semantics changed |
| APK signer digest differs from Google identity | Real APK signer and package identity can differ from contextual firmware presentation | APK verification and PM-visible signatures must be reported separately | Optional APK verification implemented; not a spoofing test |

## A. Huawei differentiated signatures

### Initialization and Binder contract

Reviewed sources (paths are relative to this repository):

- [`fake-signature/build.gradle`](../fake-signature/build.gradle): `huawei` is
  a **target flavor**, independent of map-engine flavor.
- [`fake-signature/src/huawei/AndroidManifest.xml`](../fake-signature/src/huawei/AndroidManifest.xml):
  non-exported `InitProvider` (`initOrder=1000`), enabled/exported `InitReceiver`
  for boot/package events, exported service action `com.huawei.signature.diff`
  running in `:signature`.
- [`InitProvider.java`](../fake-signature/src/huawei/java/com/huawei/signature/diff/InitProvider.java):
  provider creation checks running services, then tries `startService`.
- [`InitReceiver.java`](../fake-signature/src/huawei/java/com/huawei/signature/diff/InitReceiver.java):
  reception also tries `startService`. Both ignore startup exceptions. Provider
  declaration does not mean the app process/provider has actually started;
  receiver declaration does not mean delivery occurred. Android background
  execution restrictions are a possible failure, not a reproduced cause here.
- [`SignatureService.java`](../fake-signature/src/huawei/java/com/huawei/signature/diff/SignatureService.java):
  opens local policy database, returns `START_STICKY`, and exposes
  `ISignatureService.querySignature(String packageName, boolean suggested)`.
  `onTransact` rejects UIDs **greater than** 10000 (not `>=10000`). This numerical
  guard includes system/shell identities below the boundary; it is not proof
  that any specific framework process has called the service. Ordinary Play
  Store UID 10136 is above the boundary. We do not change or bypass it.
- [`AppListDatabaseOpenHelper.java`](../fake-signature/src/huawei/java/com/huawei/signature/diff/AppListDatabaseOpenHelper.java)
  and [`arrays.xml`](../fake-signature/src/main/res/values/arrays.xml): per-name
  fake/real policy, seeded on writable opens, with suggestion used for unknown
  names. `com.android.vending` and `com.google.android.gms` are already in the
  fake list. Adding them again cannot supply a missing firmware caller. The
  firmware's interpretation of `packageName` is not established by this server
  implementation alone.

The AIDL cites a [Huawei developer discussion](https://forums.developer.huawei.com/forumPortal/en/topic/0202128603315033024).
That citation is not evidence that this C432 firmware has the extension. No
firmware implementation or device-side call trace was available to audit.

### What detects firmware support?

These initialization classes contain **no positive firmware-support detection**.
The existing [`RomSpoofSignatureChecks`](../play-services-core/src/main/java/org/microg/tools/selfcheck/RomSpoofSignatureChecks.java)
checks the fake-signature permission and compares Google's expected digest with
both legacy and SigningInfo results for GMS. [`PackageUtils`](../play-services-base/core/src/main/java/org/microg/gms/common/PackageUtils.java)
uses `GET_SIGNATURES` and, on API >=28, `GET_SIGNING_CERTIFICATES`/signing history.
This is a meaningful self-check for the context that performs it, not proof that
Vending sees the same result under caller-dependent Huawei policy.

Android 10's [PackageManagerService](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/services/core/java/com/android/server/pm/PackageManagerService.java)
and package/signing metadata live outside an ordinary APK. A firmware extension
must actually consume the Huawei Binder/metadata and change the relevant query
results. An APK can declare metadata or return a requested certificate but
cannot by itself replace system PackageManager policy on a locked stock phone.
The `fake-signature`/`other-signature` metadata in the base manifest is a contract
for a cooperating implementation, not an Android permission grant or a new
cryptographic signature on the APK.

The collector therefore reports `huawei_firmware_integration=unknown` and
`caller_visible_gms_signature=unknown` even if every component is present and a
service appears running. A true claim requires successful system consumption
and the relevant caller's legacy/SigningInfo/possibly checkSignatures behavior.
A non-Vending probe can narrow this but cannot stand in for Vending's identity.
`dumpsys package` signature hashes are not a measurement of all contextual PM
responses. APK signer SHA-256 from `apksigner` measures actual file certificates.

### Why changing microG's signature utility is not a demonstrated fix

This repository's [`GooglePlayServicesUtil.isGooglePlayServicesAvailable`](../play-services-base/src/main/java/com/google/android/gms/common/GooglePlayServicesUtil.java)
already logs that it pretends availability and returns success. It does not
contain the reported signature-invalid message. A consumer APK can contain a
separately compiled Google client library; replacing microG's implementation
does not replace code inside Play Store. The message and caller attribution
suggest that route but do not reveal its stack, return-value consumer, or
whether that branch gates the install button's delivery operation. No binary
patch or unconditional “signature valid” response is introduced here.

## B. Auth, Check-in, delivery and installation are separate stages

The useful model is: account authentication → device registration/configuration
and service calls → catalog/delivery policy → APK transfer → session creation /
write → commit/user action → package installed. Clients can order or overlap
these stages; a visible detail page is not proof of the later stages.

| Source | What it supplies | Why success is insufficient |
| --- | --- | --- |
| [`AccountAuthenticator.getAuthToken`](../play-services-core/src/main/java/org/microg/gms/auth/loginservice/AccountAuthenticator.java) | Validates requesting package/UID/PID, calls AuthManager and returns AccountManager token or consent/reauth intent | A token may be cached; it is not proof of successful delivery RPC or install permission |
| [`AuthManager.requestAuth`](../play-services-core/src/main/java/org/microg/gms/auth/AuthManager.java) / [`AuthRequest`](../play-services-base/core/src/main/java/org/microg/gms/auth/AuthRequest.java) | Package/signature identity, account/service/options and Check-in-derived information in Google auth requests | Different API scopes and package signing identity may be checked independently |
| [`CheckinManager`](../play-services-core/src/main/java/org/microg/gms/checkin/CheckinManager.java) | Honors enabled setting and three-hour throttling; builds registration request; stores server Android ID, security token, timestamps, digest/config data | Disabled/stale/missing registration cannot be ruled out by GSF package presence or successful OAuth |
| [`CheckinClient`](../play-services-core/src/main/java/org/microg/gms/checkin/CheckinClient.java) / [`DeviceConfiguration`](../play-services-base/core/src/main/java/org/microg/gms/common/DeviceConfiguration.java) | POST to `android.clients.google.com/checkin`, build/device/locale/features/ABI/config and account cookies | Device configuration, server compatibility/certification and delivery eligibility still need device-specific evidence |
| [`CheckinService`](../play-services-core/src/main/java/org/microg/gms/checkin/CheckinService.java) | Binder returns last success time and device-data-version info | Existing provider/package declaration does not prove these values are current/nonempty |
| [`GServicesProvider`](../play-services-core/src/main/java/org/microg/gms/gservices/GServicesProvider.java) | Stores/serves selected registration settings; Android ID reads have caller checks | Rejection for shell/another app is not proof Vending receives no ID; directly querying IDs risks exposing identifiers |

The read-only collector does not query private databases, AccountManager token
stores, Google IDs or the Huawei Binder. Device Registration UI/self-check state
can be transcribed manually as booleans/age without sharing identifiers. No
fabricated ID, security token, certification claim or forced Check-in is added.

### Causality experiment, not a claim of success

Use [the collector](../tools/psmarts-diagnostics/README.md) before/after one manual
attempt on the same phone/user. Record the visible Play Store error, selected app
and approximate delay separately, without account data. Keep current UID, flags,
permission/AppOp state and active/history session ownership together. A positive
new session for current Vending UID proves a later installer interaction became
visible; it still does not prove downloaded bytes or successful installation.
No new visible ID does **not** prove no download: sessions can be short-lived,
OEM visibility can differ, or Play Store can fail before session creation.

Counters use overlapping rolling buffers. They cannot locate a warning in the
same installation call stack or establish a controlled signature-only A/B test.
The following remain distinct hypotheses: client availability gate; stale device
registration; server delivery/certification/account restrictions; network/space/
policy rejection; nonprivileged install route unsupported by this old Vending
binary. Advertising-ID failure is not established as the download blocker.

To **prove** the signature warning gates this install attempt, obtain a
privacy-reviewed causal trace/stack from the actual Vending flow or a controlled
comparison changing only that effective signature check while holding account,
registration, target app and install privileges constant. The original binary,
firmware consumer and device are unavailable here, so that experiment was not
performed. Patching out a check and observing another failure would not prove
full compatibility. We do not present a guessed root cause as proof.

### Android 10 permissions and sessions: primary-source check

Sources were read at AOSP tag `android-10.0.0_r47`; Huawei may modify behavior.

1. [Framework permissions](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/res/AndroidManifest.xml#L3346):
   `INSTALL_PACKAGES` is `signature|privileged`; `REQUEST_INSTALL_PACKAGES` is
   `signature|appop`. The latter participates in the public unknown-source
   approval flow, not unrestricted silent-install authority. Ordinary `pm grant`
   cannot turn a user-installed APK into a platform-signed/privileged installer.
2. [PackageInstallerService.createSessionInternal](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/services/core/java/com/android/server/pm/PackageInstallerService.java#L471):
   ordinary callers can create normal non-staged sessions subject to checks;
   without `INSTALL_PACKAGES` the installer package must match the calling UID.
   Staged/APEX sessions and special flags have extra requirements. Hence denial
   of `INSTALL_PACKAGES` alone does not explain an absence of **any** new session.
3. [PackageInstallerSession.needToAskForPermissionsLocked](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/services/core/java/com/android/server/pm/PackageInstallerSession.java#L382):
   commit can require user action. Besides install/update/self-update permissions,
   system/root and device-owner/affiliated-profile-owner cases are considered;
   this report does not assume those exceptions apply to this phone.
4. [PackageInstaller.STATUS_PENDING_USER_ACTION](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/java/android/content/pm/PackageInstaller.java#L247):
   the installer must handle the returned confirmation intent. Session progress,
   sealed/committed flags, and historic status are not installed-package proof.
5. [InstallStart](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/packages/PackageInstaller/src/com/android/packageinstaller/InstallStart.java#L80)
   checks `REQUEST_INSTALL_PACKAGES` declaration for originating target SDK >=26.
   [PackageManagerService.canRequestPackageInstalls](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/services/core/java/com/android/server/pm/PackageManagerService.java#L25365)
   also checks declaration, target SDK and user restrictions/trust policy. A
   missing AppOp record is not equivalent to granted permission or approval.

An enabled Package Installer provides a system UI, not authority for Vending to
skip it. A separate user-approved installer can implement the public flow for
legitimately obtained APKs; that does not supply Play delivery or satisfy
Vending's internal assumptions. APK/split handling, integrity checks, explicit
user approval and update-signer continuity still matter.

A manifest-modified Vending APK needs re-signing without Google's signing key.
That changes identity, may block updating the installed official Vending APK,
and can break auth/API/anti-tamper expectations. Adding only a permission is not
a supported repair of the closed-source client. No proprietary Play Store APK,
patched binary, automatic uninstall or certificate-verification bypass is shipped.
Our debug-signed GmsCore build similarly cannot be assumed to update the owner's
release-signed microG-HW installation. Do not uninstall it to make a test build
install. Keeping the existing release signing key/identity is a separate prerequisite.

## C. Changes, validation and remaining unknowns

- Diagnostic fixes: missing/unsupported data stays unknown; active/history/user/
  UID separation; updated-system-app flags; selectable authorized device/user;
  optional temporary APK verification; exclusive private report creation;
  exception/log payload suppression and strict comparison field allowlist.
- Separate production fix: static event logging in AccountAuthenticator,
  AuthManager, form transport/parser and Check-in. This removes observed token,
  account, Bundle, registration body/ID and parse-exception logging at those sites
  without changing auth requests, token results, firmware policy or install rights.
  It is not a repository-wide audit of every possible logging path.
- Local regression suite: **22 tests passed** (20 diagnostic tests plus a JVM
  production-form roundtrip/privacy test and a sensitive-log-site regression
  guard). The roundtrip tests known/unknown fields and invalid numeric values
  with synthetic secrets, verifies the request still contains the credential,
  and verifies the parsed token is unchanged while logs contain neither.
- GitHub Actions workflow: Python 3.10/3.12 with Java 17; `VtmHuaweiDebug` APK
  assembly, signature/manifest-component verification, and existing five
  `CryptoTest` cases. Use PR checks for the actual run result and tested SHA;
  a workflow definition alone is not a passing build. `VtmHuaweiDebug` exercises
  Huawei **target/signature code** with VTM maps; it is not an HMS-map firmware
  compatibility test or a release-signing artifact.
- No CI step connects to Google accounts, captures device logs, publishes APKs,
  installs/uninstalls phone apps, grants privileges or changes firmware.
- Unknown: exact firmware consumer/support, Vending caller-visible signatures,
  active registration values, delivery request/response, first failing Vending
  branch, successful transfer and successful phone installation. An attached
  authorized handset and user-mediated reproduction are required to resolve them.

The reproducible result here is validated diagnostic tooling and safer logging.
A working rootless Play Store remains unproven. Firmware support is **not**
reported absent solely from component presence or a warning, and it is **not**
reported working solely because the APK builds or OAuth succeeds.
