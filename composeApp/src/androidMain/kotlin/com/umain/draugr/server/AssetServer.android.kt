package com.umain.draugr.server

import java.security.SecureRandom

actual class AssetServer actual constructor(provider: AssetProvider) {

    private val delegate = LoopbackServer(randomToken(), provider)

    actual suspend fun start(): String = delegate.start()

    actual fun stop() = delegate.stop()
}

actual fun randomToken(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.toHexToken()
}
