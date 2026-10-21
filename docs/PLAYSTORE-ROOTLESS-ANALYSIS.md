# Rootless EMUI 12 / Play Store: evidence and boundaries

Target: Huawei P Smart S AQM-L21A / AQM-LX1,
Android 10 (API 29), EMUI 12.0.0.226(C432E5R5P3).
These are **user-supplied device observations**, not tests performed by CI.

## Observed evidence — 21 October 2026

| Observation | Meaning | Does not prove |
| --- | --- | --- |
| Installed GmsCore 0.3.17.252432-hw, Vending 19.5.13, GSF v0.1.0 | Known versions | Current upstream compatibility |
| Play Store loads app pages and receives Google OAuth tokens via microG | Some account and catalog paths work | Google Play downloads will work |
| \`GooglePlayServicesUtil: com.android.vending requires Google Play services, but their signature is invalid\` | Caller rejects GmsCore signature | It directly caused each failed install |
| Vending UID \`10136\`, path \`/data/app\`, no privileged privateFlag | Nonprivileged, user-installed package | Every download must fail |
| \`INSTALL_PACKAGES\` denied; \`REQUEST_INSTALL_PACKAGES\` not in requested permission list | Privileged silent installation is unavailable; standard user-action route is not declared | The exact point of download failure |
| Installer sessions from 18–20 October have UID \`10250\` or \`10291\` | Old installer identities exist | Present Vending UID owns those sessions |
| No new session IDs in the 21 October attempt | No new session observed in snapshot | No network request was sent |
| Download Manager / Package Installer packages present, enabled | Relevant system packages exist | Delivery pipeline operates correctly |

## Huawei differentiated-signature source review

In this repository:

- \`fake-signature/src/huawei/AndroidManifest.xml\` declares
  \`InitReceiver\`, \`InitProvider\`, and \`SignatureService\`.
- \`fake-signature/src/huawei/java/com/huawei/signature/diff/InitProvider.java\`
  *tries* to start the service and silently ignores failures.
- \`fake-signature/src/huawei/java/com/huawei/signature/diff/InitReceiver.java\`
  also starts it and silently ignores exceptions.
- \`fake-signature/src/huawei/java/com/huawei/signature/diff/SignatureService.java\`
  implements an \`ISignatureService\` Binder whose \`onTransact\` rejects
  callers with \`Binder.getCallingUid() > 10000\`. Only a framework/system-side
  caller can use that path; installing a service APK is **not** a framework patch.
- \`AppListDatabaseOpenHelper.java\` chooses the suggested signature by package
  allowlist. That only matters **if** Huawei firmware calls the service.

**Bounded conclusion:** Existing Huawei service declarations do not demonstrate
that this EMUI 12 C432 build has the firmware integration. A signature-invalid
warning is strong evidence that at least one Play Store signature validation
continues to reject the installed microG package. We have not proved which
PackageManager path is involved.

## Android installation model

- On Android 10 \`INSTALL_PACKAGES\` is signature/privileged; a user app under
  \`/data/app\` cannot obtain it through a normal \`pm grant\`.
- Normal applications can use user-confirmed installation flows, but an
  unrelated Play Store APK cannot gain privileged Google installer identity
  merely by adding \`REQUEST_INSTALL_PACKAGES\` or changing an AppOp.
- Android's \`PackageInstaller.Session\` commit and progress flags are
  not proof of an installed application. The device may need user approval.
- Because the Play Store 19.5.13 binary is proprietary, this repository does
  **not** contain its full source for a reliable installation-path patch.
  Repacking/re-signing Vending may break its signing identity and compatibility.

Official context:
- https://developer.android.com/reference/android/content/pm/PackageInstaller
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- https://source.android.com/docs/core/permissions/perms-allowlist
- https://github.com/microg/GmsCore/wiki/Signature-Spoofing

## Next discriminating experiment

Run the read-only tool in \`tools/psmarts-diagnostics\` before and after pressing
Install exactly once. Compare old/new session IDs and the counters for signature
validation, installer events and failures. This can narrow the next step but
cannot identify server rejection from counters alone. Future work can add an
*opt-in, on-device* diagnostic probe that tracks **only** state transitions,
without collecting raw account tokens.

## Non-goals

No root, flash, downgrade, system-server hooks, stolen credentials,
fabricated tokens, Google certification bypass or claims of a working Play Store
until proven on-device. Never write raw \`GmsAuthenticator\` logs into CI reports.
