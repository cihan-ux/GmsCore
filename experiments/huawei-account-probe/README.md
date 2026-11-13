# Huawei microG — Rootless AccountManager visibility probe

**Standalone APK:** `dev.cihan.microg.accountprobe`

Purpose: distinguish a missing `com.google` authenticator from an account that
Android does not expose to an independent application, and identify Chrome-related
package presence without changing accounts or microG. This is a **diagnostic
baseline**, not a fix or a test of Chrome's internal account-picker API.

## Safety
- Different app ID from `com.google.android.gms`: does not replace or uninstall microG.
- Requests only `GET_ACCOUNTS`, at runtime and with explicit user action.
- No `INTERNET` permission, network calls, account modifications, or Google token reads.
- Never displays, stores, or logs account email addresses, names, passwords or tokens.
- Only reports the number of Google accounts visible **to the probe's own package**.
  It **cannot** determine how many accounts are visible specifically to Chrome.
- Works on Android 10 / EMUI 12 (minSdk=29); also suitable for comparing Android 11 LineageOS.
- v0.2 additionally checks stable, Beta, Dev, Canary and Chromium package names and
  lists local packages that can handle an HTTPS view intent. Some HTTPS handlers may not
  be browsers; this list is **not** a proof of Chrome being installed.
- On Android 10 with targetSdk 29, standard Android 11 package-visibility filtering
  should not hide Chrome from PackageManager; Huawei vendor behavior can still differ.
- The Google account count is only for this probe's package; neither the count nor the
  HTTPS handlers list directly tests Chrome's account visibility.
- Does **not** add signature spoofing to EMUI and cannot prove Chrome login will work.

## How to run
1. Install the `app-debug.apk` produced by the
   `Huawei microG account visibility (read-only probe)` Actions workflow.
   Verify the ZIP and APK SHA-256 from the build logs.
2. Open **microG Account Probe**.
3. Tap **Hesap iznini iste** if Android offers a permission prompt.
4. Tap **Yeniden kontrol et** and read the account *count*, Google authenticator
   presence, Chrome variant checks, and HTTPS handlers.
5. Compare these observations on the Huawei P Smart S and the Samsung A7.
6. Uninstall this standalone probe whenever desired; it does not remove Google
   accounts or change microG settings.

**Interpretation caveat:** A zero count for the probe can coexist with a Google
account visible to Chrome, because AccountManager visibility may differ by
application package. Successful account enumeration does not prove that OAuth
tokens, Chrome synchronization, or signature spoofing work.

## Build locally
```bash
gradle -p experiments/huawei-account-probe :app:assembleDebug
```
The GitHub Actions workflow uses Java 17, Gradle 8.9, Android SDK 35.

## v0.2 upgrade note

The CI debug signing key may be different between workflow runs. If Android reports
an incompatible package signature when updating v0.1 to v0.2, it is safe to uninstall
**only this independent Account Probe app** and install the v0.2 APK. The probe has
no stored Google credentials. Never uninstall or clear data for microG itself.

## v0.3 Chrome permissions diagnostics (2026-11-12)

The user's Huawei AQM-LX1, Android API 29, reports one `com.google` account
visible to the independent v0.1 probe, while Chrome 157 shows the Google
services settings screen rather than an account picker when the profile icon
is tapped. **These observations do not prove that Chrome has access to the
account or that Chrome even sends an auth request.**

This update prints the package hosting Android's `com.google` authenticator
and checks whether the installed stable Chrome package **requests** and is
**granted** `android.permission.GET_ACCOUNTS` and
`android.permission.GET_ACCOUNTS_PRIVILEGED`. It also retains the v0.2
package/HTTPS handler inventory.

Interpretation:
- If Chrome does not request a permission, this is **not automatically a bug**:
  modern Android account visibility may be provided by the authenticator or
  through user approval, independent of GET_ACCOUNTS.
- Even when GET_ACCOUNTS is granted, the result **does not show which accounts
  Chrome sees**. Chrome's own account selection, OAuth, and native sync remain
  separate from permission grants.
- This diagnostic does not install or patch the experimental microG APK,
  apply framework signature spoofing, or modify the user's Google account.
- On Android 10/EMUI, package permissions can be inspected without root,
  but some OEM-specific policies remain inaccessible to third-party apps.

To update: install the v0.3 `app-debug.apk` from the matching successful CI
artifact. If Android refuses an in-place update due to a different CI signing
key, remove **only** `dev.cihan.microg.accountprobe` (not microG or Chrome)
and install v0.3. Do not clear Google/Chrome/microG data.


## 2026-11-13: Read-only USB/ADB visibility audit (no APK needed)

Chrome does not present the existing account on Huawei AQM-LX1 even though
microG's "Allow apps to find accounts" is enabled. Account Probe v0.3
sees one Google account to itself. Signature diagnostics v0.5 confirmed
expected distinct legitimate microG and Chrome signing identities, but this
does not prove why Chrome's account chooser is unavailable.

Upstream report from a *different* device and ROM:
https://github.com/microg/GmsCore/issues/3379 (opened 2026-04-07).
Missing EMUI signature spoofing is not proven to be the sole cause.

### Optional follow-up from an ADB-enabled computer

Script: [scripts/chrome_visibility_dumpsys.py](scripts/chrome_visibility_dumpsys.py)

1. On a Linux/Windows computer, install adb and Python 3. Connect
   the Huawei by USB and authorize USB debugging. Confirm that the
   command **adb devices** shows **device**.
2. Download and inspect the plain-text Python script above.
3. Run **python3 chrome_visibility_dumpsys.py** from its download folder.
   The script executes **adb shell dumpsys account** in READ-ONLY mode,
   processes the response in memory, prints only counts and numeric
   visibility codes. No data is saved or uploaded.
4. Share only the script's ALREADY REDACTED SUMMARY.
   NEVER upload raw dumpsys account output because it may include
   personal account identifiers, e-mail addresses and session history.

The script reports *stored* visibility keys for Chrome and Android legacy
defaults across ALL account types. It CANNOT determine effective account
visibility, which entries belong specifically to a Google account, or
why Chrome's picker/sign-in fails. An absent entry is INCONCLUSIVE, not
proof that Chrome cannot access the account. Some Huawei/EMUI builds may
withhold this section.

No root, account removal, reinstalling microG or replacing Chrome is needed.
CI uses synthetic fixtures to verify privacy redaction.
Android source: AccountManagerService dumpUser():
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/accounts/AccountManagerService.java


## 2026-11-13: Samsung Galaxy A7 versus Huawei AQM-LX1

New user evidence:
- Samsung Galaxy A7 (2018), LineageOS 18.1 / Android 11, with microG
  installed as an APK: **Google sign-in works inside Chrome**.
- Huawei P Smart S AQM-LX1, EMUI 12 / Android API 29, microG
  0.3.17.252432-hw, Chrome 157.0.8103.50: Google account exists and is
  visible to the Account Probe, **but Chrome does not show a sign-in picker**.
- Installation by APK alone is not the relevant distinction: Android framework
  account visibility, GMS implementation, Chrome versions, and effective
  signature spoofing may all differ between these ROMs.

According to the [official microG signature spoofing documentation](https://github.com/microg/GmsCore/wiki/Signature-Spoofing),
LineageOS builds from 2024-02-26 support **microG-specific** spoofing.
Do **not** assume this older LineageOS 18.1 build includes it without
confirmation. The older microG 0.3.17 Self-Check cannot reliably establish
all newer signature-spoofing requirements. Chrome sign-in working on Samsung
does **not** independently prove spoofing is present or identify the Huawei
root cause.

### New read-only Account Probe v0.4

The same standalone APK can run on both Android 10 and Android 11. It reports:
- Manufacturer/model, Android release/API and build type (not a unique device ID).
- microG, Chrome, Play Store and optional Google Services Framework package versions.
- Android Google authenticator package and Google account count **visible only to
  the independent probe**, with the optional GET_ACCOUNTS grant.
- Chrome's requested and granted GET_ACCOUNTS-related permissions.
- **Public APK signing-certificate fingerprints** for microG, Chrome, and
  Play/Companion (no passwords, account names or tokens).
- Whether the optional legacy `FAKE_PACKAGE_SIGNATURE` permission name is
  defined/granted. This is **only a weak clue**: its absence does not rule out
  ROM-level signature spoofing; its presence does not prove functional spoofing.
- Which installed packages claim HTTPS links, not which account Chrome uses.

Safest experiment: install the standalone probe on the working A7 and report
only its on-screen output; compare with the known Huawei v0.3/v0.5 observations.
Do not modify microG/Chrome or remove any account. If the APK update fails
because separate CI runs generated different debug signing keys, uninstall
**only the independent probe app** `dev.cihan.microg.accountprobe`, never
`com.google.android.gms`.

A stronger comparison would use a modern microG Self-Check on a sacrificial
environment; **do not install another microG version over the working A7
or Huawei just to run it**. Chrome's private account picker and OAuth calls
cannot be inspected by this independent APK.
