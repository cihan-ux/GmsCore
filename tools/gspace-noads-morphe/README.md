# Gspace NoAds — Morphe patch source

Unofficial, experimental **Gspace – Remove Ads** patch for `com.gspace.android`, version **2.3.3**.
This is a **source project**, not a ready-to-import `.mpp` bundle. It is not affiliated with Gspace or Morphe.

## What it changes

Exactly reproduces the changes in `classes.dex` from the provided working original and NoAds test APKs.
The bundle includes only 1097 altered offset/byte records (2,349 compressed bytes), **not a copy of the Gspace APK or DEX**.

SHA-256 of original DEX: `3596977526bfd45da5c8cdca2f72894fd8dca10bb59e73dcee9da236381fb067`
SHA-256 of modified DEX: `943b8697a03162e6fabf5099d204b812c5ae56527990ee4def8faaa15aee3bba`

The patch **rejects different DEX builds**, including future updates with the same version label.
It is not an anti-advertising filter for any arbitrary Gspace release.

## Build a Morphe .mpp bundle

1. Install JDK 21 and Gradle 9.8.1, or copy these project sources into the [official Morphe patches template](https://github.com/MorpheApp/morphe-patches-template).
2. Morphe's Gradle plugin is hosted on GitHub Packages; follow the [upstream authentication guide](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md).
   Set `GITHUB_ACTOR` and `GITHUB_TOKEN` (read:packages) or user-level Gradle credentials. **Never commit the token.**
3. Run `gradle buildAndroid` from this directory (containing `settings.gradle.kts`). The upstream template contains a wrapper, but this subproject intentionally does not ship a wrapper JAR.
4. Read the generated `.mpp` from `patches/build/libs/`.
5. Add the patch bundle as a patch source in Morphe, or use Morphe Desktop with `-p` for local `.mpp` testing.

## Test the DEX delta

With a locally owned original Gspace 2.3.3 APK:

```sh
python3 verify_delta.py /path/to/Gspace-original.apk
```

This test does not modify the original APK and verifies the exact patched DEX SHA-256.

## Safety notes

- Applying the patch makes a newly signed APK. It cannot normally update the vendor-signed installation in place.
- Keep a backup of app and account data. Do not uninstall a working installation solely to test this.
- Use the original unpatched APK as input. A previously modified APK or different 2.3.3 build is rejected.
- The Morphe `.mpp` build and Morphe patch execution have **not been verified**. Delta replay has been checked locally.
- Do not upload account backups, signing keys, authentication tokens or proprietary APKs to this public repository.
- This project is isolated in `tools/gspace-noads-morphe/` on a feature branch and does not change the GmsCore runtime.

## Sources

- https://github.com/MorpheApp/morphe-patches-template
- https://github.com/MorpheApp/morphe-patcher/blob/main/docs/4_apis.md

Newly authored patch code: GPL-3.0-or-later. See the [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html). All third-party trademarks and binaries belong to their respective owners.
