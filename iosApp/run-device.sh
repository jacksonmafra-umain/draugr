#!/usr/bin/env bash
# Builds, signs, installs and launches on a physical iPhone.
#
# Signing is the whole difficulty here. Simulator builds need no team at all, so an empty
# TEAM_ID goes unnoticed until the first device build fails with "No Account for Team". The team
# must be one Xcode has an account for, not merely one with a certificate in the keychain.
#
# Usage:
#   ./iosApp/run-device.sh                 # first device that actually builds
#   ./iosApp/run-device.sh "Qa iPhone 13"  # restrict to devices matching a name fragment
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
filter="${1:-}"
build_dir="${DRAUGR_DEVICE_BUILD_DIR:-$root/build/ios-device}"

say() { printf '>> %s\n' "$1"; }

# Neither tool can be trusted on its own about whether a device is usable:
#
#   devicectl list devices        keeps listing a dropped wireless pairing as `available (paired)`
#   xcodebuild -showdestinations  can report a device with no `error:` and then fail the build
#                                 with "developer disk image could not be mounted"
#
# Only attempting the build settles it, so every candidate is tried in turn.
say "looking for a device"
candidates=""
for attempt in 1 2 3 4 5 6; do
  candidates="$(
    xcodebuild -project "$root/iosApp/iosApp.xcodeproj" -scheme iosApp -showdestinations 2>/dev/null |
      grep 'platform:iOS,' |
      grep -v 'placeholder' |
      { [ -n "$filter" ] && grep -i -- "$filter" || cat; }
  )"
  # Devices with no error first: they are the likeliest to work.
  candidates="$(printf '%s\n' "$candidates" | grep -v 'error:'; printf '%s\n' "$candidates" | grep 'error:' || true)"
  candidates="$(printf '%s\n' "$candidates" | sed '/^$/d')"
  [ -n "$candidates" ] && break
  [ "$attempt" -lt 6 ] && sleep 5
done

if [ -z "$candidates" ]; then
  cat >&2 <<MSG
No iPhone is paired${filter:+ matching "$filter"}.

  - Connect it by cable, or put it on the same network as this Mac
  - Unlock it and trust this computer
  - Settings > Privacy & Security > Developer Mode must be on

$(xcrun devicectl list devices 2>/dev/null | tail -n +2)
MSG
  exit 1
fi

app=""
identifier=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  udid="$(printf '%s' "$line" | sed -E 's/.*id:([^,}]+).*/\1/' | tr -d ' ')"
  name="$(printf '%s' "$line" | sed -E 's/.*name:([^,}]*).*/\1/' | sed -E 's/[[:space:]]+$//')"

  say "trying $name"
  if ! xcodebuild \
    -project "$root/iosApp/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Debug \
    -destination "platform=iOS,id=$udid" \
    -destination-timeout 30 \
    -derivedDataPath "$build_dir" \
    -allowProvisioningUpdates \
    build >"$build_dir.log" 2>&1
  then
    say "$name did not work, see $build_dir.log"
    continue
  fi

  identifier="$(
    xcrun devicectl list devices 2>/dev/null |
      grep -F "$name" |
      awk '{for (i=1;i<=NF;i++) if ($i ~ /coredevice\.local$/) print $(i+1)}' |
      head -1
  )"
  if [ -z "$identifier" ]; then
    say "$name built but devicectl does not list it for install"
    continue
  fi
  say "built for $name"
  break
done <<EOF_CANDIDATES
$candidates
EOF_CANDIDATES

if [ -z "$identifier" ]; then
  printf 'Every candidate device failed. Last build log: %s\n' "$build_dir.log" >&2
  exit 1
fi

app="$build_dir/Build/Products/Debug-iphoneos/DRAUGR.app"
say "installing $app"
xcrun devicectl device install app --device "$identifier" "$app" >/dev/null

say "launching"
xcrun devicectl device process launch --device "$identifier" com.umain.draugr

cat <<'MSG'

Signed with a free personal team? The app stops launching after seven days. Re-run this script
to sign it again.
MSG
