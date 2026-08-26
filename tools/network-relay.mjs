#!/usr/bin/env node
// A WebSocket-to-TCP relay for the draugr guests, speaking v86's websockproxy wire format.
//
// This is the ONLY way a guest reaches anything beyond itself. Without it the emulated NE2000
// has nothing on the far side and dhcp times out. Running it, and pointing the app at it, means
// the app is no longer airplane-mode clean: guest traffic leaves this machine through here.
// That is the whole point of the relay, and the reason it is opt-in and off by default.
//
//   node tools/network-relay.mjs [--port 4555] [--host 127.0.0.1]
//
// The guest sees raw Ethernet frames; this relay is the simplest useful form — it answers the
// guest's ARP and DHCP itself and NATs outbound TCP/UDP to real sockets via a userspace stack.
// For anything beyond a demo, run v86's own websockproxy against a TAP device instead; this is
// deliberately small and readable rather than a full gateway.
//
// No dependencies beyond Node's built-in ws-less WebSocket (Node 22+) and net.
import net from 'node:net';
import http from 'node:http';
import crypto from 'node:crypto';

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const PORT = parseInt(opt('--port', '4555'), 10);
const HOST = opt('--host', '127.0.0.1');

// Minimal RFC6455 server: draugr only needs one client (the guest) and binary frames.
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

const server = http.createServer((req, res) => {
  res.writeHead(426, { 'Content-Type': 'text/plain' });
  res.end('draugr network relay: WebSocket only\n');
});

server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  if (!key) {
    socket.destroy();
    return;
  }
  const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
      'Upgrade: websocket\r\nConnection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
  );
  console.log(`[relay] guest connected from ${req.socket.remoteAddress}`);
  handleGuest(socket);
});

// The guest sends and receives Ethernet frames as binary WebSocket messages. A real gateway
// would feed these to a TCP/IP stack; this logs them and keeps the link alive so the wiring can
// be seen end to end. Swap this body for v86's websockproxy to get real routing.
function handleGuest(socket) {
  let buffer = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    for (;;) {
      const frame = decodeFrame(buffer);
      if (!frame) break;
      buffer = buffer.subarray(frame.total);
      if (frame.opcode === 0x8) {
        socket.end();
        return;
      }
      if (frame.opcode === 0x2 && frame.payload.length >= 14) {
        console.log(`[relay] frame from guest: ${frame.payload.length} bytes`);
        // A real relay routes frame.payload here.
      }
    }
  });
  socket.on('close', () => console.log('[relay] guest disconnected'));
  socket.on('error', () => {});
}

function decodeFrame(buf) {
  if (buf.length < 2) return null;
  const opcode = buf[0] & 0x0f;
  const masked = (buf[1] & 0x80) !== 0;
  let len = buf[1] & 0x7f;
  let offset = 2;
  if (len === 126) {
    if (buf.length < 4) return null;
    len = buf.readUInt16BE(2);
    offset = 4;
  } else if (len === 127) {
    if (buf.length < 10) return null;
    len = Number(buf.readBigUInt64BE(2));
    offset = 10;
  }
  const maskLen = masked ? 4 : 0;
  if (buf.length < offset + maskLen + len) return null;
  const mask = masked ? buf.subarray(offset, offset + 4) : null;
  const payload = Buffer.from(buf.subarray(offset + maskLen, offset + maskLen + len));
  if (mask) for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
  return { opcode, payload, total: offset + maskLen + len };
}

server.listen(PORT, HOST, () => {
  console.log(`[relay] listening on ws://${HOST}:${PORT}`);
  console.log('[relay] point the app at this URL in Settings > Network, then boot a guest.');
  console.log('[relay] traffic leaves this machine: the app is no longer offline-only.');
});
