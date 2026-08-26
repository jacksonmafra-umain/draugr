// Reports host capabilities to the native side. The one that matters is crossOriginIsolated:
// without it there is no SharedArrayBuffer, and without that the emulator has no threads.
(function () {
  function send(payload) {
    var text = JSON.stringify(payload);
    if (window.DraugrNative && window.DraugrNative.report) {
      window.DraugrNative.report(text);
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.draugr) {
      window.webkit.messageHandlers.draugr.postMessage(text);
    }
  }

  function probe() {
    var report = {
      crossOriginIsolated: window.crossOriginIsolated === true,
      secureContext: window.isSecureContext === true,
      sharedArrayBuffer: typeof SharedArrayBuffer === 'function',
      wasm: typeof WebAssembly === 'object',
      wasmThreads: false,
      atomicsWait: typeof Atomics === 'object' && typeof Atomics.wait === 'function',
      workers: typeof Worker === 'function',
      userAgent: navigator.userAgent,
      origin: location.origin,
      rangeSupported: null,
      rangeStatus: null
    };

    try {
      // Threads-enabled module: (module (memory 1 1 shared))
      var bytes = new Uint8Array([
        0, 97, 115, 109, 1, 0, 0, 0,
        5, 4, 1, 3, 1, 1
      ]);
      report.wasmThreads = WebAssembly.validate(bytes);
    } catch (e) {
      report.wasmThreads = false;
    }

    var lines = [];
    Object.keys(report).forEach(function (key) {
      lines.push(key + ' = ' + report[key]);
    });
    document.getElementById('out').textContent = lines.join('\n');

    // Prove the server slices ranges: ask for the first 16 bytes of this very script.
    fetch('bridge-probe.js', { headers: { Range: 'bytes=0-15' } })
      .then(function (response) {
        report.rangeStatus = response.status;
        report.coep = response.headers.get('cross-origin-embedder-policy');
        report.coop = response.headers.get('cross-origin-opener-policy');
        report.corp = response.headers.get('cross-origin-resource-policy');
        return response.arrayBuffer();
      })
      .then(function (buffer) {
        report.rangeSupported = report.rangeStatus === 206 && buffer.byteLength === 16;
        send(report);
      })
      .catch(function (error) {
        report.rangeSupported = false;
        report.error = String(error);
        send(report);
      });
  }

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    probe();
  } else {
    document.addEventListener('DOMContentLoaded', probe);
  }
})();
