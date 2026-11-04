# Huawei P Smart S native login — research phase

Target: Huawei P Smart S, Android 10 / EMUI 12 C432. No root, bootloader unlock, downgrade, factory reset, or account removal.

## Evidence already collected
- AccountManager contains existing `app.revanced` and `com.huawei.hwid` accounts. Preserve both.
- Official GMS 26.34.65 failed at `SingletonComponentRouterProvider` due to missing `INTERACT_ACROSS_USERS`.
- Older GMS 19.2.75 failed at `SharedModuleProvider` for the same permission; GMS and GSF share UID 10618 and also triggered `OBSERVE_GRANT_REVOKE_PERMISSIONS` failure.
- RenzDagz `com.google.android.gmt` is a third-party cloned microG authenticator for account type `com.google`. A Google device-verification prompt appeared, but no `com.google` account was registered.
- Stock firmware has no verified system-wide signature spoofing.

## First milestone: source-only compatibility audit
1. Identify upstream base, current branch changes, package IDs, authenticator service, manifest and `res/xml/authenticator.xml`.
2. Trace account creation and token exchange in code, including the exact AccountManager account type.
3. Identify conflicts with official GMS, GSF, cloned helper, and Morphe RE; do not assume duplicate authenticators coexist.
4. Identify whether the current fork supports Huawei-specific signature behavior and Android 10.
5. Define unit tests and an isolated emulator test; never use a personal Google account in an unverified test build.

## Safety boundaries
- No phone-side installation, uninstallation, permission grants, data clearing, account removal, or credential capture in this phase.
- No copying signing keys, Google auth tokens, account databases or passwords into GitHub.
- Do not treat successful browser verification as proof of Android account registration or official app compatibility.
- Official microG requires signature spoofing for drop-in Play Services compatibility. Account-only functionality must be evaluated separately.

## Exit criteria
Document current implementation and blockers; propose the smallest testable code change with rollback plan before any phone-side action.
