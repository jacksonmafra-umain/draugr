// The single interface the Kotlin side talks to. Engine specifics live behind it, so
// commonMain never branches on which emulator is running.
//
//   window.DRAUGR.boot(config)
//   window.DRAUGR.sendKeys([scancode, ...])
//   window.DRAUGR.sendText(str)
//   window.DRAUGR.pause() / resume()
//   window.DRAUGR.snapshot() -> Promise<ArrayBuffer>
//   window.DRAUGR.restore(buffer)
//
// Events travel the other way as JSON: {type: "state"|"serial"|"screen"|"error", ...}

(function () {
  'use strict';


  var listeners = [];

  function emit(event) {
    var text = JSON.stringify(event);
    if (window.DraugrNative && window.DraugrNative.event) {
      window.DraugrNative.event(text);
    } else if (
      window.webkit &&
      window.webkit.messageHandlers &&
      window.webkit.messageHandlers.draugr
    ) {
      window.webkit.messageHandlers.draugr.postMessage(text);
    }
    for (var i = 0; i < listeners.length; i++) {
      try {
        listeners[i](event);
      } catch (ignored) {
        // A broken listener must not stop the emulator.
      }
    }
  }

  function fail(stage, error) {
    emit({
      type: 'error',
      stage: stage,
      message: error && error.message ? error.message : String(error)
    });
  }

  // ---------------------------------------------------------------- engine: v86

  function V86Engine() {
    this.emulator = null;
    this.serialBuffer = '';
    this.flushHandle = null;
    this.graphical = false;
    this.cols = 80;
    this.rows = 25;
  }

  // A text screen is a character grid, not pixels. v86 renders each row as a block-level
  // child whose box is the parent's width, so measuring the DOM tells us nothing about the
  // real content width. Size the font from the grid instead: cols x glyph advance.
  var BASE_FONT_PX = 16;
  var LINE_HEIGHT = 1.05;
  var measureCanvas = document.createElement('canvas');

  function glyphWidth(fontPx) {
    var ctx = measureCanvas.getContext('2d');
    ctx.font = fontPx + 'px monospace';
    return ctx.measureText('M').width || fontPx * 0.6;
  }

  V86Engine.prototype.fitTextScreen = function () {
    if (this.graphical) return;
    var container = document.getElementById('screen_container');
    var text = container.querySelector('div');
    if (!text) return;

    // v86 writes inline width/height onto the container, which beats the stylesheet and can
    // leave it 0px tall in text mode. Reassert the surface size first.
    container.style.width = window.innerWidth + 'px';
    container.style.height = window.innerHeight + 'px';

    var cols = this.cols || 80;
    var rows = this.rows || 25;
    var advance = glyphWidth(BASE_FONT_PX) / BASE_FONT_PX;

    var byWidth = window.innerWidth / (cols * advance);
    var byHeight = window.innerHeight / (rows * LINE_HEIGHT);
    // A hair under a perfect fit: sub-pixel advances round up and clip the last column.
    var fontPx = Math.floor(Math.min(byWidth, byHeight) * 100) / 100 * 0.98;
    if (!(fontPx > 0) || !isFinite(fontPx)) return;

    text.style.transform = 'none';
    text.style.fontSize = fontPx + 'px';
    text.style.lineHeight = String(LINE_HEIGHT);
  };

  V86Engine.prototype.boot = function (config) {
    var self = this;
    var options = {
      wasm_path: 'v86/v86.wasm',
      memory_size: (config.memMb || 128) * 1024 * 1024,
      vga_memory_size: (config.vgaMemMb || 8) * 1024 * 1024,
      screen_container: document.getElementById('screen_container'),
      autostart: true,
      disable_speaker: true,
      // Range requests keep a large image out of RAM: v86 asks for the blocks it needs.
      // async: true is what turns a URL into a block device instead of a full download.
      bios: config.bios ? { url: config.bios } : undefined,
      vga_bios: config.vgabios ? { url: config.vgabios } : undefined
    };

    if (config.hda) {
      options.hda = { url: config.hda, size: config.hdaSize || undefined, async: true };
    }
    if (config.fda) {
      options.fda = { url: config.fda, size: config.fdaSize || undefined, async: true };
    }
    if (config.cdrom) {
      options.cdrom = { url: config.cdrom, size: config.cdromSize || undefined, async: true };
    }
    if (config.kernel) {
      options.bzimage = { url: config.kernel, async: false };
      options.cmdline = config.cmdline || 'console=ttyS0 root=/dev/sda rw';
    }
    if (config.initrd) {
      options.initrd = { url: config.initrd, async: false };
    }
    if (config.stateImage) {
      options.initial_state = { url: config.stateImage };
    }

    this.emulator = new V86(options);

    this.emulator.add_listener('emulator-ready', function () {
      emit({ type: 'state', state: 'booting' });
    });

    this.emulator.add_listener('emulator-loaded', function () {
      emit({ type: 'state', state: 'running' });
    });

    this.emulator.add_listener('serial0-output-byte', function (byte) {
      var char = String.fromCharCode(byte);
      if (char === '\r') return;
      if (char === '\n') {
        self.flushSerial();
      } else {
        self.serialBuffer += char;
        if (self.serialBuffer.length > 512) self.flushSerial();
        self.scheduleFlush();
      }
    });

    this.emulator.add_listener('screen-set-mode', function (graphical) {
      self.graphical = graphical;
      var container = document.getElementById('screen_container');
      container.style.alignItems = graphical ? 'center' : 'flex-start';
      container.style.justifyContent = graphical ? 'center' : 'flex-start';
      setTimeout(function () { self.fitTextScreen(); }, 0);
    });

    this.emulator.add_listener('screen-set-size', function (args) {
      if (!self.graphical) {
        self.cols = args[0];
        self.rows = args[1];
      }
      emit({
        type: 'screen',
        width: args[0],
        height: args[1],
        graphical: self.graphical
      });
      setTimeout(function () { self.fitTextScreen(); }, 0);
    });

    window.addEventListener('resize', function () { self.fitTextScreen(); });
    window.addEventListener('draugr-resize', function () { self.fitTextScreen(); });

    this.emulator.add_listener('emulator-stopped', function () {
      emit({ type: 'state', state: 'suspended' });
    });

    emit({ type: 'state', state: 'fetching' });
  };

  // A guest that never emits a newline would otherwise look frozen.
  V86Engine.prototype.scheduleFlush = function () {
    var self = this;
    if (this.flushHandle !== null) return;
    this.flushHandle = setTimeout(function () {
      self.flushHandle = null;
      if (self.serialBuffer.length > 0) self.flushSerial();
    }, 250);
  };

  V86Engine.prototype.flushSerial = function () {
    if (this.flushHandle !== null) {
      clearTimeout(this.flushHandle);
      this.flushHandle = null;
    }
    var line = this.serialBuffer;
    this.serialBuffer = '';
    emit({ type: 'serial', line: line });
  };

  V86Engine.prototype.sendKeys = function (codes) {
    this.emulator.keyboard_send_scancodes(codes);
  };

  V86Engine.prototype.sendText = function (text) {
    this.emulator.keyboard_send_text(text);
  };

  V86Engine.prototype.pause = function () {
    this.emulator.stop();
  };

  V86Engine.prototype.resume = function () {
    this.emulator.run();
  };

  V86Engine.prototype.snapshot = function () {
    return this.emulator.save_state();
  };

  V86Engine.prototype.restore = function (buffer) {
    return this.emulator.restore_state(buffer);
  };

  V86Engine.prototype.screenshot = function () {
    var canvas = document.querySelector('#screen_container canvas');
    if (!canvas || !canvas.width) return null;
    return canvas.toDataURL('image/png');
  };

  // ------------------------------------------------------------- engine registry

  var engines = { v86: V86Engine };

  // Engine B registers itself from its own shim, so this file never needs to know it exists.
  window.DRAUGR_REGISTER_ENGINE = function (name, factory) {
    engines[name] = factory;
  };

  var active = null;

  window.DRAUGR = {
    capabilities: function () {
      return {
        crossOriginIsolated: window.crossOriginIsolated === true,
        sharedArrayBuffer: typeof SharedArrayBuffer === 'function',
        wasm: typeof WebAssembly === 'object'
      };
    },

    boot: function (config) {
      try {
        var name = (config.engine || 'v86').toLowerCase();
        var Engine = engines[name];
        if (!Engine) {
          fail('boot', new Error('unknown engine: ' + name));
          return;
        }
        active = new Engine();
        active.boot(config);
      } catch (error) {
        fail('boot', error);
      }
    },

    sendKeys: function (codes) {
      try {
        if (active) active.sendKeys(codes);
      } catch (error) {
        fail('sendKeys', error);
      }
    },

    sendText: function (text) {
      try {
        if (active) active.sendText(text);
      } catch (error) {
        fail('sendText', error);
      }
    },

    pause: function () {
      try {
        if (active) active.pause();
        emit({ type: 'state', state: 'suspended' });
      } catch (error) {
        fail('pause', error);
      }
    },

    resume: function () {
      try {
        if (active) active.resume();
        emit({ type: 'state', state: 'running' });
      } catch (error) {
        fail('resume', error);
      }
    },

    snapshot: function () {
      if (!active) return Promise.reject(new Error('no machine'));
      return Promise.resolve(active.snapshot());
    },

    restore: function (buffer) {
      if (!active) return Promise.reject(new Error('no machine'));
      return Promise.resolve(active.restore(buffer));
    },

    screenshot: function () {
      return active && active.screenshot ? active.screenshot() : null;
    },

    // Snapshots cross the bridge as base64 because neither platform channel carries binary.
    snapshotBase64: function () {
      return window.DRAUGR.snapshot().then(function (buffer) {
        var bytes = new Uint8Array(buffer);
        var chunk = 0x8000;
        var parts = [];
        for (var i = 0; i < bytes.length; i += chunk) {
          parts.push(String.fromCharCode.apply(null, bytes.subarray(i, i + chunk)));
        }
        return btoa(parts.join(''));
      });
    },

    restoreBase64: function (encoded) {
      var binary = atob(encoded);
      var bytes = new Uint8Array(binary.length);
      for (var i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      return window.DRAUGR.restore(bytes.buffer);
    },

    onEvent: function (listener) {
      listeners.push(listener);
    }
  };

  window.addEventListener('error', function (event) {
    fail('window', event.error || new Error(event.message));
  });

  window.addEventListener('unhandledrejection', function (event) {
    fail('promise', event.reason || new Error('unhandled rejection'));
  });

  emit({ type: 'state', state: 'idle' });
})();
