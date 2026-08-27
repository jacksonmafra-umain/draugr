# Guest networking, and what it costs

draugr ships airplane-mode clean: the app makes no outbound requests, and the only peer is the
embedded server on `127.0.0.1`. A guest inside it is just as offline — v86's NE2000 has nothing
on the far side, so DHCP times out and only loopback answers.

A guest reaches a real network **only** through a WebSocket relay, and turning that on is the one
thing that breaks the guarantee above. It is therefore off by default, opt-in, and stated in
plain terms wherever it appears.

## How v86 networking works

v86 does not open sockets. It hands every Ethernet frame the guest sends to a WebSocket
(`network_relay_url`) and injects whatever comes back. Something on the other end has to bridge
those frames to real TCP/IP. That is the relay. No relay, no network — there is no accidental
path out.

## Running the relay

```bash
node tools/network-relay.mjs                 # ws://127.0.0.1:4555
node tools/network-relay.mjs --port 4555 --host 0.0.0.0
```

Needs only Node 22+ (built-in `net`, `dgram`, `crypto`; no npm install). It is a **WISP** relay
(https://github.com/MercuryWorkshop/wisp-protocol): v86 speaks WISP over the WebSocket, running
its own TCP/IP stack in the browser — so it answers the guest's DHCP and ARP itself — and
forwards each flow as a WISP stream. The relay opens the matching real socket with `node:net`
(TCP) or `node:dgram` (UDP, so the guest's DNS works) and pipes bytes both ways. No TAP, no root.

The app hands v86 a `wisp://` (or `wisps://`) URL derived from the `ws://`/`wss://` you enter,
because v86 selects its backend from the scheme.

Android note: a `ws://` relay is a cleartext WebSocket to a LAN host, which the app's
network security config must permit. It does — cleartext is allowed precisely so a
configured relay can be reached; nothing connects outbound unless a relay URL is set.

## Turning it on in the app

Settings → **NETWORK**. Enter the relay's `ws://` or `wss://` URL (the phone reaches your
machine at its LAN address, e.g. `ws://192.168.0.50:4555`, not `127.0.0.1`), then `>> APPLY`.
The panel header changes from `[OFFLINE]` to `[RELAY ON]` and turns red, and the copy says out
loud that guest traffic now leaves the device. `>> GO OFFLINE` clears it. It applies to the next
boot.

The URL is stored like any other setting and survives a restart. Blank is the default and means
no network.

## A tiny image built to test exactly this

`emulator/build-net-image.sh` builds `alpine-x86-net`, a console-only Alpine x86 guest carrying
nothing but busybox — small on purpose so it boots inside a mobile WebView's memory budget. It
brings up `eth0` by DHCP and, at boot, a `local.d` probe runs `udhcpc`, `nslookup` and
`wget http://example.org/`, so the relay's own log is the evidence without any typing. Build and
sideload it like the security image (`emulator/install-net-image.sh`), point the app at a relay,
and boot it.

Run the relay with `DRAUGR_WISP_DEBUG=1` to log every WISP frame the guest sends — invaluable for
telling "the guest never tried" apart from "the relay dropped it".

## The Android Wi-Fi proxy gotcha

If the phone's Wi-Fi has a **manual HTTP proxy** set, the WebView honours it for LAN hosts and the
relay WebSocket is dialled *through that proxy* — so a dead proxy silently stops the guest
connecting, with nothing in the relay log. Either clear the proxy in Wi-Fi settings, or point it at
a real pass-through. Symptom in `adb logcat`: `NetworkMonitor ... Probe failed ... connect to
/<proxy-ip> (port <n>)`.

## What is verified, and what is not

- **The relay routes real traffic.** Verified on loopback: a WISP TCP stream to `example.org:80`
  returned `HTTP/1.1 200 OK`, and a WISP UDP stream to `8.8.8.8:53` returned a DNS answer. So both
  TCP and DNS work through it.
- **On a physical Galaxy A34**, with the `alpine-x86-net` guest and a LAN relay: the guest boots to
  a shell, v86 opens the WISP WebSocket to the relay, `udhcpc` obtains a lease and a default route
  from v86's stack, and `nslookup example.org` returns live public addresses
  (`172.66.157.237`, `104.20.26.136`, …). So the guest resolves real internet names on-device.
- The app persists the relay URL, shows the on/offline state, and hands v86 the `wisp://` form,
  with unit tests over the scheme mapping.
- **Two on-device caveats, both about the phone, not the relay:**
  - v86 answers the guest's DHCP/ARP and resolves DNS (over DoH) *itself* in the browser; only
    the guest's own TCP/UDP flows cross the relay as WISP streams. So a successful `nslookup` proves
    the guest reached the internet, but does not by itself exercise a relay *stream*.
  - Capturing that outbound TCP stream on-device (the boot-time `wget`) is the open item: on the
    test phone the WebView renderer is reclaimed under memory pressure ~3–4 min into the heavy boot,
    before the probe fires. Freeing RAM lets the guest reach a shell (proven), but the crash is
    non-deterministic. On a desktop browser, where v86 has full memory, this ceiling is absent.

## Only what you are authorised to reach

A relay makes the guest as capable on the network as the machine running the relay. Point it at
targets you own or have permission to test, and nothing else.
