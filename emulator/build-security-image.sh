#!/usr/bin/env bash
# Builds a console-only Alpine x86 guest with a security toolset, for the v86 engine.
#
# Why Alpine x86 and not Kali: Kali stopped publishing i386 images, and its i386 package index is
# empty, so there is no 32-bit Kali to boot. v86 is a 32-bit x86 emulator. Kali amd64 would need
# the TinyEMU x86_64 engine (not built, see BUILDING.md), more RAM than iOS allows a guest, and an
# image past 8GB. Alpine still ships x86, and the tools are all packaged for it.
#
# Read emulator/SECURITY-IMAGE.md before using any of it against anything.
#
# Requires Docker with 32-bit emulation. Verify with:
#   docker run --rm --platform linux/386 alpine:3.21 uname -m     # prints i686
set -euo pipefail

ALPINE_VERSION="3.21"
IMAGE_SIZE="${DRAUGR_IMAGE_SIZE:-1400M}"
MACHINE_ID="alpine-x86-security"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${DRAUGR_SECURITY_OUT:-$root/build/security-image}"

say() { printf '>> %s\n' "$1"; }

if ! docker info >/dev/null 2>&1; then
  cat >&2 <<'MSG'
Docker is not running. Either Docker Desktop, or:

  colima start --cpu 4 --memory 6

32-bit emulation has to work. Check it with:

  docker run --rm --platform linux/386 alpine:3.21 uname -m      # i686
MSG
  exit 1
fi

mkdir -p "$out"

# The toolset. Deliberately no hashcat: CPU-only cracking on an emulated 486-class core is
# theatre. aircrack-ng is here for reading captures you took elsewhere — an emulated NE2000
# cannot go into monitor mode, so it will never capture a handshake itself.
# Checked against the v3.21 x86 APKINDEX, not from memory: sqlmap is not packaged for Alpine
# at all (it comes from PyPI below), and the SSH client is in `openssh` rather than
# `openssh-client`.
PACKAGES="
alpine-base linux-lts openrc busybox-openrc util-linux e2fsprogs e2fsprogs-extra
nmap nmap-scripts nmap-ncat masscan tcpdump tshark termshark
hydra nikto socat netcat-openbsd
radare2 aircrack-ng bind-tools whois
openssh curl wget git jq
python3 py3-pip vim less strace file binutils
"

say "building rootfs in a 32-bit container (this is emulated, so it is slow)"
docker run --rm --platform linux/386 \
  -v "$out:/out" \
  alpine:"$ALPINE_VERSION" \
  sh -euc '
    apk add --no-cache e2fsprogs cpio kmod gzip >/dev/null

    ROOT=/rootfs
    mkdir -p "$ROOT/etc/apk"

    # The new root starts with no repositories at all, so every package "does not exist" until
    # this file is written. community matters too: half the toolset lives there.
    cat > "$ROOT/etc/apk/repositories" <<REPOS
https://dl-cdn.alpinelinux.org/alpine/v'"$ALPINE_VERSION"'/main
https://dl-cdn.alpinelinux.org/alpine/v'"$ALPINE_VERSION"'/community
REPOS
    cp "$ROOT/etc/apk/repositories" /etc/apk/repositories

    apk add --root "$ROOT" --initdb --no-cache --allow-untrusted \
      --keys-dir /etc/apk/keys \
      '"$(echo $PACKAGES | tr -s '[:space:]' ' ')"'

    # sqlmap ships on PyPI, not in Alpine. Vendored at build time so the guest needs no
    # network to have it.
    apk add --no-cache py3-pip >/dev/null
    pip install --no-cache-dir --break-system-packages --root "$ROOT" --prefix /usr sqlmap \
      >/dev/null 2>&1 && echo ">> sqlmap vendored from PyPI" \
      || echo ">> sqlmap could not be vendored, continuing without it"

    # Serial console: the app reads the guest through ttyS0 and shows it in the boot log.
    cat > "$ROOT/etc/inittab" <<INITTAB
::sysinit:/sbin/openrc sysinit
::sysinit:/sbin/openrc boot
::wait:/sbin/openrc default
ttyS0::respawn:/sbin/getty -L 115200 ttyS0 vt100
tty1::respawn:/sbin/getty 38400 tty1
::shutdown:/sbin/openrc shutdown
INITTAB

    # Root with no password. This guest is a sandbox with no network unless a relay is
    # configured, and typing a password through an on-screen scancode keyboard is misery.
    sed -i "s|^root:[^:]*:|root::|" "$ROOT/etc/shadow"

    echo draugr-sec > "$ROOT/etc/hostname"
    cat > "$ROOT/etc/fstab" <<FSTAB
/dev/sda   /   ext4   rw,relatime   0 1
FSTAB

    cat > "$ROOT/etc/network/interfaces" <<NET
auto lo
iface lo inet loopback

# Only reachable when a network relay is configured in the app. Without one the NE2000 has
# nothing on the other side, and dhcp simply times out.
auto eth0
iface eth0 inet dhcp
NET

    cat > "$ROOT/etc/motd" <<MOTD

  draugr :: alpine x86 security console

  No network unless a relay is configured in the app: without one, targets outside this
  guest are unreachable and dhcp will time out. 127.0.0.1 works.

  nmap masscan tcpdump tshark hydra sqlmap nikto socat radare2 aircrack-ng

  Scan only what you are authorised to scan.

MOTD

    for service in devfs dmesg mdev hwdrivers modules sysctl hostname bootmisc syslog; do
      ln -sf "/etc/init.d/$service" "$ROOT/etc/runlevels/boot/$service" 2>/dev/null || true
    done
    ln -sf /etc/init.d/networking "$ROOT/etc/runlevels/default/networking" 2>/dev/null || true

    kernel_version="$(ls "$ROOT"/lib/modules | head -1)"
    echo ">> kernel $kernel_version"

    # A hand-rolled initramfs, not mkinitfs. mkinitfs bundles /lib/firmware and the whole base
    # module set (~85MB), which only loads beside a >=256MB guest — and v86 in a mobile web view
    # cannot allocate a single buffer that big. This carries busybox plus exactly the modules v86
    # needs to expose its IDE disk as ext4, and a tiny init that mounts root and switch_roots.
    # The result is a few MB, small enough to boot beside a 128MB guest.
    # Explicit mkdir, not brace expansion: the container shell is busybox ash under `sh -euc`,
    # which does not expand {a,b}, so a braced path would create one literally-named directory
    # and the next cp would fail the whole build under set -e.
    IRD=/tmp/initramfs
    rm -rf "$IRD"
    mkdir -p "$IRD/bin" "$IRD/sbin" "$IRD/dev" "$IRD/proc" "$IRD/sys" \
      "$IRD/newroot" "$IRD/lib/modules"

    cp "$ROOT/bin/busybox" "$IRD/bin/busybox"
    for applet in sh mount umount switch_root modprobe insmod mknod mkdir sleep echo cat ls; do
      ln -sf busybox "$IRD/bin/$applet"
    done
    ln -sf ../bin/busybox "$IRD/sbin/modprobe"

    # Alpine busybox is dynamically linked against musl. Without the loader and libc the kernel
    # cannot exec /init at all (Failed to execute /init (error -2)), so copy them in.
    mkdir -p "$IRD/lib"
    for lib in "$ROOT"/lib/ld-musl-*.so.* "$ROOT"/lib/libc.musl-*.so.*; do
      [ -e "$lib" ] && cp -a "$lib" "$IRD/lib/"
    done

    # Copy only the driver subtrees that matter, with modules.* so modprobe can resolve deps.
    md="$ROOT/lib/modules/$kernel_version"
    mkdir -p "$IRD/lib/modules/$kernel_version"
    for sub in kernel/drivers/ata kernel/drivers/scsi kernel/block \
               kernel/fs/ext4 kernel/fs/jbd2 kernel/fs/mbcache.ko* kernel/lib/crc16.ko* \
               kernel/lib/crc32c* kernel/crypto; do
      for path in $md/$sub; do
        [ -e "$path" ] || continue
        dest="$IRD/lib/modules/$kernel_version/${path#$md/}"
        mkdir -p "$(dirname "$dest")"
        cp -a "$path" "$dest" 2>/dev/null || true
      done
    done
    cp "$md/modules.order" "$IRD/lib/modules/$kernel_version/" 2>/dev/null || true
    for f in "$md"/modules.builtin*; do
      [ -e "$f" ] && cp "$f" "$IRD/lib/modules/$kernel_version/" 2>/dev/null || true
    done
    depmod -b "$IRD" "$kernel_version" 2>/dev/null || true

    cat > "$IRD/init" <<INIT
#!/bin/sh
/bin/mount -t proc proc /proc
/bin/mount -t sysfs sys /sys
/bin/mount -t devtmpfs dev /dev 2>/dev/null
echo "draugr initramfs: loading storage drivers"
for m in libata ata_piix ata_generic pata_legacy sd_mod mbcache jbd2 crc32c_generic ext4; do
  /sbin/modprobe \$m 2>/dev/null || /bin/modprobe \$m 2>/dev/null
done
/bin/sleep 1
echo "draugr initramfs: mounting /dev/sda"
if /bin/mount -t ext4 -o ro /dev/sda /newroot 2>/dev/null || /bin/mount /dev/sda /newroot 2>/dev/null; then
  /bin/mount -o remount,rw /newroot 2>/dev/null
  echo "draugr initramfs: switch_root"
  exec /bin/switch_root /newroot /sbin/init
fi
echo "draugr initramfs: FAILED to mount /dev/sda, dropping to shell"
exec /bin/sh
INIT
    chmod +x "$IRD/init"

    ( cd "$IRD" && find . | cpio -o -H newc 2>/dev/null | gzip -9 > /out/initramfs-lts )
    cp "$ROOT/boot/vmlinuz-lts" /out/vmlinuz-lts
    echo ">> initramfs $(( $(stat -c %s /out/initramfs-lts) / 1048576 ))MB (hand-rolled)"

    # -d populates the filesystem from a directory, so no loop mount and no privileged
    # container is needed.
    rm -f /out/rootfs.ext4
    mke2fs -q -t ext4 -b 4096 -d "$ROOT" -F /out/rootfs.ext4 '"$IMAGE_SIZE"'

    du -sh "$ROOT" /out/rootfs.ext4 /out/vmlinuz-lts /out/initramfs-lts
  '

say "artifacts in $out"
ls -la "$out"

cat <<MSG

The image is not bundled with the app: it is far too large for an APK, and this is exactly what
the sideload path is for. Install it with:

  ./emulator/install-security-image.sh

Then boot "$MACHINE_ID" from the catalog.
MSG
