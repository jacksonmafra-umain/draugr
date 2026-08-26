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

Needs only Node 22+ (built-in crypto and net; no npm install). It speaks the raw-Ethernet-frame
WebSocket format v86 uses. **The shipped script is deliberately minimal**: it accepts the guest,
logs frames, and keeps the link up, so the wiring can be seen end to end. It does not yet NAT
frames to real sockets — for actual routing, point `network_relay_url` at v86's own
`websockproxy` against a TAP device, which is a full gateway. The script's header says so too.

## Turning it on in the app

Settings → **NETWORK**. Enter the relay's `ws://` or `wss://` URL (the phone reaches your
machine at its LAN address, e.g. `ws://192.168.0.50:4555`, not `127.0.0.1`), then `>> APPLY`.
The panel header changes from `[OFFLINE]` to `[RELAY ON]` and turns red, and the copy says out
loud that guest traffic now leaves the device. `>> GO OFFLINE` clears it. It applies to the next
boot.

The URL is stored like any other setting and survives a restart. Blank is the default and means
no network.

## What is verified, and what is not

- The relay script runs, completes the WebSocket handshake, and logs guest frames. Verified.
- The app persists the relay URL, shows the on/offline state, and threads the URL into the v86
  boot config. Verified on a Galaxy A34.
- A guest actually routing traffic through the relay to the internet is **not** verified
  end to end, because it needs a guest that boots to a shell, and the large x86 guest that
  carries networking tools does not yet boot on the device tested — see `SECURITY-IMAGE.md`.
  FreeDOS boots but has no TCP/IP stack to drive the NIC.

So the honest state: the plumbing is built and each piece is tested in isolation; a live scan
from a phone through the relay awaits the guest-boot memory work in `SECURITY-IMAGE.md`, or a
desktop browser where the larger guest boots today.

## Only what you are authorised to reach

A relay makes the guest as capable on the network as the machine running the relay. Point it at
targets you own or have permission to test, and nothing else.
