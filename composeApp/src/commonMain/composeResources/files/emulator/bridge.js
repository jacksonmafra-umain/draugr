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
  var fetchedBytes = 0;

  // Both engines stream disk blocks over HTTP, one through XHR and one through fetch. Counting
  // here rather than inside an engine keeps the HUD honest for whichever is running.
  (function instrumentTransports() {
    var nativeSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
      this.addEventListener('load', function () {
        var length = this.getResponseHeader && this.getResponseHeader('content-length');
        if (length) {
          fetchedBytes += parseInt(length, 10) || 0;
        } else if (this.response && this.response.byteLength) {
          fetchedBytes += this.response.byteLength;
        }
      });
      return nativeSend.apply(this, arguments);
    };

    if (typeof window.fetch === 'function') {
      var nativeFetch = window.fetch;
      window.fetch = function () {
        return nativeFetch.apply(this, arguments).then(function (response) {
          var length = response.headers.get('content-length');
          if (length) fetchedBytes += parseInt(length, 10) || 0;
          return response;
        });
      };
    }
  })();

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

  // A text screen is a character grid, not pixels. v86 renders each row as a block-level child
  // whose box is the parent's width, so measuring the DOM directly tells us nothing about the
  // real content width. Instead: set a candidate font size, measure a hidden probe of exactly
  // one row of characters in the same inherited font, then correct. Estimating the glyph
  // advance instead of measuring it clipped the last few columns on narrower screens.
  var BASE_FONT_PX = 16;
  var LINE_HEIGHT = 1.05;

  // 1.0 means "fit the whole 80-column screen". Above that the glyphs grow and the guest is
  // panned instead, because the column count is fixed by the guest's video mode.
  var textZoom = 1;

  function probeRowWidth(container, fontPx, columns, fontFamily) {
    var probe = document.createElement('span');
    probe.style.position = 'absolute';
    probe.style.visibility = 'hidden';
    probe.style.whiteSpace = 'pre';
    probe.style.fontSize = fontPx + 'px';
    probe.style.fontFamily = fontFamily || 'monospace';
    probe.textContent = new Array(columns + 1).join('M');
    container.appendChild(probe);
    var width = probe.getBoundingClientRect().width;
    container.removeChild(probe);
    return width;
  }

  /**
   * Width of a row as actually laid out. A Range measures the inline extent of the text inside
   * a block element, which is the only reliable number here: the row's own box is the parent's
   * width, and a probe span can pick up a different font than the one v86 renders with.
   */
  function renderedRowWidth(text) {
    var widest = 0;
    for (var i = 0; i < text.children.length; i++) {
      var row = text.children[i];
      if (!row.firstChild) continue;
      var range = document.createRange();
      range.selectNodeContents(row);
      var width = range.getBoundingClientRect().width;
      range.detach && range.detach();
      if (width > widest) widest = width;
    }
    return widest;
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

    // Scale rather than restyle. v86 rewrites the text screen's inline font-size on every
    // redraw, so anything set there is gone by the next frame; it never touches transform.
    text.style.transformOrigin = 'top left';
    text.style.transform = 'none';
    text.style.width = 'max-content';
    text.style.overflow = 'visible';

    var box = text.getBoundingClientRect();
    var width = Math.max(renderedRowWidth(text), box.width);
    var height = box.height;
    if (!width) {
      width = probeRowWidth(container, BASE_FONT_PX, this.cols || 80, 'monospace');
    }
    if (!width || !height) return;

    // A hair under a perfect fit: sub-pixel advances round up and clip the last column.
    var fit = Math.min(window.innerWidth / width, window.innerHeight / height) * 0.99;
    var scale = fit * textZoom;
    if (!(scale > 0) || !isFinite(scale)) return;
    text.style.transform = 'scale(' + scale + ')';

    // Panning is only offered when there is something off-screen to pan to.
    var overflows = width * scale > window.innerWidth || height * scale > window.innerHeight;
    container.style.overflow = overflows ? 'auto' : 'hidden';
    container.style.webkitOverflowScrolling = 'touch';
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
      // Guest networking, only when the app supplies a relay. Without it the NE2000 has no peer
      // and the guest stays offline, which is the default.
      network_relay_url: config.networkRelayUrl || undefined,
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
    if (config.initialStateBuffer) {
      options.initial_state = { buffer: config.initialStateBuffer };
    } else if (config.stateImage) {
      options.initial_state = { url: config.stateImage };
    }

    this.emulator = new V86(options);

    this.emulator.add_listener('emulator-ready', function () {
      emit({ type: 'state', state: 'booting' });
    });

    this.emulator.add_listener('emulator-loaded', function () {
      emit({ type: 'state', state: 'running' });
      self.startStats();
      // The widest row only exists once the guest has printed one, so refit for a short while.
      var refits = 0;
      var handle = setInterval(function () {
        self.fitTextScreen();
        if (++refits > 12) clearInterval(handle);
      }, 500);
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

    // A web view can be resized without firing window.resize, for instance when the host
    // rotates and re-letterboxes the surface. Watch the element itself.
    if (typeof ResizeObserver === 'function') {
      new ResizeObserver(function () { self.fitTextScreen(); })
        .observe(document.getElementById('screen_container'));
      new ResizeObserver(function () { self.fitTextScreen(); }).observe(document.documentElement);
    }

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

  V86Engine.prototype.startStats = function () {
    var self = this;
    var previous = 0;
    this.statsHandle = setInterval(function () {
      var counter = 0;
      try {
        counter = self.emulator.get_instruction_counter();
      } catch (ignored) {
        counter = 0;
      }
      var delta = counter >= previous ? counter - previous : 0;
      previous = counter;
      emit({ type: 'stats', ips: delta, fetchedBytes: fetchedBytes });
    }, 1000);
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
    // In text mode the canvas exists but is blank, so capturing it would store a black
    // rectangle and claim it was a framebuffer.
    if (!this.graphical) return null;
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

  // Shims emit through the same channel as the built-in engine.
  window.DRAUGR_EMIT = emit;

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var element = document.createElement('script');
      element.src = src;
      element.onload = function () { resolve(); };
      element.onerror = function () { reject(new Error('cannot load ' + src)); };
      document.head.appendChild(element);
    });
  }

  /**
   * Engine B is a local build, not a vendored artifact, so it may simply be absent. Load it on
   * first use and say plainly what to run when it is missing.
   */
  function ensureEngine(name) {
    if (engines[name]) return Promise.resolve(engines[name]);
    if (name !== 'tinyemu') return Promise.reject(new Error('unknown engine: ' + name));
    return loadScript('tinyemu/tinyemu64.js')
      .then(function () { return loadScript('tinyemu-shim.js'); })
      .then(function () {
        if (!engines[name]) {
          throw new Error('engine not built: run emulator/build-tinyemu.sh');
        }
        return engines[name];
      })
      .catch(function () {
        throw new Error('engine not built: run emulator/build-tinyemu.sh');
      });
  }

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
      // Booting twice would leave two machines fighting over the same screen.
      if (active) {
        fail('boot', new Error('a machine is already running'));
        return;
      }
      var name = (config.engine || 'v86').toLowerCase();
      ensureEngine(name)
        .then(function (Engine) {
          active = new Engine();
          active.boot(config);
        })
        .catch(function (error) {
          fail('boot', error);
        });
    },

    /** True once a machine exists in this page, which decides restore versus cold boot. */
    hasMachine: function () {
      return active !== null;
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

    /**
     * Cold start straight into a saved state, fetched from the local server. Used after the host
     * process has been killed, when the page is new and there is no machine to restore into.
     */
    bootWithStateUrl: function (config, url) {
      config.stateImage = url;
      window.DRAUGR.boot(config);
    },

    /** Restores into the machine already in this page, fetching the state over HTTP. */
    restoreFromUrl: function (url) {
      return fetch(url)
        .then(function (response) {
          if (!response.ok) throw new Error('state fetch failed: ' + response.status);
          return response.arrayBuffer();
        })
        .then(function (buffer) {
          return window.DRAUGR.restore(buffer);
        })
        .then(function () {
          return 'ok';
        });
    },

    /** 1.0 fits the whole screen; larger grows the glyphs and lets the guest be panned. */
    setTextZoom: function (factor) {
      var value = parseFloat(factor);
      if (!(value > 0) || !isFinite(value)) return;
      textZoom = value;
      if (active && active.fitTextScreen) active.fitTextScreen();
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
