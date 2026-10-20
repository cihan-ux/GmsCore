#!/usr/bin/env bash
set -euo pipefail
command -v adb >/dev/null || { echo "adb missing" >&2; exit 1; }
echo "== ADB device =="; adb devices -l
echo "== Device =="; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
echo "== Relevant packages (read-only) =="
adb shell pm list packages | grep -Ei 'fakegapps|lspatch|shizuku|google.android.gms|android.vending' || true
for pkg in inc.whew.android.fakegapps.psmarts dev.cihan.fakegapps.probe com.google.android.gms com.android.vending; do
    echo "-- $pkg --"
    adb shell pm path "$pkg" || true
done
echo "== ADB/Shizuku processes =="; adb shell ps -A | grep -E 'shizuku_server|system_server' || true
echo "No packages, permissions or data were modified."
