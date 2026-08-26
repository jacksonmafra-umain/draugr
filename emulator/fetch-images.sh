#!/usr/bin/env bash
# Fetches guest disk images into composeResources so they are packaged with the app.
#
# Images are deliberately not committed: they are large, and keeping them out of the tree is
# what keeps the repository free of anything with an awkward licence. Fetching happens here,
# at build time, against pinned SHA-256 sums. The app itself never makes a network request.
#
# Nothing Microsoft derived is listed or ever will be. Windows 95 and Windows 2000 are marked
# `bundled = false` in catalog.json and go through the sideload path instead.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$root/composeApp/src/commonMain/composeResources/files/emulator/images"

say() { printf '>> %s\n' "$1"; }

# name|relative path|url|sha256  (empty sha means "record it on first fetch")
IMAGES=(
  "freedos|freedos/freedos722.img|https://copy.sh/v86/images/freedos722.img|6c7b3c62823d538e20797fecae6dc55d2572455791a573a7de2cdba0b583f6be"
)

want="${1:-all}"
mkdir -p "$dest"

for row in "${IMAGES[@]}"; do
  IFS='|' read -r name path url sha <<<"$row"
  if [ "$want" != "all" ] && [ "$want" != "$name" ]; then
    continue
  fi

  target="$dest/$path"
  mkdir -p "$(dirname "$target")"

  if [ -f "$target" ]; then
    say "$name already present, verifying"
  else
    say "fetching $name"
    curl -fSL --progress-bar -o "$target" "$url"
  fi

  actual="$(shasum -a 256 "$target" | cut -d' ' -f1)"
  if [ -z "$sha" ]; then
    say "$name sha256 = $actual  (paste this into IMAGES to pin it)"
  elif [ "$actual" != "$sha" ]; then
    printf 'checksum mismatch for %s\n  expected %s\n  actual   %s\n' "$name" "$sha" "$actual" >&2
    printf 'refusing to keep an unpinned image; delete it and re-check the source.\n' >&2
    exit 1
  else
    say "$name verified"
  fi
done

say "images live in $dest and are ignored by git"
