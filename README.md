# draugr

> **draugr** (Old Norse): the undead that walk again from the howe. Dead operating systems,
> resurrected in your pocket.

A Compose Multiplatform app that boots emulated machines on-device. The catalog, HUD, keyboard
and boot log are shared Compose; the emulator is a WebAssembly payload running inside a platform
web view.

**Proof of concept. Local only. Not for store distribution.**

## Why it is built this way

The app cannot emulate anything itself.

iOS forbids `fork`, `posix_spawn` and W→X `mprotect`, so a native emulator would be
interpreted-only and unusably slow. But **`WKWebView` runs out-of-process in WebContent, which
holds the `dynamic-codesigning` entitlement** — WebKit is allowed to JIT. WebAssembly inside a
web view therefore runs *compiled*, not interpreted. That single fact is why the project is
viable on iOS at all. Android's Chromium `WebView` gives the equivalent.

So: **the emulator is a WASM payload, the web view is the CPU host, and Compose is the console.**

```
┌──────────────────────── Compose Multiplatform (commonMain) ─────────────────────────┐
│  CatalogScreen · MachineDetailScreen · VmScreen · SnapshotScreen · SettingsScreen   │
│  BracketPanel · GlitchText · ScanlineOverlay · TerminalKeyboard · StatBar           │
│                                                                                     │
│  VmController ── VmState/VmEvent ── BridgeProtocol ── Scancodes ── CatalogRepository │
└───────────┬─────────────────────────────────────────────────────┬───────────────────┘
            │ expect/actual                                       │ expect/actual
   ┌────────┴────────┐                                   ┌────────┴─────────┐
   │  AssetServer    │  Ktor CIO on 127.0.0.1            │  VmBridge        │
   │  ephemeral port │  COOP/COEP/CORP · Range · token   │  VmSurface       │
   └────────┬────────┘                                   └────────┬─────────┘
            │ http://127.0.0.1:PORT/TOKEN/…                       │ JS ⇄ Kotlin
            ▼                                                     ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │  host.html + bridge.js        window.DRAUGR (one contract, two engines) │
   │      ├── v86            libv86.js + v86.wasm      (vendored, pinned)    │
   │      └── tinyemu-shim   tinyemu64.js/.wasm        (built locally)       │
   └─────────────────────────────────────────────────────────────────────────┘
```

## The embedded server, and why `file://` cannot work

`AssetServer` runs Ktor CIO on `127.0.0.1` with an ephemeral port, mounted under a random
16-byte token so nothing else on the loopback interface can enumerate it. Three reasons it
cannot be replaced with `file://`:

1. `SharedArrayBuffer` requires cross-origin isolation — `COOP: same-origin` plus
   `COEP: require-corp`. A `file://` origin is never cross-origin isolated.
2. `WKWebView` blocks XHR and `fetch` from `file://` origins outright.
3. Disk images need HTTP **Range** so the guest streams 512-byte blocks on demand. On iOS this
   is not an optimisation: without it WebContent is jetsam-killed before boot completes.

Every response carries the three isolation headers plus `Accept-Ranges: bytes`. Range handling
is written out rather than delegated: single `bytes=` spans, open-ended and suffix forms, `206`
with a correct `Content-Range`, `416` with `bytes */size` when unsatisfiable.

### Cross-origin isolation in practice

| Host | `crossOriginIsolated` | `SharedArrayBuffer` | Threaded WASM |
|---|---|---|---|
| iOS `WKWebView` | **true** | yes | yes |
| Android `WebView` (Chromium 151) | **false** | no | no |

Android is not at parity here, and it is not a configuration mistake. The headers arrive intact
and the page is a secure context on `http://127.0.0.1`; `crossOriginIsolated` still reports
`false`, and no Chromium switch tried (`--enable-blink-features=SharedArrayBuffer`,
`--enable-features=SharedArrayBuffer`, `--site-per-process`) changes it. **Consequence: the
default TinyEMU build is single-threaded** (`ASYNCIFY`, no `-pthread`). v86 is single-threaded
anyway and runs identically on both platforms.

Check any host for yourself: tap the catalog header, then `HOST SELF TEST`.

## Machines

Ten entries in `catalog.json`. Bundled images are fetched at build time by
`emulator/fetch-images.sh` against pinned SHA-256 sums; the app itself never fetches anything.

| Machine | CPU | UI | Engine | RAM | Bundled | Licence |
|---|---|---|---|---|---|---|
| Alpine Linux 3.23 | x86_64 | Console | TinyEMU | 512MB | yes | Alpine: MIT/GPL, per-package |
| Alpine Linux 3.12 | x86 | Console | v86 | 256MB | yes | Alpine: MIT/GPL, per-package |
| Alpine Linux 3.12 | x86 | X Window | v86 | 512MB | yes | Alpine: MIT/GPL, per-package |
| Buildroot | riscv64 | Console | TinyEMU | 256MB | yes | GPL-2.0 kernel + BusyBox |
| Buildroot | riscv64 | X Window | TinyEMU | 512MB | yes | GPL-2.0 kernel + BusyBox |
| Fedora 33 | riscv64 | Console | TinyEMU | 1024MB | yes | Fedora: MIT/GPL, per-package |
| FreeDOS | x86 | VGA text | v86 | 64MB | yes | GPL-2.0 |
| ReactOS | x86 | Graphical | v86 | 512MB | yes | GPL-2.0 |
| Windows 95 | x86 | Graphical | v86 | 64MB | **no** | Proprietary — sideload only |
| Windows 2000 | x86 | Graphical | v86 | 512MB | **no** | Proprietary — sideload only |

Emulator payload: **v86** is BSD-2-Clause, **SeaBIOS** and **VGABIOS** are LGPLv3, **TinyEMU**
is MIT, **Courier Prime** is SIL OFL. Licence texts are in `third_party/`.

**No Microsoft-derived image is in this repository, and none ever will be.** The two Windows
entries are `bundled = false`, render dimmed as `[SIDELOAD REQUIRED]`, and take a user-supplied
image through the sideload path. Drop one in over iTunes File Sharing or pick it with the
document picker, and the catalog row becomes bootable.

## Networking: none

The app makes **no outbound network requests**. The only peer is the embedded server on
`127.0.0.1`. Android declares `android.permission.INTERNET` because binding a loopback socket
requires it, and its network security config permits cleartext to `127.0.0.1` and `localhost`
only. iOS declares `NSAllowsLocalNetworking` and nothing else. It runs airplane-mode clean.

Development-time scripts (`emulator/vendor.sh`, `emulator/fetch-images.sh`,
`emulator/build-tinyemu.sh`) do use the network. The app does not.

## Building

```bash
./gradlew :composeApp:assembleDebug          # Android APK
./gradlew :composeApp:testDebugUnitTest      # unit tests
./emulator/fetch-images.sh                   # guest images (pinned, git-ignored)
open iosApp/iosApp.xcodeproj                 # iOS, or:
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build
```

Toolchain: Gradle 9.7.1, Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.3.2, JDK 17+,
compileSdk 37, minSdk 26. Compose Multiplatform 1.12 no longer publishes `iosX64`, so the Intel
simulator is out; device and Apple Silicon simulator only.

Engine B has to be compiled locally — see `emulator/BUILDING.md`. Until it is, TinyEMU machines
boot to `engine not built: run emulator/build-tinyemu.sh` rather than failing quietly.

## Jetsam: the number one failure mode on iOS

WebContent is a separate process and iOS kills it aggressively under memory pressure, especially
while backgrounded. What the app does about it:

- **Auto-snapshot on `UIApplicationWillResignActive`**, persisted to disk, resumed on return.
- **`webViewWebContentProcessDidTerminate`** becomes `Suspended(HOST_TERMINATED)` and the screen
  offers a restore instead of the app dying with the page.
- **`memMb` is clamped to 512 on iOS.** Asking for more does not degrade gracefully; the whole
  web content process is killed mid-boot. The detail screen warns for machines above the budget.

### Troubleshooting

| Symptom | Cause | What to do |
|---|---|---|
| Guest dies silently on iOS, app survives | jetsam killed WebContent | Restore from the offered snapshot; pick a machine with less RAM |
| `Fedora 33` never finishes booting on device | 1024MB, above the iOS budget | Use it on Android, or on a simulator with room |
| `crossOriginIsolated = false` in the self test | Android WebView | Expected. Use single-threaded engine builds |
| Boot log silent, screen black | image missing or a 404 | Run `emulator/fetch-images.sh`; check the log for the failing URL |
| `SocketException: Operation not permitted` | `INTERNET` permission missing | Already declared; check a stripped manifest in a fork |
| Guest text clipped after rotation | the page refits through a `ResizeObserver` | If it persists, rotate again or reopen the machine |

## Layout

```
composeApp/src/commonMain/kotlin/com/umain/draugr/
├── catalog/    MachineSpec, CatalogRepository, filters
├── vm/         VmController, VmState/VmEvent, VmBridge (expect), BridgeProtocol, TinyEmuConfig
├── server/     AssetServer (expect), routing, Range handling, asset providers
├── storage/    SnapshotStore, SideloadStore, SettingsStore, platform roots (expect)
├── input/      PC scancodes and Linux keycodes
├── platform/   HostLifecycle (expect), platform memory ceiling
└── ui/         theme, components, screens
composeApp/src/androidMain/   WebView + @JavascriptInterface, activity lifecycle
composeApp/src/iosMain/       WKWebView + WKScriptMessageHandler, notification lifecycle
emulator/                     vendor and build scripts, BUILDING.md, checksums
iosApp/                       Xcode project
```
