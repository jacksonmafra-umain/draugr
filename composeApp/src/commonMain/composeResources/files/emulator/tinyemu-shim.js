// Engine B: TinyEMU, wrapped so it looks exactly like Engine A to the Kotlin side.
//
// Registers itself through window.DRAUGR_REGISTER_ENGINE, so bridge.js never needs to know
// this file exists. Loaded on demand the first time a tinyemu machine is booted.
//
// TinyEMU's own JavaScript entry points are used directly: vm_start takes the URL of a config
// file, which the app generates and serves from the local asset server, and the guest's block
// reads become HTTP Range requests through Emscripten's async wget. draugr_lib.js supplies the
// five hooks jsemu.c calls back into.

(function () {
  'use strict';

  var CONSOLE_COLUMNS = 80;
  var CONSOLE_ROWS = 25;

  function TinyEmuEngine() {
    this.module = null;
    this.serialBuffer = '';
    this.flushHandle = null;
    this.canvas = document.querySelector('#screen_container canvas');
    this.text = document.querySelector('#screen_container div');
    this.graphical = false;
    this.emit = window.DRAUGR_EMIT;
  }

  TinyEmuEngine.prototype.host = function () {
    var self = this;
    return {
      onConsoleWrite: function (text) {
        for (var i = 0; i < text.length; i++) {
          var char = text[i];
          if (char === '\r') continue;
          if (char === '\n') {
            self.flushSerial();
          } else {
            self.serialBuffer += char;
            if (self.serialBuffer.length > 512) self.flushSerial();
            self.scheduleFlush();
          }
        }
        self.appendToScreen(text);
      },

      consoleColumns: function () {
        return CONSOLE_COLUMNS;
      },

      consoleRows: function () {
        return CONSOLE_ROWS;
      },

      onFramebuffer: function (data, x, y, w, h, stride) {
        self.blitFramebuffer(data, x, y, w, h, stride);
      },

      onDownloading: function (active) {
        self.emit({ type: 'state', state: active ? 'fetching' : 'booting' });
      },
    };
  };

  TinyEmuEngine.prototype.scheduleFlush = function () {
    var self = this;
    if (this.flushHandle !== null) return;
    this.flushHandle = setTimeout(function () {
      self.flushHandle = null;
      if (self.serialBuffer.length > 0) self.flushSerial();
    }, 250);
  };

  TinyEmuEngine.prototype.flushSerial = function () {
    if (this.flushHandle !== null) {
      clearTimeout(this.flushHandle);
      this.flushHandle = null;
    }
    var line = this.serialBuffer;
    this.serialBuffer = '';
    this.emit({ type: 'serial', line: line });
  };

  // Console guests have no framebuffer, so the text goes into the same element v86 writes to
  // and gets the same fitting treatment.
  TinyEmuEngine.prototype.appendToScreen = function (text) {
    if (!this.text || this.graphical) return;
    this.text.textContent = (this.text.textContent + text).slice(-CONSOLE_COLUMNS * CONSOLE_ROWS * 4);
  };

  TinyEmuEngine.prototype.blitFramebuffer = function (data, x, y, w, h, stride) {
    if (!this.canvas || !this.module) return;
    if (!this.graphical) {
      this.graphical = true;
      this.emit({ type: 'screen', width: w, height: h, graphical: true });
    }
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
      this.emit({ type: 'screen', width: w, height: h, graphical: true });
    }
    var context = this.canvas.getContext('2d');
    var image = context.createImageData(w, h);
    var heap = this.module.HEAPU8;
    for (var row = 0; row < h; row++) {
      var source = data + row * stride;
      var target = row * w * 4;
      for (var column = 0; column < w; column++) {
        // TinyEMU's simplefb is BGRA in memory; canvas wants RGBA.
        image.data[target + column * 4] = heap[source + column * 4 + 2];
        image.data[target + column * 4 + 1] = heap[source + column * 4 + 1];
        image.data[target + column * 4 + 2] = heap[source + column * 4];
        image.data[target + column * 4 + 3] = 255;
      }
    }
    context.putImageData(image, x, y);
  };

  TinyEmuEngine.prototype.boot = function (config) {
    var self = this;
    if (!config.configUrl) {
      throw new Error('tinyemu needs a config url');
    }
    if (typeof TinyEmu64 !== 'function') {
      throw new Error('engine not built: run emulator/build-tinyemu.sh');
    }

    this.emit({ type: 'state', state: 'fetching' });
    this.graphical = config.ui === 'X_WINDOW' || config.ui === 'GRAPHICAL';

    TinyEmu64().then(function (module) {
      self.module = module;
      module['DRAUGR_HOST'] = self.host();

      self.emit({ type: 'state', state: 'booting' });
      module.ccall(
        'vm_start',
        null,
        ['string', 'number', 'string', 'string', 'number', 'number', 'number'],
        [
          config.configUrl,
          config.memMb || 256,
          config.cmdline || '',
          null,
          self.canvas ? self.canvas.width || 800 : 800,
          self.canvas ? self.canvas.height || 600 : 600,
          0,
        ],
      );
      self.emit({ type: 'state', state: 'running' });
    }).catch(function (error) {
      self.emit({ type: 'error', stage: 'boot', message: String(error) });
    });
  };

  // TinyEMU speaks Linux keycodes, so the Kotlin side sends those for this engine.
  TinyEmuEngine.prototype.sendKeys = function (codes) {
    if (!this.module) return;
    for (var i = 0; i < codes.length; i++) {
      var code = codes[i];
      var down = (code & 0x80) === 0;
      this.module.ccall('display_key_event', null, ['number', 'number'], [down ? 1 : 0, code & 0x7f]);
    }
  };

  TinyEmuEngine.prototype.sendText = function (text) {
    if (!this.module) return;
    for (var i = 0; i < text.length; i++) {
      this.module.ccall('console_queue_char', null, ['number'], [text.charCodeAt(i)]);
    }
  };

  // TinyEMU has no state serialiser upstream, so these report honestly rather than pretending.
  TinyEmuEngine.prototype.pause = function () {
    throw new Error('tinyemu cannot be paused yet');
  };

  TinyEmuEngine.prototype.resume = function () {
    throw new Error('tinyemu cannot be resumed yet');
  };

  TinyEmuEngine.prototype.snapshot = function () {
    throw new Error('tinyemu snapshots are not implemented');
  };

  TinyEmuEngine.prototype.restore = function () {
    throw new Error('tinyemu snapshots are not implemented');
  };

  TinyEmuEngine.prototype.screenshot = function () {
    if (!this.graphical || !this.canvas || !this.canvas.width) return null;
    return this.canvas.toDataURL('image/png');
  };

  window.DRAUGR_REGISTER_ENGINE('tinyemu', TinyEmuEngine);
})();
