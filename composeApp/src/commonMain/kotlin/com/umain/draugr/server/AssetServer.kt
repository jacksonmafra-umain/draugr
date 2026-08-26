package com.umain.draugr.server

/**
 * The emulator payload has to come over HTTP, not `file://`: cross-origin isolation is never
 * granted to file origins, `WKWebView` refuses fetch from them, and disk images need Range
 * requests so the guest streams blocks instead of loading a whole image into RAM.
 */
expect class AssetServer(provider: AssetProvider) {
    /** Starts on 127.0.0.1 with an ephemeral port. Returns the token-scoped origin. */
    suspend fun start(): String

    fun stop()
}

/** Random path segment guarding the mount. One per server instance. */
expect fun randomToken(): String
