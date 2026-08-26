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
├── tinyemu/                  tinyemu.js, tinyemu.wasm, tinyemu-shim.js (built locally)
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

`emulator/build-tinyemu.sh` drives this. The flags that matter:

```bash
emcc \
  -O3 \
  -o tinyemu.js \
  $SOURCES \
  -s ALLOW_MEMORY_GROWTH=1 \
  -s ASYNCIFY=1 \
  -s ASYNCIFY_STACK_SIZE=32768 \
  -s MODULARIZE=1 \
  -s EXPORT_NAME=TinyEmuModule \
  -s EXPORTED_RUNTIME_METHODS='["ccall","cwrap","stringToUTF8","lengthBytesUTF8"]' \
  -s EXPORTED_FUNCTIONS='["_temu_start","_temu_key","_temu_snapshot","_temu_restore","_temu_pause","_temu_resume","_malloc","_free"]' \
  -s INITIAL_MEMORY=67108864 \
  -s STACK_SIZE=1048576 \
  -s FETCH=1 \
  --js-library tinyemu-vfsync.js
```

Add `-pthread -s PTHREAD_POOL_SIZE=4 -s SHARED_MEMORY=1` **only** for an iOS-only build.
That variant cannot run on Android for the isolation reason above, so it is not the default.

`ASYNCIFY` is what lets the C main loop block on a synchronous block-device read while the
JavaScript event loop keeps turning. Without it the VFsync block device deadlocks.

`FETCH=1` plus the VFsync JS library maps the guest's 512-byte block reads onto HTTP Range
requests against the local asset server, which is the whole reason a 1GB Fedora image can boot
on a phone.

### C API the shim expects

```c
int  temu_start(const char *config_json);   /* returns 0 on success              */
void temu_key(int keycode, int pressed);    /* Linux keycodes, not PC scancodes  */
int  temu_snapshot(void **out, size_t *len);
int  temu_restore(const void *data, size_t len);
void temu_pause(void);
void temu_resume(void);
```

`tinyemu-shim.js` wraps those and calls
`window.DRAUGR_REGISTER_ENGINE('tinyemu', TinyEmuEngine)`, so `bridge.js` picks the engine up
without knowing anything about it and `commonMain` stays engine-agnostic.

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
