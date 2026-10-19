# Google Play Store 53.5.48 patched build: download/crash triage

Status: **diagnostic only, no Play Store APK patch and no account changes**.

## Reproduced on Huawei P Smart S / EMUI 12 / Android 10

- Rootless installer smoke test succeeded via ordinary Android PackageInstaller and user confirmation. Android can install a selected local APK.
- Play Store says "Bir sorun oluştu. Tekrar deneyin" before a new application download begins.
- An earlier Play Store process reported `SecurityException` from `AccountManager.getUserData` for account type `app.revanced`. The call is caught by Finsky; its status as the direct download blocker is **unproven**. The Play Store APK and MorpheApp microG-RE have distinct signing certificates.
- In the 2026-10-20 00:00 log: Play Store self-update tries to add `arm64_v8a` and `tr` configuration splits, logs `SU: Cannot patch ... bad base file hash`, enters full-download fallback, and shortly afterward the Play Store process crashes and restarts. **Timing does not prove self-update caused the crash**.
- Other warnings: no GServices read permission, and UserManager query failure on non-privileged Play Store.

## Next decisive observation: capture the actual fatal exception

The previously used grep selected `Finsky` and selected network tags, but **omitted AndroidRuntime**, which likely contains the exception and stack trace.

On Linux Mint:
```bash
adb logcat -c
# On the phone: launch Play Store; reproduce the failing Install action, then run immediately:
adb logcat -d -b crash -v threadtime AndroidRuntime:E '*:S' > "$HOME/playstore-fatal.txt"
wc -l "$HOME/playstore-fatal.txt"
```

If the output is empty (native crashes may not use AndroidRuntime), capture a redacted **crash-only** buffer:
```bash
adb logcat -d -b crash -v threadtime |
  sed -E 's/ya29\.[A-Za-z0-9._~-]+/[REDACTED_TOKEN]/g; s/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/[REDACTED_EMAIL]/g' |
  tail -n 120 > "$HOME/playstore-crash-buffer.txt"
```

**Review files manually before sharing**, as logs can contain sensitive information.

## Constraints

Do not root/downgrade, clear Google accounts, uninstall/replace the existing Play Store, install an official Google-signed replacement over the experimental store, or assume that changing an AccountManager exception will fix downloads. A repackaged Play Store would require a different signing key and may not update over the installed experimental APK.

When the crash stack is available, inspect the *first* `Caused by` and its caller stack, distinguish Play Store `self-update`, UI crash, account access, signature/registration rejection, and networking.
