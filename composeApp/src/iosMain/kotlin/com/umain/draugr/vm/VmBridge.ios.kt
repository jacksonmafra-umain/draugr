package com.umain.draugr.vm

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
import platform.WebKit.WKWebView

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

    private var webView: WKWebView? = null

    actual fun prepare(hostUrl: String) {
        this.hostUrl = hostUrl
    }

    internal fun attach(view: WKWebView) {
        webView = view
    }

    /** The live view, so the surface can re-adopt it instead of building a new page. */
    internal fun retainedView(): WKWebView? = webView

    actual fun dispose() {
        webView = null
        ready.value = false
    }

    /** Called from the `draugr` script message handler. */
    internal fun onMessage(raw: String) {
        val reply = BridgeProtocol.parseReplyOrNull(raw)
        if (reply != null) {
            pending.remove(reply.first)?.complete(reply.second)
            return
        }
        val event = BridgeProtocol.parseEvent(raw) ?: return
        if (event is VmEvent.StateChanged && event.state is VmState.Idle) {
            ready.value = true
        }
        _events.tryEmit(event)
    }

    /** WebKit insists that `evaluateJavaScript` is called on the main queue. */
    private suspend fun evaluate(script: String) {
        val view = webView ?: return
        withContext(Dispatchers.Main) {
            view.evaluateJavaScript(script) { _, _ -> }
        }
    }

    private suspend fun request(expression: String): String? {
        val id = ++requestId
        val deferred = CompletableDeferred<String?>()
        pending[id] = deferred
        evaluate(
            """
            (function () {
              function reply(value) {
                window.webkit.messageHandlers.draugr.postMessage(
                  JSON.stringify({ type: "reply", id: "$id", payload: value === null ? null : String(value) })
                );
              }
              try {
                Promise.resolve($expression).then(reply).catch(function () { reply(null); });
              } catch (error) { reply(null); }
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
        ready.first { it }
        evaluate("window.DRAUGR.setTextZoom($factor);")
    }

    actual suspend fun pause() = evaluate("window.DRAUGR.pause();")

    actual suspend fun resume() = evaluate("window.DRAUGR.resume();")

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun snapshot(): ByteArray {
        val encoded = request("window.DRAUGR.snapshotBase64()") ?: error("snapshot failed")
        return Base64.decode(encoded)
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun restore(data: ByteArray) {
        evaluate("window.DRAUGR.restoreBase64(\"${Base64.encode(data)}\");")
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun screenshot(): ByteArray? {
        val dataUrl = request("window.DRAUGR.screenshot()") ?: return null
        val payload = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        if (payload.isEmpty()) return null
        return runCatching { Base64.decode(payload) }.getOrNull()
    }

    /**
     * WebContent died, almost always jetsam. The page is gone, so the guest is suspended and
     * the screen can offer a restore instead of the app crashing.
     */
    internal fun onHostTerminated() {
        ready.value = false
        _events.tryEmit(
            VmEvent.StateChanged(VmState.Suspended(SuspendReason.HOST_TERMINATED)),
        )
    }

    /** Forwarded to the guest so X11 reconfigures when the surface changes size. */
    suspend fun notifyResize(width: Int, height: Int) {
        evaluate("window.dispatchEvent(new CustomEvent('draugr-resize', { detail: { width: $width, height: $height } }));")
    }
}
