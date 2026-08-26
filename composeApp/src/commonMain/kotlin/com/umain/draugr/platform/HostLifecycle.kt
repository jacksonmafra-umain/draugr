package com.umain.draugr.platform

/**
 * Foreground and background transitions. On iOS this is not cosmetic: WebContent is killed
 * aggressively while backgrounded, so the guest has to be snapshotted before it goes away.
 */
expect class HostLifecycle() {
    fun observe(onBackground: () -> Unit, onForeground: () -> Unit)
    fun dispose()
}

/**
 * Memory ceiling the platform will tolerate for a guest, or null when there is none worth
 * enforcing. iOS WebContent gets jetsam-killed well before a large guest finishes booting.
 */
expect fun platformMemoryCeilingMb(): Int?
