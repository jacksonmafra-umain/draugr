# Building the emulator payload

Everything the WebView loads lives in
`composeApp/src/commonMain/composeResources/files/emulator/`, so it is served by the embedded
asset server over HTTP and never over `file://`.

```
emulator/                     scripts and notes (this directory)
composeResources/files/emulator/
├── host.html                 the page the WebView loads
├── bridge.js                 window.DRAUGR, the only interface Kotlin talks to
├── isolation-check.html      host self test page
├── bridge-probe.js           host self test script
├── v86/                      libv86.js, v86.wasm, v86-fallback.wasm   (committed)
├── bios/                     seabios.bin, vgabios.bin                 (committed)
├── tinyemu/                  tinyemu64.js, tinyemu64.wasm            (built locally)
├── tinyemu-shim.js           engine B wrapper, registers itself
└── images/                   guest disk images                        (fetched, git-ignored)
```

The app makes no network requests at runtime. Both scripts below run at development time only.

## Cross-origin isolation: what actually works

| Host | `crossOriginIsolated` | `SharedArrayBuffer` | Threaded WASM |
|---|---|---|---|
| iOS `WKWebView` | true | yes | yes |
| Android `WebView` (Chromium 151) | **false** | **no** | **no** |

The server sends `Cross-Origin-Opener-Policy: same-origin`,
`Cross-Origin-Embedder-Policy: require-corp` and `Cross-Origin-Resource-Policy: same-origin`
on every response, and the page is a secure context on `http://127.0.0.1`. iOS grants
isolation on that basis. Android WebView does not, and no Chromium switch tried
(`--enable-blink-features=SharedArrayBuffer`, `--enable-features=SharedArrayBuffer`,
`--site-per-process`) changes it. Verify on any host by opening `HOST SELF TEST` from the
catalog header.

**Consequence: the default TinyEMU build must be single-threaded.** v86 is single-threaded
anyway and runs on both platforms unchanged.

## Engine A — v86 (BSD-2-Clause)

Vendored, committed, and pinned. To move to a newer release:

```bash
# edit V86_NPM_VERSION / V86_NPM_SHA256 / V86_COMMIT at the top first
./emulator/vendor.sh
shasum -a 256 composeApp/src/commonMain/composeResources/files/emulator/v86/* \
              composeApp/src/commonMain/composeResources/files/emulator/bios/* \
  > emulator/CHECKSUMS.txt
```

Currently pinned to npm `v86@0.5.441` and BIOS blobs from v86 commit `847e34d5`.
`shasum -a 256 -c emulator/CHECKSUMS.txt` must pass before committing.

### Building v86 from source instead

```bash
git clone https://github.com/copy/v86
cd v86
# needs rustup with the wasm32-unknown-unknown target, clang, and make
rustup target add wasm32-unknown-unknown
make build/v86.wasm
make build/libv86.js
```

Copy `build/v86.wasm` and `build/libv86.js` into `composeResources/files/emulator/v86/` and
refresh `CHECKSUMS.txt`. BIOS blobs come from the repository's `bios/` directory;
`bios/fetch-and-build-seabios.sh` rebuilds them from SeaBIOS source if you would rather not
trust the prebuilt ones. SeaBIOS is LGPLv3 — its licence text is in
`third_party/SEABIOS-COPYING.LESSER.txt`.

## Engine B — TinyEMU compiled to WASM (MIT)

TinyEMU's source is MIT. **The jslinux.com JavaScript build and the images hosted there are
not redistributable.** Do not scrape them, do not bundle them. Compile from source.

### Prerequisites

```bash
git clone https://github.com/emscripten-core/emsdk ~/emsdk
cd ~/emsdk && ./emsdk install latest && ./emsdk activate latest
source ~/emsdk/emsdk_env.sh
emcc --version        # 4.x or newer
```

### Build

```bash
./emulator/build-tinyemu.sh
```

The script fetches `tinyemu-2019-12-21.tar.gz` from bellard.org, verifies it against a pinned
SHA-256, compiles the riscv64 target, and writes `tinyemu64.js` plus `tinyemu64.wasm` into
`composeResources/files/emulator/tinyemu/`. Record the hashes in `emulator/CHECKSUMS.txt`
afterwards so a normal build stays verifiable.

The flags that matter:

```
-s WASM=1
-s ALLOW_MEMORY_GROWTH=1
-s ASYNCIFY=1
-s ASYNCIFY_STACK_SIZE=32768
-s INITIAL_MEMORY=67108864
-s STACK_SIZE=1048576
-s MODULARIZE=1 -s EXPORT_NAME=TinyEmu64
-s NO_EXIT_RUNTIME=1 -s NO_FILESYSTEM=1
--js-library emulator/tinyemu/draugr_lib.js
```

`ASYNCIFY` is what lets the C main loop block on a synchronous block-device read while the
JavaScript event loop keeps turning. Without it the network block device deadlocks.

Add `-pthread -s PTHREAD_POOL_SIZE=4 -s SHARED_MEMORY=1` **only** for an iOS-only build. That
variant cannot run on Android for the isolation reason above, which is why it is not the default.

### What upstream provides, and what this repository adds

TinyEMU already compiles to the web: `jsemu.c` is its browser entry point and exports
`vm_start`, `console_queue_char`, `display_key_event` and friends. It fetches its config, BIOS,
kernel and disk blocks with `emscripten_async_wget3_data`, which is plain Emscripten — that is
exactly what turns a guest's block reads into HTTP Range requests against the local asset
server, with no custom block device needed.

What upstream does *not* ship in the source archive is `js/lib.js`, the Emscripten
`--js-library` holding the five hooks `jsemu.c` calls back into. That file belongs to the
jslinux.com build, which is not redistributable. `emulator/tinyemu/draugr_lib.js` provides them:

| Hook | Purpose |
|---|---|
| `console_write` | guest serial output, forwarded to the Compose boot log |
| `console_get_size` | the 80x25 character grid the guest is told it has |
| `fb_refresh` | framebuffer blits, drawn onto the host canvas |
| `net_recv_packet` | ignored: the app is loopback only |
| `fs_wget_update_downloading` | drives the `FETCHING` state in the HUD |

`composeResources/files/emulator/tinyemu-shim.js` wraps those into an engine object and calls
`window.DRAUGR_REGISTER_ENGINE('tinyemu', ...)`, so `bridge.js` picks it up without knowing it
exists and `commonMain` stays engine-agnostic. `bridge.js` loads the shim on first use and, when
the artifacts are missing, boots to a plain `engine not built: run emulator/build-tinyemu.sh`.

### The config file

TinyEMU reads a config file over HTTP rather than taking arguments. The app generates one per
machine from its `MachineSpec` and serves it from memory at `config/<id>.cfg`, with every asset
written as an absolute URL against the same server. See `TinyEmuConfig` and its tests.

### Known gaps

- **Snapshots.** Upstream TinyEMU has no state serialiser, so the shim reports pause, resume and
  snapshot as unsupported rather than pretending. v86 machines keep full snapshot support.
- **x86_64.** `x86_cpu.c` and `x86_machine.c` are only wired into upstream's native target. The
  `alpine-x86_64` catalog entry needs a second `build_target` once those compile clean under
  `emcc`.

## Guest images

```bash
./emulator/fetch-images.sh          # everything
./emulator/fetch-images.sh freedos  # one machine
```

Each entry is pinned by SHA-256 and the script refuses a mismatch. Images land in
`composeResources/files/emulator/images/` which `.gitignore` excludes, so they are packaged
into the app when present but never committed.

Windows 95 and Windows 2000 are absent by design. They are `bundled = false` in
`catalog.json`, appear dimmed in the catalog as `[SIDELOAD REQUIRED]`, and take a user-supplied
image through the sideload path.
