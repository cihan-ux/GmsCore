# Huawei P Smart S — rootless GSF / microG account compatibility laboratory

**Status: source research and read-only preflight. Not a working Google Play repair or APK.**

Device under examination: AQM-LX1, Android 10 / EMUI 12.0.0.226, locked bootloader.
Goals: Google account sign-in through an open-source authenticator, an unprivileged
GSF provider, and eventual app compatibility without claiming genuine Google certification.

## Verified blocker

- Official Google-signed `com.google.android.gsf` (Android 10 build) is installed as
  `/data/app`, not as a privileged system app.
- Account addition crashes in `com.google.process.gapps`:
  `GoogleSettingsProvider.onCreate` -> `UserManager.getProfileParent`
  -> `SecurityException` / system `checkManageUsersPermission`.
- `android.permission.MANAGE_USERS` is `signature|privileged`; ordinary ADB cannot
  grant it. Granting `INTERACT_ACROSS_USERS` does not satisfy this Android 10
  / Huawei service's confirmed `MANAGE_USERS` check.
- An original Google-signed GMS, GSF, and Play Store package can be installed, but
  successful installation != compatibility, usable account sign-in or Play certification.
- A differently signed replacement for Google's `com.google.android.gsf` cannot
  update the Google-signed one in-place. Its `com.google.uid.shared` relationship to
  Google Play services makes a swap especially risky. Do **not** uninstall or clear
  data as part of this lab.

## Code bases to compare

- [microG GoogleServicesFramework: GoogleSettingsProvider](https://github.com/microg/GoogleServicesFramework/blob/master/src/com/google/android/gsf/settings/GoogleSettingsProvider.java):
  `onCreate()` opens a local helper and avoids invoking `UserManager.getProfileParent`.
  Source is incomplete in several other areas; this does NOT prove account login works.
- [microG GmsCore LoginActivity](https://github.com/microg/GmsCore/blob/master/play-services-core/src/main/java/org/microg/gms/auth/login/LoginActivity.java):
  separate open-source account-login flow.
- [microG signature-spoofing prerequisites](https://github.com/microg/GmsCore/wiki/Signature-Spoofing):
  current stock EMUI 12 signature checks cannot be changed by an ordinary APK.

## Current safe deliverable

`tools/huawei_google_login_preflight.py` is **read-only** and prints no credentials,
tokens, account names or raw logs. Run from repository root:

```sh
python3 tools/huawei_google_login_preflight.py
python3 -m unittest discover -s tools -p 'test_huawei_google_login_preflight.py'
```

Do not upload raw logcat, password, Google OAuth tokens, signing keys, or private accounts.

## Implementation gates

1. **Completed:** identify the privileged GSF crash; confirm why identical APK reinstall
   cannot solve `MANAGE_USERS`.
2. **Next:** isolate an independent GSF compat implementation based on the microG open-source
   provider and GmsCore authenticator. Use a unique package / provider authority in early
   tests so it cannot replace or conflict with installed Google components.
3. **Only with explicit consent and a backup strategy:** decide whether a different-signer
   GSF/GmsCore stack is worth migrating to. This likely requires changing installed Google
   packages and shared-UID state; not supported by a silent or in-place install.
4. **Then:** test Android `com.google` account type with the microG authenticator and
   separately assess Play Store compatibility. Google's official Play Store cannot
   be assumed to work with a custom-signed replacement.

No device mutation, root, downgrade, package uninstall or account change performed.
