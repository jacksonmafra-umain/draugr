package com.umain.draugr.server

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

actual class AssetServer actual constructor(provider: AssetProvider) {

    private val delegate = LoopbackServer(randomToken(), provider)

    /** Ktor's CIO engine runs on its own dispatcher, so the main queue stays with Compose. */
    actual suspend fun start(): String = delegate.start()

    actual fun stop() = delegate.stop()
}

@OptIn(ExperimentalForeignApi::class)
actual fun randomToken(): String {
    val bytes = ByteArray(16)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), pinned.addressOf(0))
    }
    return bytes.toHexToken()
}
