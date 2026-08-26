package com.umain.draugr.vm

import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.platform.HostLifecycle
import com.umain.draugr.platform.platformMemoryCeilingMb
import com.umain.draugr.server.AssetServer
import com.umain.draugr.catalog.Engine
import com.umain.draugr.server.GeneratedAssets
import com.umain.draugr.server.draugrAssetProvider
import com.umain.draugr.storage.SnapshotEntry
import com.umain.draugr.storage.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    /**
     * Its own scope, not a composition scope: the controller outlives the screen so that a trip
     * to the state list does not tear down a running guest.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    val bridge: VmBridge = VmBridge(),
    private val generated: GeneratedAssets = GeneratedAssets(),
    private val server: AssetServer = AssetServer(draugrAssetProvider(generated)),
    private val lifecycle: HostLifecycle = HostLifecycle(),
    private val snapshots: SnapshotStore = SnapshotStore(),
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
    private var lastSnapshotEntry: SnapshotEntry? = null

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

    private val _stats = MutableStateFlow(VmEvent.Stats(0L, 0L))
    val stats: StateFlow<VmEvent.Stats> = _stats.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private var origin: String? = null

    private var started = false

    /**
     * Idempotent. The screen can be re-entered, and starting a second server left the page on
     * one origin with its assets on another, which cross-origin isolation correctly refuses.
     */
    fun start() {
        if (started) return
        started = true
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
                        // Persisted, not just held: on iOS the process itself may not survive.
                        runCatching { snapshotNow() }
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
        val entry = lastSnapshotEntry ?: snapshots.latest(spec.id) ?: return false
        applyState(entry)
        return _state.value is VmState.Running
    }

    val canRestore: Boolean
        get() = lastSnapshotEntry != null || snapshots.latest(spec.id) != null

    fun savedSnapshots(): List<SnapshotEntry> = snapshots.list(spec.id)

    fun thumbnailOf(entry: SnapshotEntry): ByteArray? =
        runCatching { snapshots.readThumbnail(entry) }.getOrNull()

    /**
     * Fire and forget entry points for the UI. A screen's `rememberCoroutineScope` dies the
     * moment it is navigated away from, which is exactly when a restore is asked for, so this
     * work belongs to the controller's own scope.
     */
    fun requestRestore(entry: SnapshotEntry) = scope.launch { applyState(entry) }

    fun requestRestoreLatest() = scope.launch { restoreLastSnapshot() }

    fun requestSnapshot() = scope.launch { runCatching { snapshotNow() } }

    /**
     * Restoring into a live machine is not the same operation as cold starting into a saved
     * state: booting a second machine into the same page leaves two of them fighting over the
     * screen. Either way the state travels over the loopback server, because a ten megabyte
     * base64 string does not survive the JS bridge.
     */
    private suspend fun applyState(entry: SnapshotEntry) {
        val serverOrigin = origin ?: return
        val path = snapshots.serverPathOf(entry)
        val alreadyRunning = runCatching { bridge.hasMachine() }.getOrDefault(false)
        runCatching {
            if (alreadyRunning) {
                if (!bridge.restoreFrom(serverOrigin, path)) error("STATE FETCH FAILED")
            } else {
                bridge.bootWithState(spec, serverOrigin, path)
            }
        }.onSuccess {
            intendedSuspendReason = null
            lastSnapshotEntry = entry
            _state.value = VmState.Running(since = nowInstant())
        }.onFailure { failure ->
            val message = failure.message ?: "RESTORE FAILED"
            appendLog(":: RESTORE FAILED: $message")
            _state.value = VmState.Halted(message)
        }
    }

    fun delete(entry: SnapshotEntry) = snapshots.delete(entry)

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
                    is VmEvent.Stats -> _stats.value = event
                    is VmEvent.ScreenResized -> {
                        _geometry.value = Geometry(event.w, event.h, event.graphical)
                        // A mode change refits the screen, which resets the applied scale.
                        if (_zoom.value != 1f) setZoom(_zoom.value)
                    }
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
            // TinyEMU fetches its own config file, so it has to exist before the boot call.
            if (spec.engine == Engine.TINYEMU) {
                generated.put(
                    TinyEmuConfig.pathFor(spec),
                    TinyEmuConfig.render(spec, serverOrigin),
                )
            }
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

    fun setZoom(factor: Float) {
        _zoom.value = factor
        scope.launch { runCatching { bridge.setTextZoom(factor) } }
    }

    fun sendText(text: String) = scope.launch { bridge.sendText(text) }

    suspend fun snapshot(): ByteArray = bridge.snapshot()

    suspend fun restore(data: ByteArray) {
        bridge.restore(data)
        _state.value = VmState.Running(since = nowInstant())
    }

    suspend fun screenshot(): ByteArray? = bridge.screenshot()

    /** Saves state plus a framebuffer thumbnail, then trims the machine's history. */
    suspend fun snapshotNow(): SnapshotEntry {
        val state = bridge.snapshot()
        val thumbnail = runCatching { bridge.screenshot() }.getOrNull()
        val entry = snapshots.save(spec.id, state, thumbnail)
        lastSnapshotEntry = entry
        snapshots.prune(spec.id)
        return entry
    }

    fun halt() {
        started = false
        _state.value = VmState.Halted(error = null)
        lifecycle.dispose()
        server.stop()
        scope.cancel()
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
