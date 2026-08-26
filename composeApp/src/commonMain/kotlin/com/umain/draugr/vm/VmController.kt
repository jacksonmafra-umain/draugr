package com.umain.draugr.vm

import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.platform.HostLifecycle
import com.umain.draugr.platform.platformMemoryCeilingMb
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
    spec: MachineSpec,
    private val scope: CoroutineScope,
    val bridge: VmBridge = VmBridge(),
    private val server: AssetServer = AssetServer(draugrAssetProvider()),
    private val lifecycle: HostLifecycle = HostLifecycle(),
) {
    /**
     * Asking for more memory than the platform tolerates does not fail gracefully: on iOS the
     * whole web content process is killed mid-boot. Clamp instead.
     */
    private val spec: MachineSpec = platformMemoryCeilingMb()
        ?.takeIf { it < spec.memMb }
        ?.let { ceiling -> spec.copy(memMb = ceiling) }
        ?: spec

    val memoryWasClamped: Boolean = this.spec.memMb != spec.memMb

    /** Held so a guest can come back after the host process is killed under memory pressure. */
    private var lastSnapshot: ByteArray? = null

    /**
     * Why we asked the guest to stop. Pausing makes the engine report a plain "suspended"
     * event of its own, which must not overwrite a more specific reason such as backgrounding.
     */
    private var intendedSuspendReason: SuspendReason? = null
    private val _state = MutableStateFlow<VmState>(VmState.Idle)
    val state: StateFlow<VmState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _geometry = MutableStateFlow(Geometry.UNKNOWN)
    val geometry: StateFlow<Geometry> = _geometry.asStateFlow()

    private var origin: String? = null

    fun start() {
        observeLifecycle()
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

    private fun observeLifecycle() {
        lifecycle.observe(
            onBackground = {
                if (_state.value is VmState.Running) {
                    intendedSuspendReason = SuspendReason.BACKGROUNDED
                    scope.launch {
                        runCatching { bridge.snapshot() }.onSuccess { lastSnapshot = it }
                        bridge.pause()
                        _state.value = VmState.Suspended(SuspendReason.BACKGROUNDED)
                    }
                }
            },
            onForeground = {
                val suspended = _state.value as? VmState.Suspended ?: return@observe
                scope.launch {
                    when (suspended.reason) {
                        SuspendReason.BACKGROUNDED -> resumeQuietly()
                        SuspendReason.HOST_TERMINATED -> restoreLastSnapshot()
                        SuspendReason.USER -> Unit
                    }
                }
            },
        )
    }

    private suspend fun resumeQuietly() {
        runCatching { bridge.resume() }
            .onSuccess {
                intendedSuspendReason = null
                _state.value = VmState.Running(since = nowInstant())
            }
    }

    /** Offered after WebContent dies: the page is gone, so the guest is rebuilt from state. */
    suspend fun restoreLastSnapshot(): Boolean {
        val snapshot = lastSnapshot ?: return false
        val serverOrigin = origin ?: return false
        runCatching {
            bridge.boot(this.spec, serverOrigin)
            bridge.restore(snapshot)
        }.onFailure {
            _state.value = VmState.Halted(it.message ?: "RESTORE FAILED")
            return false
        }
        _state.value = VmState.Running(since = nowInstant())
        return true
    }

    val canRestore: Boolean get() = lastSnapshot != null

    private fun collectEvents() {
        scope.launch {
            bridge.events.collect { event ->
                when (event) {
                    is VmEvent.Serial -> appendLog(event.line)
                    is VmEvent.StateChanged -> {
                        val incoming = event.state
                        when {
                            // The page reports Idle once bridge.js has loaded, which happens
                            // before a boot has been asked for. Do not clobber a live machine.
                            incoming is VmState.Idle && _state.value != VmState.Idle -> Unit

                            // The engine reports its own stop with no idea why it happened.
                            incoming is VmState.Suspended -> {
                                _state.value = VmState.Suspended(
                                    intendedSuspendReason ?: incoming.reason,
                                )
                            }

                            else -> {
                                if (incoming is VmState.Running) intendedSuspendReason = null
                                _state.value = incoming
                            }
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
        intendedSuspendReason = SuspendReason.USER
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

    suspend fun snapshotNow(): ByteArray = bridge.snapshot().also { lastSnapshot = it }

    fun halt() {
        _state.value = VmState.Halted(error = null)
        lifecycle.dispose()
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
