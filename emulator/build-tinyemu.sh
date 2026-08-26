#!/usr/bin/env bash
# Builds TinyEMU (MIT) to WebAssembly and drops the result into composeResources.
#
# The jslinux.com JavaScript build and the images hosted there are NOT redistributable. This
# compiles from Bellard's source archive, pinned by SHA-256, and bundles nothing from that site.
# Upstream's own js/lib.js is part of the jslinux build rather than the source archive, so
# emulator/tinyemu/draugr_lib.js provides the five JavaScript hooks jsemu.c expects.
#
# Requires the Emscripten SDK on PATH:
#   git clone https://github.com/emscripten-core/emsdk ~/emsdk
#   cd ~/emsdk && ./emsdk install latest && ./emsdk activate latest
#   source ~/emsdk/emsdk_env.sh
set -euo pipefail

TINYEMU_VERSION="2019-12-21"
TINYEMU_URL="https://bellard.org/tinyemu/tinyemu-${TINYEMU_VERSION}.tar.gz"
TINYEMU_SHA256="be8351f2121819b3172fcedce5cb1826fa12c87da1b7ed98f269d3e802a05555"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$root/composeApp/src/commonMain/composeResources/files/emulator/tinyemu"
work="$root/emulator/tinyemu/build"

say() { printf '>> %s\n' "$1"; }

if ! command -v emcc >/dev/null 2>&1; then
  cat >&2 <<'MSG'
emcc not found. Install the Emscripten SDK first:

  git clone https://github.com/emscripten-core/emsdk ~/emsdk
  cd ~/emsdk && ./emsdk install latest && ./emsdk activate latest
  source ~/emsdk/emsdk_env.sh

Then run this script again.
MSG
  exit 1
fi

mkdir -p "$work" "$dest"
src="$work/tinyemu-${TINYEMU_VERSION}"

if [ ! -d "$src" ]; then
  say "fetching TinyEMU ${TINYEMU_VERSION}"
  curl -fsSL -o "$work/tinyemu.tar.gz" "$TINYEMU_URL"
  actual="$(shasum -a 256 "$work/tinyemu.tar.gz" | cut -d' ' -f1)"
  if [ "$actual" != "$TINYEMU_SHA256" ]; then
    printf 'checksum mismatch for the TinyEMU archive\n  expected %s\n  actual   %s\n' \
      "$TINYEMU_SHA256" "$actual" >&2
    exit 1
  fi
  tar -xzf "$work/tinyemu.tar.gz" -C "$work"
fi

install -m 644 "$root/emulator/tinyemu/draugr_lib.js" "$src/draugr_lib.js"
install -m 644 "$src/MIT-LICENSE.txt" "$root/third_party/TINYEMU-MIT-LICENSE.txt"

# Threads are deliberately absent. Android WebView does not grant cross-origin isolation, so
# SharedArrayBuffer is unavailable there and a -pthread build cannot run at all. For an
# iOS-only variant add: -pthread -s PTHREAD_POOL_SIZE=4 -s SHARED_MEMORY=1
CFLAGS=(
  -O3
  -Wall
  -D_FILE_OFFSET_BITS=64
  -D_LARGEFILE_SOURCE
  -fno-strict-aliasing
  -DCONFIG_FS_NET
  -DCONFIG_VERSION="\"${TINYEMU_VERSION}\""
)

SHARED_SOURCES=(
  jsemu.c softfp.c virtio.c fs.c fs_net.c fs_wget.c fs_utils.c
  simplefb.c pci.c json.c block_net.c iomem.c cutils.c aes.c sha256.c
  riscv_machine.c machine.c
)

build_target() {
  local name="$1" xlen="$2"
  say "compiling ${name} (riscv${xlen})"
  local objects=()
  for file in "${SHARED_SOURCES[@]}"; do
    local object="$work/${name}-${file%.c}.o"
    emcc "${CFLAGS[@]}" -I"$src" -DMAX_XLEN="$xlen" -DCONFIG_RISCV_MAX_XLEN="$xlen" \
      -c -o "$object" "$src/$file"
    objects+=("$object")
  done
  local cpu_object="$work/${name}-riscv_cpu.o"
  emcc "${CFLAGS[@]}" -I"$src" -DMAX_XLEN="$xlen" -DCONFIG_RISCV_MAX_XLEN="$xlen" \
    -c -o "$cpu_object" "$src/riscv_cpu.c"
  objects+=("$cpu_object")

  emcc \
    -O3 \
    -o "$dest/${name}.js" \
    "${objects[@]}" \
    -s WASM=1 \
    -s ALLOW_MEMORY_GROWTH=1 \
    -s ASYNCIFY=1 \
    -s ASYNCIFY_STACK_SIZE=32768 \
    -s INITIAL_MEMORY=67108864 \
    -s STACK_SIZE=1048576 \
    -s MODULARIZE=1 \
    -s EXPORT_NAME="TinyEmu${xlen}" \
    -s NO_EXIT_RUNTIME=1 \
    -s NO_FILESYSTEM=1 \
    -s EXPORTED_RUNTIME_METHODS='["ccall","cwrap","HEAPU8","HEAP32"]' \
    -s EXPORTED_FUNCTIONS='["_vm_start","_console_queue_char","_display_key_event","_display_mouse_event","_display_wheel_event","_fs_import_file","_malloc","_free"]' \
    --js-library "$src/draugr_lib.js"
}

build_target "tinyemu64" 64

say "artifacts in $dest"
ls -la "$dest"

cat <<'MSG'

Next steps:
  1. Record the hashes so a normal build stays verifiable:
       shasum -a 256 composeApp/src/commonMain/composeResources/files/emulator/tinyemu/* \
         >> emulator/CHECKSUMS.txt
  2. ./emulator/fetch-images.sh   to pull the riscv64 guests
  3. Boot buildroot-rv64 from the catalog. It goes through the same Kotlin code path as every
     v86 machine; only the JS shim differs.

Not built here:
  x86_64 needs x86_cpu.c and x86_machine.c, which upstream only wires into the native target.
  Add a build_target for it once those compile clean under emcc.
MSG
