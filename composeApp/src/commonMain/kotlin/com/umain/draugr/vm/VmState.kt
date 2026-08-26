package com.umain.draugr.vm

import kotlin.time.Instant

enum class SuspendReason {
    /** The user asked for it. */
    USER,

    /** The app went to the background; the guest was snapshotted first. */
    BACKGROUNDED,

    /** WebContent died. On iOS this is jetsam, and it is the common case. */
    HOST_TERMINATED,
}

sealed interface VmState {
    data object Idle : VmState
    data class Fetching(val asset: String, val received: Long, val total: Long) : VmState
    data class Booting(val elapsedMs: Long) : VmState
    data class Running(val since: Instant) : VmState
    data class Suspended(val reason: SuspendReason) : VmState
    data class Halted(val error: String?) : VmState
}

sealed interface VmEvent {
    data class Serial(val line: String) : VmEvent
    data class StateChanged(val state: VmState) : VmEvent
    data class ScreenResized(val w: Int, val h: Int, val graphical: Boolean) : VmEvent
    data class Fault(val message: String) : VmEvent
}

val VmState.label: String
    get() = when (this) {
        VmState.Idle -> "IDLE"
        is VmState.Fetching -> "FETCHING"
        is VmState.Booting -> "BOOTING"
        is VmState.Running -> "RUNNING"
        is VmState.Suspended -> "SUSPENDED"
        is VmState.Halted -> "HALTED"
    }

val VmState.isLive: Boolean
    get() = this is VmState.Running || this is VmState.Booting || this is VmState.Fetching
