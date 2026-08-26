package com.umain.draugr.vm

import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.server.AssetServer
import com.umain.draugr.server.draugrAssetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the server and the bridge for one machine, and turns the event stream into the state the
 * screen renders. Everything here is shared: the platform only supplies the web view.
 */
class VmController(
    private val spec: MachineSpec,
    private val scope: CoroutineScope,
    val bridge: VmBridge = VmBridge(),
    private val server: AssetServer = AssetServer(draugrAssetProvider()),
) {
    private val _state = MutableStateFlow<VmState>(VmState.Idle)
    val state: StateFlow<VmState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _geometry = MutableStateFlow(Geometry.UNKNOWN)
    val geometry: StateFlow<Geometry> = _geometry.asStateFlow()

    private var origin: String? = null

    fun start() {
        scope.launch {
            runCatching { server.start() }
                .onSuccess { serverOrigin ->
                    origin = serverOrigin
                    bridge.prepare(serverOrigin)
                    collectEvents()
                    boot(serverOrigin)
                }
                .onFailure { failure ->
                    _state.value = VmState.Halted(failure.message ?: "SERVER DID NOT START")
                }
        }
    }

    private fun collectEvents() {
        scope.launch {
            bridge.events.collect { event ->
                when (event) {
                    is VmEvent.Serial -> appendLog(event.line)
                    is VmEvent.StateChanged -> {
                        // The page reports Idle once bridge.js has loaded, which happens before
                        // a boot has been asked for. Do not let it clobber a live machine.
                        if (event.state !is VmState.Idle || _state.value == VmState.Idle) {
                            _state.value = event.state
                        }
                    }
                    is VmEvent.ScreenResized ->
                        _geometry.value = Geometry(event.w, event.h, event.graphical)
                    is VmEvent.Fault -> {
                        appendLog(":: ${event.message}")
                        _state.value = VmState.Halted(event.message)
                    }
                }
            }
        }
    }

    private fun boot(serverOrigin: String) {
        scope.launch {
            _state.value = VmState.Booting(elapsedMs = 0L)
            runCatching { bridge.boot(spec, serverOrigin) }
                .onFailure { _state.value = VmState.Halted(it.message ?: "BOOT FAILED") }
        }
    }

    fun pause() = scope.launch {
        bridge.pause()
        _state.value = VmState.Suspended(SuspendReason.USER)
    }

    fun resume() = scope.launch {
        bridge.resume()
        _state.value = VmState.Running(since = nowInstant())
    }

    fun reset() {
        val serverOrigin = origin ?: return
        _log.value = emptyList()
        boot(serverOrigin)
    }

    fun sendKeys(codes: IntArray) = scope.launch { bridge.sendKeys(codes) }

    fun sendText(text: String) = scope.launch { bridge.sendText(text) }

    suspend fun snapshot(): ByteArray = bridge.snapshot()

    suspend fun restore(data: ByteArray) {
        bridge.restore(data)
        _state.value = VmState.Running(since = nowInstant())
    }

    suspend fun screenshot(): ByteArray? = bridge.screenshot()

    fun halt() {
        _state.value = VmState.Halted(error = null)
        server.stop()
    }

    private fun appendLog(line: String) {
        val current = _log.value
        _log.value = if (current.size >= LOG_LIMIT) {
            current.drop(current.size - LOG_LIMIT + 1) + line
        } else {
            current + line
        }
    }

    private companion object {
        // Enough to see a full boot, bounded so a chatty guest cannot exhaust memory.
        const val LOG_LIMIT = 2000
    }
}
