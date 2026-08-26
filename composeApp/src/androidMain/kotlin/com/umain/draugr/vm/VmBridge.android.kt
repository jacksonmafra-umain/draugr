package com.umain.draugr.vm

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umain.draugr.catalog.MachineSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

actual class VmBridge actual constructor() {

    private val _events = MutableSharedFlow<VmEvent>(replay = 0, extraBufferCapacity = 512)
    actual val events: Flow<VmEvent> = _events.asSharedFlow()

    private val ready = MutableStateFlow(false)
    private val pending = mutableMapOf<Long, CompletableDeferred<String?>>()
    private var requestId = 0L

    // Compose state, not a plain field: the surface composes before the server has a port,
    // and it has to recompose once the origin arrives.
    internal var hostUrl: String? by mutableStateOf(null)
        private set

    private var webView: WebView? = null

    actual fun prepare(hostUrl: String) {
        this.hostUrl = hostUrl
    }

    internal fun attach(view: WebView) {
        webView = view
    }

    /** The live view, so the surface can re-adopt it instead of building a new page. */
    internal fun retainedView(): WebView? = webView

    actual fun dispose() {
        val view = webView
        webView = null
        ready.value = false
        view?.destroy()
    }

    /**
     * Exposed to the page as `DraugrNative`. Every method here is called on the WebView's
     * JavaBridge thread, never on the main thread.
     */
    internal val nativeInterface: Any = object {
        @JavascriptInterface
        fun event(json: String) {
            val event = BridgeProtocol.parseEvent(json) ?: return
            if (event is VmEvent.StateChanged && event.state is VmState.Idle) {
                ready.value = true
            }
            _events.tryEmit(event)
        }

        @JavascriptInterface
        fun reply(id: String, payload: String?) {
            val key = id.toLongOrNull() ?: return
            synchronized(pending) { pending.remove(key) }?.complete(payload)
        }
    }

    private suspend fun evaluate(script: String) {
        val view = webView ?: return
        withContext(Dispatchers.Main) { view.evaluateJavascript(script, null) }
    }

    /** Bridges a JS promise back to a coroutine through the `reply` callback. */
    private suspend fun request(expression: String): String? {
        val id = synchronized(pending) { ++requestId }
        val deferred = CompletableDeferred<String?>()
        synchronized(pending) { pending[id] = deferred }
        evaluate(
            """
            (function () {
              try {
                Promise.resolve($expression)
                  .then(function (value) { DraugrNative.reply("$id", value === null ? null : String(value)); })
                  .catch(function (error) { DraugrNative.reply("$id", null); });
              } catch (error) { DraugrNative.reply("$id", null); }
            })();
            """.trimIndent(),
        )
        return deferred.await()
    }

    actual suspend fun boot(spec: MachineSpec, serverOrigin: String) {
        ready.first { it }
        evaluate(BridgeProtocol.bootCall(spec, serverOrigin))
    }

    actual suspend fun bootWithState(
        spec: MachineSpec,
        serverOrigin: String,
        statePath: String,
    ) {
        ready.first { it }
        val config = BridgeProtocol.bootConfig(spec, serverOrigin)
        evaluate("window.DRAUGR.bootWithStateUrl($config, \"$serverOrigin/$statePath\");")
    }

    actual suspend fun restoreFrom(serverOrigin: String, statePath: String): Boolean {
        if (webView == null) return false
        return request("window.DRAUGR.restoreFromUrl(\"$serverOrigin/$statePath\")") == "ok"
    }

    /**
     * Answers false rather than waiting when no view is attached: the state list has no surface,
     * and a request with nowhere to go would never be replied to.
     */
    actual suspend fun hasMachine(): Boolean {
        if (webView == null) return false
        return request("window.DRAUGR.hasMachine()") == "true"
    }

    actual suspend fun sendKeys(codes: IntArray) = evaluate(BridgeProtocol.sendKeysCall(codes))

    actual suspend fun sendText(text: String) = evaluate(BridgeProtocol.sendTextCall(text))

    actual suspend fun setTextZoom(factor: Float) {
        // Zoom is applied on entry, which can beat the page's own load. Waiting for the bridge
        // to report itself avoids a `setTextZoom of undefined` in the console.
        ready.first { it }
        evaluate("window.DRAUGR.setTextZoom($factor);")
    }

    actual suspend fun pause() = evaluate("window.DRAUGR.pause();")

    actual suspend fun resume() = evaluate("window.DRAUGR.resume();")

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun snapshot(): ByteArray {
        val encoded = request("window.DRAUGR.snapshotBase64()")
            ?: error("snapshot failed")
        return Base64.decode(encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun restore(data: ByteArray) {
        val encoded = Base64.encode(data)
        evaluate("window.DRAUGR.restoreBase64(\"$encoded\");")
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun screenshot(): ByteArray? {
        val dataUrl = request("window.DRAUGR.screenshot()") ?: return null
        val payload = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        if (payload.isEmpty()) return null
        return runCatching { Base64.decode(payload) }.getOrNull()
    }
}
