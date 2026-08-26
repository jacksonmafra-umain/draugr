package com.umain.draugr.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

internal const val LOOPBACK = "127.0.0.1"

internal fun originFor(port: Int, token: String): String = "http://$LOOPBACK:$port/$token"

internal fun ByteArray.toHexToken(): String =
    joinToString("") { byte ->
        val v = byte.toInt() and 0xFF
        val hi = "0123456789abcdef"[v shr 4]
        val lo = "0123456789abcdef"[v and 0x0F]
        "$hi$lo"
    }

/**
 * Shared engine plumbing. Both platforms run the same CIO server; the only thing that differs
 * is where the random token comes from.
 */
internal class LoopbackServer(
    private val token: String,
    private val provider: AssetProvider,
) {
    private var server: EmbeddedServer<*, *>? = null
    private var engineFailure: Throwable? = null

    /**
     * A bind failure surfaces on the engine's own accept coroutine. Without a handler on the
     * parent scope it reaches the platform default handler and takes the process down.
     */
    suspend fun start(): String {
        val scope = CoroutineScope(
            SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) engineFailure = throwable
            },
        )
        val engine = with(scope) {
            embeddedServer(CIO, port = 0, host = LOOPBACK) {
                draugrAssetModule(token, provider)
            }
        }
        engine.startSuspend(wait = false)
        server = engine
        engineFailure?.let { throw it }
        val port = engine.engine.resolvedConnectors().first().port
        return originFor(port, token)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        server = null
    }
}
