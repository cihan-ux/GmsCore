# Experimental Huawei microG account-visibility fix (v1)

## Scope
This branch is based on the `cihan-ux/GmsCore` master branch and is **separate** from
the existing FakeGApps signature-diagnostics PR #8. It changes only microG's existing-account
visibility update on Android 8+ when the `Auth.VISIBLE` account-finding preference is changed.

microG currently sets `PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE` during the account login
process, but not when the setting is enabled later. Accounts created before enabling
the setting may remain invisible to applications that enumerate `com.google` accounts.

Implementation:
- For the opt-in `Auth.VISIBLE` toggle, also set explicit per-package account visibility
  for the stable Android Chrome package (`com.android.chrome`). Modern Chrome may not
  rely on the pre-Android-O legacy visibility key. This is experimental and limited to
  Chrome, not a grant to all installed applications.
- Setting enabled: set `VISIBILITY_USER_MANAGED_VISIBLE` for pre-existing `com.google` accounts.
- Setting disabled: restore `VISIBILITY_UNDEFINED` (the platform default) for the legacy and Chrome keys.
- Skip Android versions before API 26.
- Log AccountManager errors without logging account names or credentials.

Related upstream report: https://github.com/microg/GmsCore/issues/3805

## Safety and limitations
- This does **not** add signature spoofing to EMUI 12.
- This does **not** guarantee Chrome login, Gmail/Drive sign-in, or synchronization.
- The test APK is **not signed with the user's existing microG key** and may be impossible
  to install over the currently installed microG package.
- Do **not** uninstall the working microG installation on the Huawei merely to test this APK.
  Removing it may disrupt the Google account and working notification registrations.
- No changes to the phone, firmware, bootloader, kernel or partition contents were made.
- The build job is compilation-only, not on-device verification.

## Regression-test plan
1. Use a spare test environment with compatible microG signing, not the user's main Huawei.
2. Add a `com.google` account while **Allow apps to find accounts** is disabled.
3. Confirm a test app relying on `getAccountsByType("com.google")` cannot enumerate it.
4. Enable **Allow apps to find accounts** after account creation.
   Check Android 8+ account visibility separately for the legacy key and `com.android.chrome`.
   Do not log email addresses or access tokens.
5. Confirm the test app can now enumerate the account without removing/re-adding it.
6. Disable the setting. Confirm visibility returns to the platform default.
7. Repeat on Android 8+ and verify Android 7 or older does not call the new API.
8. Compare separately with Chrome account picker and microG Self-Check on the test device.
9. Confirm that if Chrome is not installed, the setting still updates the legacy key and does not crash.
10. Confirm that disabling the toggle resets the Chrome-specific visibility override.

## Build
GitHub Actions workflow:
`.github/workflows/huawei-account-visibility-build.yml`

Gradle task:
`./gradlew :play-services-core:assembleMapboxHuaweiDebug`

The workflow attempts to upload a debug APK under the artifact name
`microg-huawei-account-visibility-debug` after successful compilation.

## 2026-11-09: build and signing audit

The `mapboxHuaweiDebug` APK was successfully built in GitHub Actions
run 42615774413, and the variant was confirmed in its build log.
Run 42616836998 produced a ZIP with a manifest containing the application ID
and APK SHA-256. These prove packaging, **not** that Google Chrome login works.

**The independent read-only signature diagnostics APK already exists in PR #8**:
https://github.com/cihan-ux/GmsCore/pull/8
Its own successful workflow is:
https://github.com/cihan-ux/GmsCore/actions/runs/42434974136
Download the `fakegapps-p-smart-s-experimental-apks` artifact and use **only**
`diagnostics/build/outputs/apk/debug/diagnostics-debug.apk` for this audit.
Do not install `module-debug.apk` or `probe-debug.apk` merely to do the audit.

### Safe order for a device (root/bootloader unlock NOT required)
1. **Do not uninstall or clear data for the installed microG**. Keep the account
   and current notifications intact.
2. Optionally install the separate `dev.cihan.fakegapps.diagnostics` read-only
   diagnostics APK from PR #8. It uses its *own* application ID, and does not replace
   `com.google.android.gms`. Inspect its source before installation.
3. Read `GET_SIGNING_CERTIFICATES` for installed
   `com.google.android.gms` and record the SHA-256 certificate fingerprint.
   This fingerprint is **not** the APK file's SHA-256; they are different values.
4. Compare it with `signerCertificateSha256` in the *same build's* experimental
   ZIP `build-manifest.json`. Do not compare to the fingerprint from another run:
   ephemeral CI debug keys can change from run to run.
5. If signing certificates do not match, an Android package update over the
   existing microG is refused. **Stop here; do not uninstall or bypass this.**
6. If they match, the APK still has not been verified on AQM-LX1; the Chrome
   account picker and signature spoofing require separate device-level tests.

### Interpretation
- A signature mismatch is a **package update blocker**, not evidence that the
  Android Framework's signature spoofing works or does not work.
- The PR #8 diagnostic APK measures package signatures and PackageManager API
  behavior. It does **not** read account names, emails or Google tokens, and
  it does **not** directly measure `AccountManager`'s per-app visibility for Chrome.
- The v1 patch in PR #11 updates `AccountManager` visibility but does **not**
  implement privileged EMUI framework signature spoofing.
- Do not claim Chrome login is fixed until observed on a test device.

## 2026-11-12: Huawei AQM-LX1 Account Probe v0.3 observations

User-supplied read-only results (no account identifiers recorded):

- Device: Huawei AQM-LX1, Android API 29 (EMUI 12).
- Installed GMS package: `com.google.android.gms` v0.3.17.252432-hw, enabled.
- Stable Chrome: `com.android.chrome` v157.0.8103.50, enabled.
- Play Store package `com.android.vending` present and enabled.
- Google authenticator: `com.google.android.gms`.
- One Google account visible **to the independent diagnostic package**
  `dev.cihan.microg.accountprobe`, with its own `GET_ACCOUNTS` permission granted.
- Chrome `GET_ACCOUNTS`: declared but not granted; `GET_ACCOUNTS_PRIVILEGED`:
  not requested.
- HTTPS handler inventory from this independent probe listed
  `com.brave.browser`; that does **not** mean Chrome was absent, because
  a separate PackageManager query confirmed Chrome installed and enabled.
- Tapping the Chrome profile icon displayed Chrome's settings and Google
  services controls. **No Google account chooser appeared** in the user's
  screenshots. Chrome setting to permit sign-in was enabled.

### Interpretation and boundaries

The denied `GET_ACCOUNTS` permission is a **diagnostic observation, not a
confirmed root cause**. Android's documented `AccountManager.getAccountsByType`
behavior for apps targeting Android O (API 26) or newer does not use this
permission to determine which accounts are returned; per-app visibility set
by the authenticator or user authorization governs the result.
Source: https://developer.android.com/reference/android/accounts/AccountManager

- Probe visibility of one account **does not** establish visibility to Chrome.
- Independent apps cannot call `getAccountVisibility(account, "com.android.chrome")`
  or `getAccountsAndVisibilityForPackage` as the real `com.google`
  authenticator; these APIs require signature matching with the authenticator.
- The patch in this PR supplies an *experimental* Chrome-specific
  `setAccountVisibility` update from microG settings, but the test build
  is signed with a CI debug key. It is not a verified in-place replacement
  for the existing microG installation.
- An app-visible account, Chrome account picker, token acquisition, and
  framework signature spoofing are distinct test targets.
- Do not suggest forcing `pm grant` or uninstalling/reinstalling microG
  solely on the basis of Chrome's denied `GET_ACCOUNTS` permission.

### Next safe investigation

1. Record (without email or tokens) whether microG's own **Allow apps to
   find accounts** setting is enabled on the unchanged installation.
2. If available, use a *redacted* Android log around Chrome's attempted
   sign-in to differentiate "no attempt made" from `AccountManager` or
   `PackageManager` access-denied events. Do not share raw logcat dumps
   containing URLs, emails, cookies, or auth tokens.
3. With a disposable test ROM/environment where the microG signing key is
   controlled, run the Chrome-specific visibility patch and compare
   permission-independent account visibility and Chrome's sign-in behavior.
4. Independently confirm signature spoofing in microG Self-Check. A failed
   spoofing check is not fixed by the AccountManager patch alone.

### Do not overstate

**Chrome login remains unverified and unresolved on the reported Huawei.**
The goal of the next test is to identify which component is failing without
touching the user's functional microG installation.

## 2026-11-12: discovery already enabled — automatic reconciliation prototype

User confirmed the installed microG **Allow apps to find accounts** option
is already enabled on Huawei AQM-LX1. Therefore asking them to toggle it
again is not useful and does not verify whether Chrome's per-package account
visibility was previously applied.

**Source change in this branch:** Upon first opening microG Accounts settings,
the experimental build reads the existing preference. If it is enabled,
`updateExistingAccountVisibility(true)` reconciles the legacy visibility
key and Chrome's explicit package visibility for existing Google accounts.
The switch listener still handles later user-initiated changes. If the
preference is disabled, no automatic visibility grant is performed.

This is an experimental implementation in the source branch, **not a change
to the installed Huawei phone**. The CI debug build is signed differently
from the user's functional microG and must not replace it by uninstalling
the working package. The patch does not change Chrome's sign-in UI, guarantee
account picking or tokens, or add EMUI system signature spoofing.

### Validation to perform on a device with matched signing credentials

1. Add a `com.google` account when discovery is disabled; enable it later,
   without removing/re-adding the account, as the primary migration scenario.
2. Start the Accounts settings of the patched microG with discovery already
   enabled and verify both relevant `AccountManager` visibility keys.
3. Close and reopen Accounts settings and verify operations are idempotent.
4. Confirm no Chrome-specific override is added if discovery is disabled.
5. Confirm the switch-off path reverts the explicit overrides.
6. Finally test actual Chrome account picking and OAuth separately.

This was added in response to an already-enabled setting. Do not conclude
that this setting caused the Chrome sign-in problem without on-device evidence.

## 2026-11-12: account creation and login visibility consistency

Follow-up source inspection identified that `LoginActivity.java` applied only the
legacy AccountManager visibility override after a Google account was created
or a GMS token was successfully retrieved. This could leave the explicit
`com.android.chrome` visibility unset until the Accounts settings screen was
opened. (It does not prove the Chrome sign-in issue is caused by visibility.)

The experimental branch now calls a common local helper at both successful
login paths. If **Allow apps to find accounts** is already enabled, it attempts
to set both `PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE` and explicit visibility for
`com.android.chrome`. With the toggle disabled it does nothing, and with
Android below API 26 it does nothing. A failed OEM AccountManager operation
is logged without account identifiers and is not allowed to abort login.

The previously added `AccountsFragment.kt` migration path still reconciles
accounts created before this experiment whenever Accounts settings is opened
with the toggle already enabled. No account removal or reinstall is needed
in a controlled signing-compatible test environment.

### Additional regression tests before any release

- Account created through master-token EXTRA_EMAIL/EXTRA_TOKEN route.
- Account created through standard interactive Google login route.
- Both login paths with discovery setting on and off.
- Re-authentication of an existing Google account.
- AccountManager reporting missing Chrome package or rejecting a visibility write.
- Verify no account names or authentication tokens appear in newly added logs.
- Confirm Chrome account-picker behavior independently; visibility alone
  does not establish OAuth or framework signature spoofing support.

**Status:** Source-only, test build must still pass CI. Experimental APK is
not an in-place update for the existing microG on the Huawei.

## 2026-11-12: read-only signing comparison on Huawei AQM-LX1

The user tested the separate v0.4 `dev.cihan.fakegapps.diagnostics` APK
without patching any app. The Android API 29 package manager returned:

| Check | microG GMS (`com.google.android.gms`) | microG Companion (`com.android.vending`) |
| --- | --- | --- |
| GET_SIGNATURES cert SHA-256 | `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165` | same |
| GET_SIGNING_CERTIFICATES cert SHA-256 | same | same |
| On-disk APK archive certificate | same | same |

`PackageManager.checkSignatures(GMS, Play)` returned `MATCH (0)`.
`hasSigningCertificate(Play, Play cert)` and
`hasSigningCertificate(GMS, Play cert)` both returned true.

**Key interpretation:** The fingerprint matches documented **microG Team's
real APK signing certificate** for microG Services and Companion, not a
demonstration that Android is spoofing Google's proprietary signer.
Reference examples:
- https://www.apkmirror.com/apk/microg-team/microg-services-core/microg-services-0-3-15-250932-release/microg-services-0-3-15-250932-android-apk-download/
- https://www.apkmirror.com/apk/microg-team/fakestore/fakestore-0-3-1-40226-release/microg-companion-0-3-1-40226-android-apk-download/

Because the package manager and on-disk archive agree, this specific test
did not observe signature substitution for these packages. The result does
not comprehensively disprove every possible spoofing mechanism (other APIs,
callers, process hooks, or Google-specific signature checks could differ).

**Chrome's actual installed certificate was not included in v0.4.**
A separate v0.5 standalone read-only signature diagnostic has been authored
on `distribution/p-smart-s-signature-diagnostics-v05` to compare Chrome,
GMS and Companion fingerprints using the same PackageManager APIs.

Chrome's signature mismatch, even if confirmed, is evidence of different
signing identities, **not by itself proof** that Chrome's authentication
fails because of this difference. Chrome sign-in still needs device testing.

Do not install experimental CI-signed microG over the user's existing
official microG signature; do not remove the existing microG account or
clear Chrome data for this test.

## 2026-11-13: Huawei AQM-LX1 v0.5 Chrome-signature observations

The user ran a separate, unpatched, **read-only** diagnostic:
`dev.cihan.fakegapps.diagnostics` v0.5 on Android API 29.
The following are **certificate** SHA-256 fingerprints, not APK byte hashes.

| Package | SHA-256 signing certificate |
| --- | --- |
| microG GMS `com.google.android.gms` | `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165` |
| Companion/Store `com.android.vending` | `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165` |
| Chrome `com.android.chrome` | `f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83` |

For **each** package, `GET_SIGNATURES`, `GET_SIGNING_CERTIFICATES`
and APK-archive certificate agreed.

- `PackageManager.checkSignatures(GMS, Play)` → `MATCH (0)`.
- `PackageManager.checkSignatures(GMS, Chrome)` → `NO_MATCH (-3)`.
- `PackageManager.checkSignatures(Play, Chrome)` → `NO_MATCH (-3)`.
- `hasSigningCertificate(Play, Play cert)` → true.
- `hasSigningCertificate(GMS, Play cert)` → true.
- `hasSigningCertificate(Chrome, Chrome cert)` → true.
- `hasSigningCertificate(GMS, Chrome cert)` → false.
- `hasSigningCertificate(Play, Chrome cert)` → false.

The Chrome SHA-256 matches the publicly listed **Google Chrome/Google Inc**
signing certificate; e.g. APKMirror's Chrome 157.0.8103.14 listing:
https://www.apkmirror.com/apk/google-inc/chrome/google-chrome-157-0-8103-14-release/
The microG GMS and Companion fingerprint is the legitimate official microG
certificate as previously recorded. Thus *no unexpected APK signing anomaly
was detected in these three applications*.

### Correct interpretation and next tests

**Do NOT infer that Chrome and Play Services must have the same app signing
certificate.** `checkSignatures(GMS, Chrome) = NO_MATCH` is a direct APK
signing-identity comparison, **not a signature-spoofing test** and **not a
diagnosis of why Chrome sign-in is unavailable**.

Framework signature spoofing is a distinct capability: it can cause callers
to see Google's expected GMS package identity without re-signing Chrome or
microG. Nothing in v0.5 confirms or excludes every possible spoofing path.

Official microG signature-spoofing documentation:
https://github.com/microg/GmsCore/wiki/Signature-Spoofing
It cautions that **only microG Services v0.3.36+** can accurately assess
modern spoofing in its Self-Check. This user's installed `0.3.17.252432-hw`
predates that version. Older Self-Check results may be collected as a clue,
but must be marked **not definitive**.

Android `AccountManager` docs:
https://developer.android.com/reference/android/accounts/AccountManager
For clients targeting API 26+, `GET_ACCOUNTS` grant alone does not determine
per-app account visibility. The independent Account Probe seeing an account
only proves it is visible to the probe, not to Chrome.

Follow-up work: investigate Chrome-specific account visibility *inside the
authenticator with proper signing privileges*, distinguish Chrome account
picker from OAuth/token path, and test a signing-compatible microG prototype
on a disposable environment. Do not change account visibility forcibly,
replace the running microG with a CI debug APK, or attempt to re-sign Chrome.


## 2026-11-13: positive control — Samsung A7 LineageOS Chrome login works

**New first-hand user evidence:** Samsung Galaxy A7 (2018) running
LineageOS 18.1 / Android 11 **can sign into Chrome using an APK-installed
microG**. On Huawei P Smart S AQM-LX1 / EMUI 12 / API 29, a Google account
exists in microG but Chrome 157.0.8103.50 exposes no sign-in account picker.
Therefore APK installation alone is not a sufficient explanation of failure.

| Observation | A7 / LineageOS | Huawei / EMUI 12 |
| --- | --- | --- |
| Chrome sign-in through APK-installed microG | **WORKS (user confirmed)** | Fails to show picker |
| Android | Android 11 / LineageOS 18.1 | Android 10 / EMUI 12 |
| microG version | **Not yet recorded** | 0.3.17.252432-hw |
| Chrome version | **Not yet recorded** | 157.0.8103.50 |
| microG discoverable Google account in independent probe | **Not yet tested** | One visible to probe |
| Chrome GET_ACCOUNTS | **Not yet tested** | Not granted (not definitive on API26+) |
| Signature spoofing self-check, version-aware | **Not yet tested** | Not yet tested |

Official microG guidance says **LineageOS builds from 2024-02-26** implement
microG-restricted spoofing; whether this custom/older 18.1 build supports
spoofing is *unknown*, and success in Chrome is not itself proof.
https://github.com/microg/GmsCore/wiki/Signature-Spoofing
Modern verification in microG's own Self-Check requires v0.3.36+;
the installed Huawei microG is older. A third-party probe's
`FAKE_PACKAGE_SIGNATURE` permission check is not a reliable success/failure
test, particularly when spoofing is tied to officially signed microG.

### Revised next step

Run the SAME read-only Account Probe v0.4 on both Samsung and Huawei.
It reports Android version, microG/Chrome/Play/GSF package versions,
signing-certificate fingerprints, locally visible Google account count,
Chrome permission flags and historical signature-spoof permission clues.
**Only the independent probe's own account visibility is measured.**

The APK is published from a separate public distribution branch, not as
a replacement `com.google.android.gms` APK. Do not change ROMs, install
debug-signed microG, disable Chrome or remove the current Google account.

Interpret any differences in Chrome version, GSF presence, ROM feature
or sign-in history as hypotheses, not proof of mechanism. If the two-device
test is inconclusive, compare microG Self-Check screenshots and
version-specific logs with personal identifiers redacted.

## 2026-11-13: Chrome sign-in failure vs Android account enumeration

Source-level review of **Android 10** `AccountManagerService`:
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-mainline-release/services/core/java/com/android/server/accounts/AccountManagerService.java

- Effective account visibility is resolved per app. An explicit app override is
  checked first; when absent, the OS may use the Android legacy visible/not
  visible defaults depending on caller identity/permissions/target version.
- The Android 10 `dumpsys account` dump prints a per-user **Accounts** listing
  (with type) and a later account-name-associated **Account visibility** section
  containing *stored* overrides. It does **not** directly print the outcome of
  `resolveAccountVisibility` for Chrome.
- An independent Android 10 package with `GET_ACCOUNTS` granted seeing one
  `com.google` account does **not** prove Chrome sees it. The differing
  per-package rules must be considered. However, it also means the general
  account-registration path is functioning.
- As of 2026-11-13, upstream microG issue #3379 remains open; the reporter has
  a similar Chrome/account mismatch on LineageOS, so a Huawei-only framework
  limitation is not sufficient to explain every instance.
  https://github.com/microg/GmsCore/issues/3379
- Historical upstream microG issue #1456 reported Chrome failing sign-in with
  existing accounts even with the platform self-check reportedly successful.
  https://github.com/microg/GmsCore/issues/1456
- Chromium's own sign-in UI can hide the account picker when it thinks no
  eligible accounts exist. This is separate from Android's bare stored
  account visibility. Reference (historical Chromium source):
  https://chromium.googlesource.com/chromium/src/+/3b8787c9098d1fefd19591d7c85a89ff7cec519d/chrome/browser/ui/android/signin/java/src/org/chromium/chrome/browser/ui/signin/SyncConsentFragmentBase.java

### What changed in the diagnostic

The read-only ADB script now first examines only `type=com.google` entries
listed in the **same Android user**, matches their names internally to saved
visibility rows, and prints *only numeric summaries*. Ambiguous/malformed
mappings are intentionally discarded. Never upload raw account dumps: they
may include account names, session data and debug history.

**Do not interpret a missing Chrome override as a confirmed failure**:
Android uses fallback rules. Conversely an explicit visible entry alone
does not show that Chrome's IdentityManager or Google Play Services integration
is functional.

### Release gate

Do not install CI-signed modified `com.google.android.gms` over the existing
official-microG-signed Huawei installation; it cannot be treated as a
signature-compatible Android update. Do not uninstall microG or remove
accounts to test. The PR remains a source-only hypothesis until an isolated
device with compatible microG signing and Chrome account-flow testing exists.
