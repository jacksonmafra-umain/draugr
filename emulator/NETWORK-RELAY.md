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

## What is verified, and what is not

- **The relay routes real traffic.** Verified on loopback: a WISP TCP stream to `example.org:80`
  returned `HTTP/1.1 200 OK`, and a WISP UDP stream to `8.8.8.8:53` returned a DNS answer. So both
  TCP and DNS work through it.
- The app persists the relay URL, shows the on/offline state, and hands v86 the `wisp://` form.
  Verified on a Galaxy A34, with unit tests over the scheme mapping.
- The security console boots to a root shell on-device and `nmap` runs against `127.0.0.1`
  (see `SECURITY-IMAGE.md`).
- **Not yet confirmed:** the full phone → relay → internet path from inside the guest. The guest
  takes several minutes to reach its `udhcpc` stage, the test phone's Wi-Fi had a manual HTTP
  proxy set, and the run kept being interrupted by the phone being picked up (which backgrounds
  the app and pauses the guest). The mechanism is proven; the on-device end-to-end run is the
  open item.

## Only what you are authorised to reach

A relay makes the guest as capable on the network as the machine running the relay. Point it at
targets you own or have permission to test, and nothing else.
