#!/usr/bin/env python3
"""Read-only, privacy-limited diagnostics for GmsCore on Huawei / Android 10.

Only allowlisted properties and package metadata are shown by default.
Optional --account-types reads dumpsys account in memory but prints only two known
account type strings, never account names or passwords. Never uses root or logcat.
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


# Account type names, never account names, are included in opt-in reports.
KNOWN_ACCOUNT_TYPES = ("com.google", "app.revanced")
# Dumpsys lines can contain names/emails: only extract the account *type*.
ACCOUNT_TYPE_RE = re.compile(r"Account\s*\{name=[^}]*?,\s*type=([A-Za-z0-9_.]+)\}")
AUTHENTICATOR_TYPE_RE = re.compile(
    r"AuthenticatorDescription\s*\{type=([A-Za-z0-9_.]+)(?:[,} ])"
)
PACKAGE_UID_RE = re.compile(r"^package:([A-Za-z0-9_.]+)\s+uid:(\d+)\s*$")


def parse_known_account_types(raw):
    """Discard all raw account records and return only allowlisted type names."""
    accounts = set()
    authenticators = set()
    for line in raw.splitlines():
        match = ACCOUNT_TYPE_RE.search(line)
        if match and match.group(1) in KNOWN_ACCOUNT_TYPES:
            accounts.add(match.group(1))
        match = AUTHENTICATOR_TYPE_RE.search(line)
        if match and match.group(1) in KNOWN_ACCOUNT_TYPES:
            authenticators.add(match.group(1))
    return accounts, authenticators


def parse_known_package_uids(raw):
    """Return UID metadata for the fixed package allowlist only."""
    result = {}
    for line in raw.splitlines():
        match = PACKAGE_UID_RE.fullmatch(line.strip())
        if match and match.group(1) in PACKAGE_NAMES:
            result[match.group(1)] = match.group(2)
    return result


def build_account_type_report(accounts, authenticators):
    """No email, account name, token, or raw dumpsys line is returned."""
    lines = [
        "",
        "OPTIONAL ACCOUNT TYPE CHECK (dumpsys account; type names only)",
        "Absence means not seen in output; it does not prove no account exists.",
    ]
    for name in KNOWN_ACCOUNT_TYPES:
        lines.append(
            f"{name}: authenticator={'REGISTERED' if name in authenticators else 'NOT SEEN'}"
            f" | account={'PRESENT' if name in accounts else 'NOT SEEN'}"
        )
    lines.extend((
        "",
        "A registered authenticator does not mean an account exists.",
        "Android getUserData requires caller authorization as account-type manager;",
        "merely making an account visible or requesting GET_ACCOUNTS_PRIVILEGED",
        "does not confer permission to read another authenticator's private user data.",
        "The full dumpsys account output is processed in memory, not printed or saved.",
    ))
    return "\n".join(lines) + "\n"


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


def build_report(properties, installed, versions, uids=None):
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
            uid = (uids or {}).get(name, "unavailable")
            lines.append(f"{name}: INSTALLED | uid={uid} | versionName={version} | versionCode={version_code}")
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
    parser.add_argument("--account-types", action="store_true",
                        help="Opt in: read dumpsys account; output only com.google/app.revanced type presence")
    args = parser.parse_args()

    try:
        if adb("get-state") != "device":
            raise RuntimeError("ADB cihazı hazır değil; bağlantıyı kontrol edin.")
        properties = {key: adb("shell", "getprop", key) for key in PROPERTY_KEYS}
        installed = parse_package_list(adb("shell", "pm", "list", "packages", "--user", "0"))
        uids = parse_known_package_uids(adb("shell", "pm", "list", "packages", "-U", "--user", "0"))
        account_types = None
        if args.account_types:
            # Never write or echo this output; it can contain account addresses.
            account_types = parse_known_account_types(adb("shell", "dumpsys", "account"))
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

    report = build_report(properties, installed, versions, uids)
    if account_types is not None:
        report += build_account_type_report(*account_types)
    print(report, end="")
    if args.output:
        from pathlib import Path
        Path(args.output).write_text(report, encoding="utf-8")
        print(f"\nRapor yerel olarak kaydedildi: {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
