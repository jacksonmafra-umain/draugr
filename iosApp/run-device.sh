#!/usr/bin/env bash
# Builds, signs, installs and launches on a physical iPhone.
#
# Signing is the whole difficulty here. Simulator builds need no team at all, so an empty
# TEAM_ID goes unnoticed until the first device build fails with "No Account for Team". The team
# must be one Xcode has an account for, not merely one with a certificate in the keychain.
#
# Usage:
#   ./iosApp/run-device.sh                 # first available paired device
#   ./iosApp/run-device.sh "Qa iPhone 13"  # by name fragment
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
filter="${1:-}"
build_dir="${DRAUGR_DEVICE_BUILD_DIR:-$root/build/ios-device}"

say() { printf '>> %s\n' "$1"; }

# devicectl reports a device as unavailable for a few seconds after any operation on it, so a
# single listing is not evidence that nothing is connected.
devices=""
for attempt in 1 2 3 4 5 6; do
  devices="$(xcrun devicectl list devices 2>/dev/null | grep 'available (paired)' || true)"
  if [ -n "$filter" ]; then
    matched="$(printf '%s\n' "$devices" | grep -i -- "$filter" || true)"
  else
    matched="$devices"
  fi
  [ -n "$matched" ] && break
  [ "$attempt" -lt 6 ] && sleep 5
done
devices="$matched"

if [ -z "$devices" ]; then
  cat >&2 <<'MSG'
No paired iPhone is available${filter:+ matching "$filter"}.

  - Connect it by cable, or put it on the same network as this Mac
  - Unlock it and trust this computer
  - Settings > Privacy & Security > Developer Mode must be on

`xcrun devicectl list devices` shows the current state. A device listed as `unavailable` is
paired but not reachable right now, which is the usual case for a wireless pairing that dropped.
MSG
  exit 1
fi

# Columns are separated by runs of spaces: "name   hostname   identifier   state   model".
row="$(printf '%s\n' "$devices" | head -1)"
identifier="$(printf '%s' "$row" | awk '{for (i=1;i<=NF;i++) if ($i ~ /coredevice\.local$/) print $(i+1)}')"
name="$(printf '%s' "$row" | sed -E 's/[[:space:]]{2,}.*//')"
say "device: $name ($identifier)"

# xcodebuild wants the hardware UDID, which devicectl does not print. `|| true` matters: under
# `set -e` a failing command substitution aborts the script with no message at all.
udid="$(xcrun xctrace list devices 2>/dev/null | grep -m1 -F "$name" | sed -E 's/.*\(([0-9A-Fa-f-]{25,})\).*/\1/' || true)"
if [ -z "$udid" ]; then
  say "falling back to 'generic/platform=iOS'"
  destination="generic/platform=iOS"
else
  destination="platform=iOS,id=$udid"
fi

say "building"
xcodebuild \
  -project "$root/iosApp/iosApp.xcodeproj" \
  -scheme iosApp \
  -configuration Debug \
  -destination "$destination" \
  -derivedDataPath "$build_dir" \
  -allowProvisioningUpdates \
  build >/dev/null

app="$build_dir/Build/Products/Debug-iphoneos/DRAUGR.app"
say "installing $app"
xcrun devicectl device install app --device "$identifier" "$app" >/dev/null

say "launching"
xcrun devicectl device process launch --device "$identifier" com.umain.draugr

cat <<'MSG'

Signed with a free personal team? The app stops launching after seven days. Re-run this script
to sign it again.
MSG
