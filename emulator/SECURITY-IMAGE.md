# A security console guest

A console-only Alpine **x86** build carrying a security toolset, for the v86 engine. This
documents what it is, how to build and install it, and — plainly — where it does and does not
work today.

## Why Alpine x86, not Kali

Checked against the live indexes, not from memory:

- **Kali dropped i386.** The current image directory has only `amd64` and `arm64`, and the
  `binary-i386` package index is empty. v86 is a 32-bit x86 emulator, so there is no 32-bit Kali
  to boot.
- **Kali amd64** would need the TinyEMU x86_64 engine, which is not built (see `BUILDING.md`),
  more RAM than a mobile WebView will give a guest, and an image well past 8GB.
- **Alpine still ships x86**, and every tool below is packaged for it (`sqlmap` comes from PyPI,
  vendored at build time).

## The toolset

`nmap` (+ `ncat`, scripts), `masscan`, `tcpdump`, `tshark`/`termshark`, `hydra`, `sqlmap`,
`nikto`, `socat`, `netcat`, `radare2`, `aircrack-ng`, `bind-tools`, `whois`, plus `python3`,
`git`, `vim`, `strace`, `binutils`.

Deliberately **not** included: `hashcat` (CPU-only cracking on an emulated core is theatre).
`aircrack-ng` is here for reading captures taken elsewhere — an emulated NE2000 cannot enter
monitor mode, so it never captures a handshake itself.

## Build and install

```bash
./emulator/build-security-image.sh        # needs Docker with 32-bit emulation
./emulator/install-security-image.sh       # pushes to a connected Android device
```

The build runs a 32-bit Alpine container (so it is slow under emulation on Apple Silicon),
assembles a rootfs, and writes three files to `build/security-image/`: `vmlinuz-lts`,
`initramfs-lts`, `rootfs.ext4`. Verify 32-bit emulation first:

```bash
docker run --rm --platform linux/386 alpine:3.21 uname -m   # i686
# no Docker Desktop? colima start --cpu 4 --memory 10
```

The image is **not bundled** — it is far too large for an APK, which is exactly what the
sideload path is for. The catalog entry `alpine-x86-security` stays dimmed until all three files
are present, then it lights up as bootable. On iOS, drop the same three files under
`Documents/draugr/sideload/alpine-x86-security/` through the Files app or iTunes File Sharing.

## Where this actually runs

This is the honest part. The image builds correctly — every tool above is present in the rootfs,
verified with `debugfs` — and the sideload promotion works: the catalog row goes bootable once
the files land. **But it does not yet reach a shell inside v86 on the phone tested** (Galaxy
A34), and the reason is a v86 memory limit in a mobile WebView, not the image:

| Guest RAM | Result on the device |
|---|---|
| 512MB, 256MB | v86 fails to allocate the guest memory: `RangeError: Invalid typed array length`. A single ArrayBuffer that large is refused by this WebView. |
| 128MB | Memory allocates, but Alpine's stock initramfs is ~85MB (it bundles `/lib/firmware` and the whole base module set) and overflows the guest: `offset is out of bounds`. |

So the path to a working shell on-device is a **slim initramfs** — ata + ext4 only, no
firmware — small enough to load beside a 128MB guest. Getting `mkinitfs` down to a few MB was
not completed here; the build currently produces the stock ~85MB initramfs.

On the **desktop** (a normal browser tab serving the same three files, or a 256MB+ v86 on a
machine that allocates it) the guest boots to a shell normally. FreeDOS at 64MB and the other
small guests are unaffected — this ceiling only bites the larger x86 guests, which includes the
existing `alpine-x86-x11` (512MB) and `reactos` (512MB) catalog entries.

## Networking

The guest has **no network** unless a relay is configured — see `NETWORK-RELAY.md`. Without one
the NE2000 has no peer, DHCP times out, and only `127.0.0.1` answers. A relay changes that, and
changes the app's no-outbound-requests guarantee with it.

## Use it responsibly

Scan and probe only what you are authorised to. This is a learning sandbox, not a licence.
