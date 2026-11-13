#!/usr/bin/env python3
"""Read-only Android 10 account-visibility audit; NO personal-data output.

Requires a local, USB-connected ADB-enabled Android phone. ADB's raw
'dumpsys account' output may include identifying account names or metadata.
It is read in memory only, never saved, printed or transmitted.

This counts only stored Android visibility entries for Chrome and legacy
defaults across all account types. It is NOT effective Chrome account
visibility, a Google-specific result, OAuth, or signature-spoofing evidence.
"""

from __future__ import annotations

import argparse
from collections import Counter
import re
import subprocess
import sys

PACKAGE_NAMES = {
    "com.android.chrome": "Chrome (explicit)",
    "android:accounts:key_legacy_not_visible": "Legacy not-visible default",
    "android:accounts:key_legacy_visible": "Legacy visible default",
}
ENTRY = re.compile(r"^(" + "|".join(re.escape(p) for p in PACKAGE_NAMES) + r"),\s*([0-4])$")
VISIBILITY = {
    0: "UNDEFINED",
    1: "VISIBLE",
    2: "USER_MANAGED_VISIBLE",
    3: "NOT_VISIBLE",
    4: "USER_MANAGED_NOT_VISIBLE",
}


def summarize(raw: str) -> tuple[int, dict[str, Counter[int]]]:
    sections = 0
    inside = False
    found = {pkg: Counter() for pkg in PACKAGE_NAMES}
    for line in raw.splitlines():
        line = line.strip()
        if line == "Account visibility:":
            sections += 1
            inside = True
            continue
        if line.startswith("User ") and line.endswith(":"):
            inside = False
            continue
        if not inside:
            continue
        match = ENTRY.fullmatch(line)
        if match:
            found[match.group(1)][int(match.group(2))] += 1
    return sections, found


def read_from_adb() -> str:
    try:
        p = subprocess.run(
            ["adb", "shell", "dumpsys", "account"],
            capture_output=True, text=True, timeout=30, check=False,
        )
    except FileNotFoundError:
        raise RuntimeError("ADB bulunamadı. Bilgisayara Android platform-tools kurulmalı.") from None
    except subprocess.TimeoutExpired:
        raise RuntimeError("ADB hesap sorgusu zaman aşımına uğradı.") from None
    if p.returncode:
        # Never print vendor stderr, which can contain account identifiers.
        raise RuntimeError("ADB sorgusu başarısız; USB bağlantısını kontrol et.")
    return p.stdout


def report(raw: str) -> str:
    sections, found = summarize(raw)
    out = [
        "Chrome hesap görünürlüğü — YALNIZCA OKUMA",
        "E-posta adresleri, hesap adları ve tokenlar gösterilmez.",
    ]
    if sections == 0:
        out += [
            "SONUÇ: BELİRSİZ — Account visibility bölümü okunamadı.",
            "EMUI farklı bir dumpsys biçimi kullanıyor veya cihaz kilitli olabilir.",
        ]
        return "\n".join(out)
    out.append("Görünürlük bölümü sayısı: " + str(sections))
    for package, description in PACKAGE_NAMES.items():
        codes = found[package]
        if not codes:
            out.append(description + ": KAYIT BULUNAMADI (varsayılan erişim yine mümkün)")
            continue
        safe_codes = ", ".join(
            str(value) + " (" + VISIBILITY[value] + ") x" + str(count)
            for value, count in sorted(codes.items())
        )
        out.append(description + ": " + safe_codes)
    out += [
        "NOT: Bunlar TÜM hesap türleri için saklanan kurallardır.",
        "Google hesabına ait oldukları veya Chrome'un onu gördüğü kanıtlanmaz.",
        "Kayıt bulunmaması erişim olmadığı anlamına GELMEZ.",
        "Chrome oturum açma, OAuth ve imza taklidi bu yöntemle ölçülmez.",
    ]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stdin", action="store_true", help="Use stdin for offline test")
    args = parser.parse_args()
    try:
        raw = sys.stdin.read() if args.stdin else read_from_adb()
    except RuntimeError as error:
        print("HATA: " + str(error), file=sys.stderr)
        return 1
    print(report(raw))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
