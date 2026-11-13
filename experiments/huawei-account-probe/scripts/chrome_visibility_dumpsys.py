#!/usr/bin/env python3
"""Privacy-preserving, read-only Android AccountManager visibility audit.

This script invokes only 'adb shell dumpsys account' and processes its output
in memory. Raw output may contain account names, e-mail addresses, or session
history and MUST NOT be shared. Only aggregated results are printed.

AOSP Android 10 provides the account type in the 'Accounts:' listing and later
groups stored visibility rules by account name. We link only *unambiguous*
Google account labels within the same Android user, then count whitelisted
stored visibility codes. The result is NOT Chrome's effective account access,
OAuth, or signature-spoofing evidence. EMUI may change the dump format.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import re
import subprocess
import sys

PACKAGE_NAMES = {
    "com.android.chrome": "Chrome (explicit)",
    "android:accounts:key_legacy_not_visible": "Legacy not-visible default",
    "android:accounts:key_legacy_visible": "Legacy visible default",
}
ACCOUNT_LINE = re.compile(r"^Account \{name=(.*), type=([^}]+)\}$")
ENTRY = re.compile(r"^(\S+),\s*([0-4])$")
ANY_ENTRY = re.compile(r"^\S+,\s*\d+$")
VISIBILITY = {
    0: "UNDEFINED",
    1: "VISIBLE",
    2: "USER_MANAGED_VISIBLE",
    3: "NOT_VISIBLE",
    4: "USER_MANAGED_NOT_VISIBLE",
}


@dataclass
class Audit:
    sections: int
    google_accounts_listed: int
    matched_google_labels: int
    ambiguous_labels: int
    counts: dict[str, Counter[int]]


def _is_user_heading(line: str) -> bool:
    return line.startswith("User ") and line.endswith(":")


def summarize(raw: str) -> Audit:
    # First pass: map account names to account types, separately per Android
    # user. Names are used in-memory only and never included in the report.
    known: dict[int, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    user = 0
    in_accounts = False
    for raw_line in raw.splitlines():
        line = raw_line.strip()
        if _is_user_heading(line):
            user += 1
            in_accounts = False
            continue
        if line.startswith("Accounts: "):
            in_accounts = True
            continue
        if in_accounts:
            matched = ACCOUNT_LINE.fullmatch(line)
            if matched:
                known[user][matched.group(1)].add(matched.group(2))
            else:
                in_accounts = False

    # Second pass: match visibility records only after an account-name line
    # that unambiguously belongs to Google within this user.
    codes = {pkg: Counter() for pkg in PACKAGE_NAMES}
    google_labels = set()
    ambiguous = set()
    sections = 0
    user = 0
    in_visibility = False
    current_account = None
    for raw_line in raw.splitlines():
        line = raw_line.strip()
        if _is_user_heading(line):
            user += 1
            in_visibility = False
            current_account = None
            continue
        if line == "Account visibility:":
            sections += 1
            in_visibility = True
            current_account = None
            continue
        if not in_visibility:
            continue
        types = known[user].get(line)
        if types:
            current_account = (user, line) if types == {"com.google"} else None
            if current_account is not None:
                google_labels.add(current_account)
            if "com.google" in types and len(types) != 1:
                ambiguous.add((user, line))
            continue
        match = ENTRY.fullmatch(line)
        if match:
            pkg, value = match.group(1), int(match.group(2))
            if current_account is not None and pkg in codes:
                codes[pkg][value] += 1
            continue
        # An unrelated visibility entry is not an account-name boundary.
        if ANY_ENTRY.fullmatch(line):
            continue
        # Unknown or unparseable account label: do not misattribute entries.
        current_account = None

    listed = sum(
        1 for user_map in known.values()
        for types in user_map.values() if "com.google" in types
    )
    return Audit(sections, listed, len(google_labels), len(ambiguous), codes)


def read_from_adb() -> str:
    try:
        p = subprocess.run(
            ["adb", "shell", "dumpsys", "account"],
            capture_output=True, text=True, timeout=30, check=False,
        )
    except FileNotFoundError:
        raise RuntimeError("ADB bulunamadı; Android platform-tools gerekli.") from None
    except subprocess.TimeoutExpired:
        raise RuntimeError("ADB sorgusu zaman aşımına uğradı.") from None
    if p.returncode:
        # Vendor stderr can contain identifying information; never print it.
        raise RuntimeError("ADB sorgusu başarısız; USB hata ayıklamayı kontrol et.")
    return p.stdout


def report(raw: str) -> str:
    audit = summarize(raw)
    out = [
        "Chrome / Google hesabı görünürlük kaydı — YALNIZCA OKUMA",
        "Hesap adları, e-posta adresleri ve tokenlar gösterilmez.",
    ]
    if audit.sections == 0:
        out += [
            "SONUÇ: BELİRSİZ — Account visibility bölümü okunamadı.",
            "Cihaz kilitli olabilir veya EMUI farklı bir dumpsys biçimi kullanıyor.",
        ]
        return "\n".join(out)

    out.append("Görünürlük bölümü sayısı: " + str(audit.sections))
    out.append("Listede Google olarak tanınan hesap sayısı: " +
               str(audit.google_accounts_listed))
    out.append("Görünürlük bloğuyla eşleşen Google hesap sayısı: " +
               str(audit.matched_google_labels))
    if audit.ambiguous_labels:
        out.append("Birden fazla hesap türüne ait isim: " +
                   str(audit.ambiguous_labels) + " (kayıtlar hariç tutuldu)")

    if audit.google_accounts_listed == 0 or audit.matched_google_labels == 0:
        out.append("SONUÇ: BELİRSİZ — Google hesabına ait kaydedilmiş "
                   "görünürlük satırı güvenle eşleştirilemedi.")
    for package, description in PACKAGE_NAMES.items():
        counts = audit.counts[package]
        if not counts:
            out.append(description + ": GOOGLE HESABINA AİT AÇIK KAYIT YOK "
                       "(varsayılan erişim yine mümkün)")
        else:
            values = ", ".join(
                str(value) + " (" + VISIBILITY[value] + ") x" + str(n)
                for value, n in sorted(counts.items())
            )
            out.append(description + ": " + values)
    out += [
        "NOT: Bunlar Google hesabıyla eşleştirilen SAKLANMIŞ kurallardır.",
        "Kayıt olmaması Chrome'un hesabı göremediğini kanıtlamaz.",
        "Etkin hesap erişimi, Chrome oturum açma ve OAuth burada ölçülmez.",
        "Ham adb shell dumpsys account çıktısını ASLA paylaşma.",
    ]
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stdin", action="store_true", help="Read offline test data")
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
