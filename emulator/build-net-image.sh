#!/usr/bin/env bash
# Builds a tiny console-only Alpine x86 guest whose only job is to prove networking: bring up
# eth0 by DHCP through a configured relay and reach the internet. For the v86 engine.
#
# Why so small: the security image is a 1.4GB rootfs and takes minutes to reach userspace, long
# enough that a mobile WebView renderer runs out of memory before it gets to udhcpc. This carries
# nothing but busybox and the kernel, so the rootfs is tens of MB, boot is seconds, and the guest
# reaches its DHCP stage well inside the WebView's memory budget. busybox alone provides udhcpc,
# wget, nslookup, ping and ip — everything an end-to-end network test needs.
#
# Read emulator/NETWORK-RELAY.md: a relay is what lets this guest off the machine at all.
#
# Requires Docker with 32-bit emulation. Verify with:
#   docker run --rm --platform linux/386 alpine:3.21 uname -m     # prints i686
set -euo pipefail

ALPINE_VERSION="3.21"
# 512M is not about the ~120M of content: it is headroom. At 150M the filesystem booted 100%
# full, and udhcpc's lease script could not write /etc/resolv.conf or the default route ("No
# space left on device") — so the guest got an address but no way out. Free space is what lets
# networking actually come up.
IMAGE_SIZE="${DRAUGR_IMAGE_SIZE:-512M}"
MACHINE_ID="alpine-x86-net"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${DRAUGR_NET_OUT:-$root/build/net-image}"

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

# The whole toolset. busybox provides udhcpc, wget, nslookup, ping and ip; that is the entire
# network test. bind-tools and curl are left out on purpose to keep the rootfs small and the
# boot fast.
# linux-firmware-none satisfies linux-lts's firmware dependency with the empty provider, so the
# ~400MB of real firmware blobs — useless to v86's emulated NE2000 and IDE — never land in the
# rootfs. That is the difference between a tens-of-MB image and a bloated one.
PACKAGES="
alpine-base linux-lts linux-firmware-none busybox-openrc util-linux e2fsprogs e2fsprogs-extra
"

say "building rootfs in a 32-bit container (this is emulated, so it is slow)"
docker run --rm --platform linux/386 \
  -v "$out:/out" \
  alpine:"$ALPINE_VERSION" \
  sh -euc '
    apk add --no-cache e2fsprogs cpio kmod gzip >/dev/null

    ROOT=/rootfs
    mkdir -p "$ROOT/etc/apk"

    cat > "$ROOT/etc/apk/repositories" <<REPOS
https://dl-cdn.alpinelinux.org/alpine/v'"$ALPINE_VERSION"'/main
https://dl-cdn.alpinelinux.org/alpine/v'"$ALPINE_VERSION"'/community
REPOS
    cp "$ROOT/etc/apk/repositories" /etc/apk/repositories

    apk add --root "$ROOT" --initdb --no-cache --allow-untrusted \
      --keys-dir /etc/apk/keys \
      '"$(echo $PACKAGES | tr -s '[:space:]' ' ')"'

    # Serial console: the app reads the guest through ttyS0 and shows it in the boot log.
    cat > "$ROOT/etc/inittab" <<INITTAB
::sysinit:/sbin/openrc sysinit
::sysinit:/sbin/openrc boot
::wait:/sbin/openrc default
ttyS0::respawn:/sbin/getty -L 115200 ttyS0 vt100
tty1::respawn:/sbin/getty 38400 tty1
::shutdown:/sbin/openrc shutdown
INITTAB

    # Root with no password: typing one through an on-screen scancode keyboard is misery, and
    # this guest has no network unless a relay is configured.
    sed -i "s|^root:[^:]*:|root::|" "$ROOT/etc/shadow"

    echo draugr-net > "$ROOT/etc/hostname"
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

  draugr :: alpine x86 net probe

  A relay must be configured in the app, or dhcp times out and nothing outside this guest
  is reachable. With a relay:

    udhcpc -i eth0            # get a lease from v86 s stack
    nslookup example.org     # DNS out through the relay (UDP)
    wget -qO- http://example.org/   # HTTP out through the relay (TCP)

MOTD

    # A hands-free boot probe: once userspace is up it brings eth0 up itself and prints, to the
    # serial console the app reads, whether DHCP, DNS and HTTP each worked. This is what lets the
    # end-to-end network test be watched from outside without typing into an on-screen keyboard.
    # Every line is tagged DRAUGR-NET so it is greppable in the boot log.
    mkdir -p "$ROOT/etc/local.d"
    cat > "$ROOT/etc/local.d/netprobe.start" <<PROBE
#!/bin/sh
# Runs once networking is up and drives an outbound request so the WISP relay log is the
# evidence, independent of the on-screen keyboard. Results also land in /root/netprobe.log.
# Do NOT rewrite /etc/resolv.conf: v86 resolves names sent to its own gateway (.1) itself, so
# the leased resolver is the one that works. The request that must cross the relay is the TCP
# fetch below — v86 has no path out for it except the relay.
exec > /root/netprobe.log 2>&1
echo "DRAUGR-NET: bringing up eth0 by dhcp"
udhcpc -i eth0 -t 15 -T 2 -n -q || echo "DRAUGR-NET: udhcpc FAILED (no relay?)"
ip route
echo "DRAUGR-NET: resolv.conf:"; cat /etc/resolv.conf
echo "DRAUGR-NET: dns example.org (via the leased resolver)"
nslookup example.org 2>&1
echo "DRAUGR-NET: http example.org (TCP out through the relay)"
wget -T 20 -qO- http://example.org/ 2>&1 | head -c 400
echo
echo "DRAUGR-NET: DONE"
# Surface the result on the serial console the app shows, in one burst, after the getty settles.
sleep 8
sed "s/^/DRAUGR-NET: /" /root/netprobe.log > /dev/ttyS0 2>/dev/null || true
PROBE
    chmod +x "$ROOT/etc/local.d/netprobe.start"

    for service in devfs dmesg mdev hwdrivers modules sysctl hostname bootmisc syslog; do
      ln -sf "/etc/init.d/$service" "$ROOT/etc/runlevels/boot/$service" 2>/dev/null || true
    done
    ln -sf /etc/init.d/networking "$ROOT/etc/runlevels/default/networking" 2>/dev/null || true
    ln -sf /etc/init.d/local "$ROOT/etc/runlevels/default/local" 2>/dev/null || true

    kernel_version="$(ls "$ROOT"/lib/modules | head -1)"
    echo ">> kernel $kernel_version"

    # Same hand-rolled initramfs as the security image: busybox, the musl loader, and only the
    # ata/scsi/ext4 modules, with an init that mounts /dev/sda and switch_roots. mkinitfs bundles
    # ~85MB of firmware and modules that will not load beside a 128MB guest.
    IRD=/tmp/initramfs
    rm -rf "$IRD"
    mkdir -p "$IRD/bin" "$IRD/sbin" "$IRD/dev" "$IRD/proc" "$IRD/sys" \
      "$IRD/newroot" "$IRD/lib/modules"

    cp "$ROOT/bin/busybox" "$IRD/bin/busybox"
    for applet in sh mount umount switch_root modprobe insmod mknod mkdir sleep echo cat ls; do
      ln -sf busybox "$IRD/bin/$applet"
    done
    ln -sf ../bin/busybox "$IRD/sbin/modprobe"

    mkdir -p "$IRD/lib"
    for lib in "$ROOT"/lib/ld-musl-*.so.* "$ROOT"/lib/libc.musl-*.so.*; do
      [ -e "$lib" ] && cp -a "$lib" "$IRD/lib/"
    done

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

    rm -f /out/rootfs.ext4
    mke2fs -q -t ext4 -b 4096 -d "$ROOT" -F /out/rootfs.ext4 '"$IMAGE_SIZE"'

    du -sh "$ROOT" /out/rootfs.ext4 /out/vmlinuz-lts /out/initramfs-lts
  '

say "artifacts in $out"
ls -la "$out"

cat <<MSG

Install it with:

  ./emulator/install-net-image.sh

Then configure a relay in Settings > Network and boot "$MACHINE_ID" from the catalog.
MSG
