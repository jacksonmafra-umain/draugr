#!/usr/bin/env bash
# Re-vendors the v86 payload and the BIOS blobs into composeResources.
#
# Everything is pinned: the npm tarball by version and SHA-256, the BIOS files by the v86
# commit they came from. Run this only when deliberately moving to a newer v86; the outputs
# are committed so a normal build needs no network at all.
set -euo pipefail

V86_NPM_VERSION="0.5.441"
V86_NPM_SHA256="c3fd1592d350e903482fbd366f59caa4e773971cf0516d8d3e60f5c0bb6e1f93"
V86_COMMIT="847e34d5499b17b90d2783d5342ddd243c753497"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$root/composeApp/src/commonMain/composeResources/files/emulator"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

say() { printf '>> %s\n' "$1"; }

verify() {
  local file="$1" expected="$2" actual
  actual="$(shasum -a 256 "$file" | cut -d' ' -f1)"
  if [ "$actual" != "$expected" ]; then
    printf 'checksum mismatch for %s\n  expected %s\n  actual   %s\n' "$file" "$expected" "$actual" >&2
    exit 1
  fi
}

say "fetching v86 ${V86_NPM_VERSION} from npm"
curl -fsSL -o "$work/v86.tgz" \
  "https://registry.npmjs.org/v86/-/v86-${V86_NPM_VERSION}.tgz"
verify "$work/v86.tgz" "$V86_NPM_SHA256"

tar -xzf "$work/v86.tgz" -C "$work"
mkdir -p "$dest/v86" "$dest/bios" "$root/third_party"
install -m 644 "$work/package/build/libv86.js" "$dest/v86/libv86.js"
install -m 644 "$work/package/build/v86.wasm" "$dest/v86/v86.wasm"
install -m 644 "$work/package/build/v86-fallback.wasm" "$dest/v86/v86-fallback.wasm"
install -m 644 "$work/package/LICENSE" "$root/third_party/V86-LICENSE.txt"

say "fetching BIOS blobs from v86 @ ${V86_COMMIT:0:12}"
for f in seabios.bin vgabios.bin; do
  curl -fsSL -o "$dest/bios/$f" \
    "https://raw.githubusercontent.com/copy/v86/${V86_COMMIT}/bios/$f"
done
curl -fsSL -o "$root/third_party/SEABIOS-COPYING.LESSER.txt" \
  "https://raw.githubusercontent.com/copy/v86/${V86_COMMIT}/bios/COPYING.LESSER"

say "verifying against emulator/CHECKSUMS.txt"
( cd "$root" && shasum -a 256 -c emulator/CHECKSUMS.txt )

say "done"
