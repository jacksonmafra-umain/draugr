#!/usr/bin/env node
// A WISP relay for the draugr guests: real outbound networking, no TAP and no root.
//
// This is the ONE switch that ends draugr's no-outbound-requests guarantee. Running it, and
// pointing the app at it, means guest traffic leaves this machine through here. That is the
// whole point of the relay, and the reason it is opt-in and off by default.
//
//   node tools/network-relay.mjs [--port 4555] [--host 0.0.0.0]
//
// Point the app at ws://<this-machine-lan-ip>:<port>/ in Settings > Network. v86 speaks WISP
// over that WebSocket (network_relay_url = wisp://...): it runs its own TCP/IP stack in the
// browser — so it answers the guest's DHCP and ARP itself — and forwards each TCP/UDP flow to
// this server as a WISP stream. This server opens the matching real socket with node:net and
// pipes bytes both ways. So the guest gets working DNS and outbound TCP through the host.
//
// WISP v1 wire format (https://github.com/MercuryWorkshop/wisp-protocol):
//   frame = [type:u8][streamId:u32le][payload]
//   0x01 CONNECT  payload=[kind:u8 (1 tcp, 2 udp)][port:u16le][host:utf8]
//   0x02 DATA     payload=raw bytes
//   0x03 CONTINUE server->client, payload=[bufferRemaining:u32le]
//   0x04 CLOSE    payload=[reason:u8]
//
// No dependencies: Node's built-in http upgrade and a tiny RFC6455 frame codec.
import net from 'node:net';
import dgram from 'node:dgram';
import dns from 'node:dns';
import http from 'node:http';
import crypto from 'node:crypto';

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const PORT = parseInt(opt('--port', '4555'), 10);
const HOST = opt('--host', '0.0.0.0');
const BUFFER = 128; // CONTINUE window, in packets, per stream.

const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

const server = http.createServer((req, res) => {
  res.writeHead(426, { 'Content-Type': 'text/plain' });
  res.end('draugr WISP relay: connect over WebSocket\n');
});

server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  if (!key) return socket.destroy();
  const accept = crypto.createHash('sha1').update(key + WS_GUID).digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
      'Upgrade: websocket\r\nConnection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
  );
  console.log(`[relay] guest connected from ${req.socket.remoteAddress}`);
  new WispSession(socket);
});

// ---- WebSocket frame codec (binary only; the guest never sends text) --------------------

function wsEncode(payload) {
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.from([0x82, len]);
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x82;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x82;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

function wsDecode(buf) {
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

// ---- WISP session -----------------------------------------------------------------------

class WispSession {
  constructor(socket) {
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.streams = new Map(); // streamId -> net.Socket
    socket.on('data', (chunk) => this.onData(chunk));
    socket.on('close', () => this.destroy());
    socket.on('error', () => this.destroy());
    // WISP handshake: CONTINUE on stream 0 advertising the buffer window.
    this.sendContinue(0, BUFFER);
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    for (;;) {
      const frame = wsDecode(this.buffer);
      if (!frame) break;
      this.buffer = this.buffer.subarray(frame.total);
      if (frame.opcode === 0x8) return this.destroy();
      if (frame.opcode === 0x2 && frame.payload.length >= 5) this.onWisp(frame.payload);
    }
  }

  onWisp(pkt) {
    const type = pkt[0];
    const streamId = pkt.readUInt32LE(1);
    const payload = pkt.subarray(5);
    if (type === 0x01) this.connect(streamId, payload);
    else if (type === 0x02) this.data(streamId, payload);
    else if (type === 0x04) this.closeStream(streamId, false);
  }

  connect(streamId, payload) {
    const kind = payload[0];
    const port = payload.readUInt16LE(1);
    const host = payload.subarray(3).toString('utf8');
    if (kind === 0x02) return this.connectUdp(streamId, host, port);
    if (kind !== 0x01) return this.sendClose(streamId, 0x41);

    console.log(`[relay] stream ${streamId}: TCP ${host}:${port}`);
    const tcp = net.connect({ host, port, lookup: dns.lookup });
    tcp.draugrKind = 'tcp';
    this.streams.set(streamId, tcp);
    tcp.on('connect', () => this.sendContinue(streamId, BUFFER));
    tcp.on('data', (d) => this.sendData(streamId, d));
    tcp.on('end', () => this.closeStream(streamId, true));
    tcp.on('error', () => this.sendClose(streamId, 0x03));
    tcp.on('close', () => this.closeStream(streamId, true));
  }

  // UDP matters because the guest resolves DNS over :53 before any TCP flow.
  connectUdp(streamId, host, port) {
    console.log(`[relay] stream ${streamId}: UDP ${host}:${port}`);
    const udp = dgram.createSocket('udp4');
    udp.draugrKind = 'udp';
    udp.draugrTarget = { host, port };
    this.streams.set(streamId, udp);
    udp.on('message', (d) => this.sendData(streamId, d));
    udp.on('error', () => this.sendClose(streamId, 0x03));
    this.sendContinue(streamId, BUFFER);
  }

  data(streamId, bytes) {
    const stream = this.streams.get(streamId);
    if (!stream) return;
    if (stream.draugrKind === 'udp') {
      stream.send(bytes, stream.draugrTarget.port, stream.draugrTarget.host, () => {});
    } else if (stream.writable) {
      stream.write(bytes);
      this.sendContinue(streamId, BUFFER);
    }
  }

  closeStream(streamId, notifyGuest) {
    const stream = this.streams.get(streamId);
    if (stream) {
      this.streams.delete(streamId);
      if (stream.draugrKind === 'udp') stream.close();
      else stream.destroy();
      if (notifyGuest) this.sendClose(streamId, 0x02);
    }
  }

  // ---- outbound WISP frames ----

  send(type, streamId, payload = Buffer.alloc(0)) {
    const head = Buffer.alloc(5);
    head[0] = type;
    head.writeUInt32LE(streamId >>> 0, 1);
    if (this.socket.writable) this.socket.write(wsEncode(Buffer.concat([head, payload])));
  }

  sendData(streamId, bytes) {
    this.send(0x02, streamId, bytes);
  }

  sendContinue(streamId, packets) {
    const p = Buffer.alloc(4);
    p.writeUInt32LE(packets, 0);
    this.send(0x03, streamId, p);
  }

  sendClose(streamId, reason) {
    this.send(0x04, streamId, Buffer.from([reason]));
  }

  destroy() {
    for (const tcp of this.streams.values()) tcp.destroy();
    this.streams.clear();
    if (!this.socket.destroyed) this.socket.destroy();
  }
}

server.listen(PORT, HOST, () => {
  console.log(`[relay] WISP relay on ws://${HOST}:${PORT}`);
  console.log('[relay] Settings > Network: use ws://<this-machine-lan-ip>:' + PORT + '/');
  console.log('[relay] traffic leaves this machine: the app is no longer offline-only.');
});
