// Emscripten --js-library for TinyEMU.
//
// TinyEMU's jsemu.c calls out to five JavaScript functions. Upstream ships them in a js/lib.js
// that is part of the jslinux.com build rather than the MIT source archive, so this file
// provides them instead. Everything lands on window.DRAUGR_TINYEMU, which tinyemu-shim.js
// reads and forwards through the uniform bridge.
//
// TinyEMU fetches the config, BIOS, kernel and disk blocks with emscripten_async_wget3_data,
// which is plain Emscripten and needs nothing from us. That is also what turns the guest's
// block reads into HTTP Range requests against the local asset server.

mergeInto(LibraryManager.library, {
  console_write: function (opaque, buf, len) {
    var host = Module['DRAUGR_HOST'];
    if (!host) return;
    var text = '';
    for (var i = 0; i < len; i++) {
      text += String.fromCharCode(HEAPU8[buf + i]);
    }
    host.onConsoleWrite(text);
  },

  console_get_size: function (pw, ph) {
    var host = Module['DRAUGR_HOST'];
    var cols = host ? host.consoleColumns() : 80;
    var rows = host ? host.consoleRows() : 25;
    HEAP32[pw >> 2] = cols;
    HEAP32[ph >> 2] = rows;
  },

  fb_refresh: function (opaque, data, x, y, w, h, stride) {
    var host = Module['DRAUGR_HOST'];
    if (!host) return;
    host.onFramebuffer(data, x, y, w, h, stride);
  },

  net_recv_packet: function (bs, buf, len) {
    // No network: the app is loopback only and ships airplane-mode clean.
  },

  fs_wget_update_downloading: function (flag) {
    var host = Module['DRAUGR_HOST'];
    if (host) host.onDownloading(flag !== 0);
  },
});
