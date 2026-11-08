#!/system/bin/sh
# Huawei P Smart S / EMUI read-only firmware identifiers.
# Usage in Termux: sh termux-firmware-readonly.sh
# No root, Shizuku, ADB, installation, system modification, account data or tokens.
set -u

if [ ! -x /system/bin/getprop ]; then
  echo "ERROR: /system/bin/getprop not available"
  exit 1
fi

property() {
  key="$1"
  value=$(/system/bin/getprop "$key" 2>/dev/null)
  if [ -n "$value" ]; then
    printf '%s: %s\n' "$key" "$value"
  else
    printf '%s: (not exposed)\n' "$key"
  fi
}

printf '%s\n' 'P Smart S EMUI build check (READ-ONLY)'
property ro.product.model
property ro.product.name
property ro.build.display.id
property ro.build.version.emui
property ro.build.version.sdk
property ro.build.version.release
property ro.build.version.security_patch

printf '%s\n' '' 'Interpretation: these properties identify firmware only.'
printf '%s\n' 'They DO NOT establish Huawei differentiated-signature support.'
printf '%s\n' 'Prior v0.4 independent diagnostics (for this installation) reported'
printf '%s\n' 'checkSignatures(GMS, Play) = NO_MATCH (-3).'
printf '%s\n' 'Do not modify or patch system apps based on this output.'
