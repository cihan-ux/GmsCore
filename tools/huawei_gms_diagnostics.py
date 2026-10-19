#!/usr/bin/env python3
"""Read-only, privacy-limited diagnostics for GmsCore on Huawei / Android 10.

Only a fixed allowlist of non-secret properties and package metadata is queried.
Never runs root, package installation/removal, account dumps, or logcat.
"""
import argparse
import re
import subprocess
import sys

PROPERTY_KEYS = (
    "ro.product.manufacturer",
    "ro.product.model",
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.build.version.emui",
    "ro.build.display.id",
)

PACKAGE_NAMES = (
    "com.google.android.gms",        # Standard microG/GMS package
    "app.revanced.android.gms",       # ReVanced microG fork
    "org.microg.gms",                # microG restricted user flavor
    "com.android.vending",           # Play Store
    "com.google.android.gsf",        # Google Services Framework
    "com.google.android.gsf.login",  # Legacy authenticator
    "com.huawei.appmarket",          # Huawei AppGallery
)

MAX_VALUE_LENGTH = 150


def parse_package_list(raw):
    """Return only exact package IDs (never substring matches)."""
    return {
        line[8:].strip()
        for line in raw.splitlines()
        if line.startswith("package:") and re.fullmatch(r"[A-Za-z0-9_.]+", line[8:].strip())
    }


def parse_package_version(raw):
    """Read the first matching version fields; no other dumpsys data escapes."""
    name = re.search(r"(?m)^\s*versionName=(\S+)", raw)
    code = re.search(r"(?m)^\s*versionCode=(\d+)(?:\s|$)", raw)
    return (
        clean_value(name.group(1)) if name else "unavailable",
        code.group(1) if code else "unavailable",
    )


def clean_value(value):
    """Restrict report to printable, single-line device metadata."""
    value = "".join(c for c in value if c.isprintable() and c not in "\r\n\t")
    return value[:MAX_VALUE_LENGTH] if value else "(empty)"


def adb(*args):
    try:
        completed = subprocess.run(
            ("adb",) + args, capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=25, check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("adb bulunamadı. Android Platform Tools kurun.") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("ADB yanıtı zaman aşımına uğradı.") from exc
    if completed.returncode:
        # Do not print unfiltered errors: they may contain connected-device identifiers.
        raise RuntimeError("ADB sorgusu başarısız. USB hata ayıklama iznini ve cihaz bağlantısını kontrol edin.")
    return completed.stdout.strip()


def build_report(properties, installed, versions):
    lines = [
        "GmsCore-hw | Huawei read-only diagnostic report",
        "No account names, serial numbers, Android IDs, logs or tokens collected.",
        "",
        "DEVICE",
    ]
    for key in PROPERTY_KEYS:
        lines.append(f"{key}: {clean_value(properties.get(key, 'unavailable'))}")
    lines.extend(("", "KNOWN PACKAGES (Android user 0)"))
    for name in PACKAGE_NAMES:
        if name not in installed:
            lines.append(f"{name}: NOT INSTALLED / NOT VISIBLE")
        else:
            version, version_code = versions.get(name, ("unavailable", "unavailable"))
            lines.append(f"{name}: INSTALLED | versionName={version} | versionCode={version_code}")
    lines.extend((
        "",
        "INTERPRETATION LIMITS",
        "- Presence does not prove a working account provider, signature spoofing, or Play Store compatibility.",
        "- The Huawei GmsCore hmsHuawei flavor uses package com.google.android.gms,",
        "  while ReVanced GmsCore typically uses app.revanced.android.gms.",
        "- Package/signature conflicts must be checked before any installation; never blindly uninstall.",
        "- ADB cannot grant privileged MANAGE_USERS, CREATE_USERS or platform signature permissions.",
        "- This script changes no settings and sends nothing over the network itself.",
    ))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", metavar="PATH", help="Optional local text report (review before sharing)")
    args = parser.parse_args()

    try:
        if adb("get-state") != "device":
            raise RuntimeError("ADB cihazı hazır değil; bağlantıyı kontrol edin.")
        properties = {key: adb("shell", "getprop", key) for key in PROPERTY_KEYS}
        installed = parse_package_list(adb("shell", "pm", "list", "packages", "--user", "0"))
        versions = {}
        for name in PACKAGE_NAMES:
            if name in installed:
                try:
                    versions[name] = parse_package_version(adb("shell", "dumpsys", "package", name))
                except RuntimeError:
                    versions[name] = ("unavailable", "unavailable")
    except RuntimeError as exc:
        print(f"Tanılama tamamlanamadı: {exc}", file=sys.stderr)
        return 1

    report = build_report(properties, installed, versions)
    print(report, end="")
    if args.output:
        from pathlib import Path
        Path(args.output).write_text(report, encoding="utf-8")
        print(f"\nRapor yerel olarak kaydedildi: {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
