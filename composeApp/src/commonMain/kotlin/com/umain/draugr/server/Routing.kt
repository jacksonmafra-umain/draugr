package com.umain.draugr.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * `SharedArrayBuffer` is only handed out to a cross-origin isolated document, which means every
 * response has to carry these three. Without them the emulator falls back to a single-threaded
 * path, or refuses to start at all.
 */
internal val ISOLATION_HEADERS = listOf(
    "Cross-Origin-Opener-Policy" to "same-origin",
    "Cross-Origin-Embedder-Policy" to "require-corp",
    "Cross-Origin-Resource-Policy" to "same-origin",
)

data class RangeRequest(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

/**
 * Parses a single-range `bytes=` header. Multi-range is rejected on purpose: the emulator only
 * ever asks for one span, and honouring more would mean multipart bodies for no gain.
 */
fun parseRange(header: String?, size: Long): RangeRequest? {
    if (header == null) return null
    val spec = header.trim().removePrefix("bytes=").trim()
    if (spec.isEmpty() || spec.contains(',')) return null
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val rawStart = spec.substring(0, dash).trim()
    val rawEnd = spec.substring(dash + 1).trim()

    return when {
        rawStart.isEmpty() -> {
            val suffix = rawEnd.toLongOrNull() ?: return null
            if (suffix <= 0L) return null
            val start = (size - suffix).coerceAtLeast(0L)
            RangeRequest(start, size - 1)
        }

        rawEnd.isEmpty() -> {
            val start = rawStart.toLongOrNull() ?: return null
            if (start >= size) return null
            RangeRequest(start, size - 1)
        }

        else -> {
            val start = rawStart.toLongOrNull() ?: return null
            val end = rawEnd.toLongOrNull() ?: return null
            if (start > end || start >= size) return null
            RangeRequest(start, end.coerceAtMost(size - 1))
        }
    }
}

/**
 * Mounts the asset tree under a one-shot random token so nothing else listening on the
 * loopback interface can enumerate it.
 */
fun Application.draugrAssetModule(token: String, provider: AssetProvider) {
    install(DefaultHeaders) {
        ISOLATION_HEADERS.forEach { (name, value) -> header(name, value) }
        header(HttpHeaders.AcceptRanges, "bytes")
        header(HttpHeaders.CacheControl, "no-store")
    }

    routing {
        get("/{token}/{path...}") {
            if (call.parameters["token"] != token) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
            val requested = if (path.isEmpty() || path.endsWith("/")) "${path}host.html" else path
            val handle = provider.open(requested)
            if (handle == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val rangeHeader = call.request.header(HttpHeaders.Range)
            if (rangeHeader != null) {
                val range = parseRange(rangeHeader, handle.size)
                if (range == null) {
                    call.response.header(HttpHeaders.ContentRange, "bytes */${handle.size}")
                    call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                    return@get
                }
                call.response.header(
                    HttpHeaders.ContentRange,
                    "bytes ${range.start}-${range.endInclusive}/${handle.size}",
                )
                call.respondBytesWriter(
                    contentType = ContentType.parse(handle.contentType),
                    status = HttpStatusCode.PartialContent,
                    contentLength = range.length,
                ) {
                    handle.writeTo(this, range.start, range.length)
                }
                return@get
            }

            call.respondBytesWriter(
                contentType = ContentType.parse(handle.contentType),
                status = HttpStatusCode.OK,
                contentLength = handle.size,
            ) {
                handle.writeTo(this, 0L, handle.size)
            }
        }
    }
}
