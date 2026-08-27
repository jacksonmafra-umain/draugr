#!/usr/bin/env bash
# Pushes the built Alpine x86 security image into the app's sideload storage on a device.
#
# The image is three files (kernel, initramfs, rootfs), so it does not go through the single-file
# document picker. On Android they are pushed straight into app storage; on iOS use the Files app
# or iTunes File Sharing to drop the same three files under
# Documents/draugr/sideload/alpine-x86-net/.
#
# Build it first:  ./emulator/build-net-image.sh
set -euo pipefail

MACHINE_ID="alpine-x86-net"
APP_ID="com.umain.draugr"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="${DRAUGR_NET_OUT:-$root/build/net-image}"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

say() { printf '>> %s\n' "$1"; }

for f in vmlinuz-lts initramfs-lts rootfs.ext4; do
  if [ ! -f "$src/$f" ]; then
    printf 'Missing %s. Run ./emulator/build-net-image.sh first.\n' "$src/$f" >&2
    exit 1
  fi
done

serial="${1:-}"
if [ -z "$serial" ]; then
  serial="$("$adb" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi
if [ -z "$serial" ]; then
  echo "No Android device. Pass a serial, or use the Files app on iOS (see the header)." >&2
  exit 1
fi
say "device: $serial"

# run-as reaches app-private storage on a debuggable build without root.
dest="files/draugr/sideload/$MACHINE_ID"
"$adb" -s "$serial" shell run-as "$APP_ID" mkdir -p "$dest"
for f in vmlinuz-lts initramfs-lts rootfs.ext4; do
  say "pushing $f"
  # A two-step copy: push to a world-readable tmp, then run-as cat into app storage, because
  # `adb push` cannot target another app's private directory directly.
  "$adb" -s "$serial" push "$src/$f" "/data/local/tmp/draugr-$f" >/dev/null
  "$adb" -s "$serial" shell "run-as $APP_ID sh -c 'cat /data/local/tmp/draugr-$f > $dest/$f'"
  "$adb" -s "$serial" shell "rm -f /data/local/tmp/draugr-$f"
done

say "installed. Boot \"$MACHINE_ID\" from the catalog."
