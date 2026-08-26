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

    suspend fun boot(spec: MachineSpec, serverOrigin: String)
    suspend fun sendKeys(codes: IntArray)
    suspend fun sendText(text: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun snapshot(): ByteArray
    suspend fun restore(data: ByteArray)

    /** PNG bytes of the guest framebuffer, or null when the guest is in text mode. */
    suspend fun screenshot(): ByteArray?
}
