package com.umain.draugr.vm

import com.umain.draugr.catalog.MachineSpec
import kotlinx.coroutines.flow.Flow

/**
 * The Kotlin side of `window.DRAUGR`. One instance per machine surface; the platform
 * implementation owns the web view and translates between JSON events and [VmEvent].
 */
expect class VmBridge() {
    val events: Flow<VmEvent>

    /** Points the surface at the server origin. Must happen before the view is composed. */
    fun prepare(hostUrl: String)

    suspend fun boot(spec: MachineSpec, serverOrigin: String, networkRelayUrl: String?)

    /**
     * Cold start into a saved state served at [statePath], relative to the server root. States
     * are megabytes, so they travel over the loopback server, not through the JS bridge.
     */
    suspend fun bootWithState(spec: MachineSpec, serverOrigin: String, statePath: String)

    /** Restores into the machine already in the page, from the same server-relative path. */
    suspend fun restoreFrom(serverOrigin: String, statePath: String): Boolean

    /** Whether the page already holds a machine, which decides restore versus cold boot. */
    suspend fun hasMachine(): Boolean
    suspend fun sendKeys(codes: IntArray)
    suspend fun sendText(text: String)

    /** Glyph scale for a text guest, as a multiplier over fit-to-width. */
    suspend fun setTextZoom(factor: Float)
    suspend fun pause()
    suspend fun resume()
    suspend fun snapshot(): ByteArray
    suspend fun restore(data: ByteArray)

    /** PNG bytes of the guest framebuffer, or null when the guest is in text mode. */
    suspend fun screenshot(): ByteArray?

    /**
     * Tears down the web view for good. Leaving the machine screen must not do this: the guest
     * lives inside the page, so destroying the view kills the machine.
     */
    fun dispose()
}
