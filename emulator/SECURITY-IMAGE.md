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

## Where this runs, and the memory ceiling

The image boots. On the phone tested (Galaxy A34) the guest reaches Alpine userspace: the kernel
loads, a small hand-rolled initramfs brings up the ata + ext4 drivers, mounts `/dev/sda`,
`switch_root`s into the rootfs, and OpenRC starts. Verified from the serial log.

Two things had to be right, and both were the reason earlier attempts failed:

| Problem | Fix |
|---|---|
| v86 could not allocate the guest RAM: `RangeError: Invalid typed array length` at 256MB and above. A mobile WebView refuses a single ArrayBuffer that large. | The machine runs at **128MB**, the largest single buffer this WebView reliably allocates. |
| Alpine's stock `mkinitfs` initramfs is ~85MB — it bundles `/lib/firmware` and the whole base module set — and overflowed the 128MB guest. | A **hand-rolled ~5MB initramfs**: busybox, the musl loader, and only the ata/scsi/ext4 modules, with an init that mounts root and `switch_root`s. The musl loader matters — without it the kernel cannot even exec `/init` (`error -2`). |

The 128MB ceiling is a property of v86 in a mobile WebView, not of this image, and it also
constrains the existing 512MB `alpine-x86-x11` and `reactos` catalog entries. On a desktop
browser, where a 256MB+ buffer allocates, the guest has more room.

The `Invalid ELF header magic` lines in the boot log are cosmetic: the kernel probes a few
non-ELF files while loading modules and moves on.

## Networking

The guest has **no network** unless a relay is configured — see `NETWORK-RELAY.md`. Without one
the NE2000 has no peer, DHCP times out, and only `127.0.0.1` answers. A relay changes that, and
changes the app's no-outbound-requests guarantee with it.

## Use it responsibly

Scan and probe only what you are authorised to. This is a learning sandbox, not a licence.
