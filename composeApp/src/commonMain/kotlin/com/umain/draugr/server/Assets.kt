package com.umain.draugr.server

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** One servable byte range source. Disk images are never held in memory whole. */
interface AssetHandle {
    val size: Long
    val contentType: String

    /** Writes [length] bytes starting at [offset] into [out]. */
    suspend fun writeTo(out: ByteWriteChannel, offset: Long, length: Long)
}

interface AssetProvider {
    suspend fun open(path: String): AssetHandle?
}

private const val CHUNK = 64 * 1024

class MemoryAsset(
    private val bytes: ByteArray,
    override val contentType: String,
) : AssetHandle {
    override val size: Long get() = bytes.size.toLong()

    override suspend fun writeTo(out: ByteWriteChannel, offset: Long, length: Long) {
        var written = 0L
        while (written < length) {
            val take = minOf(CHUNK.toLong(), length - written).toInt()
            val start = (offset + written).toInt()
            // writeFully takes an end index, not a count. Passing a count here silently
            // truncates every response past the first chunk.
            out.writeFully(bytes, startIndex = start, endIndex = start + take)
            written += take
        }
    }
}

class FileAsset(
    private val fileSystem: FileSystem,
    private val path: Path,
    override val contentType: String,
    override val size: Long,
) : AssetHandle {
    override suspend fun writeTo(out: ByteWriteChannel, offset: Long, length: Long) {
        fileSystem.source(path).buffer().use { source ->
            source.skip(offset)
            val buffer = ByteArray(CHUNK)
            var written = 0L
            while (written < length) {
                val want = minOf(CHUNK.toLong(), length - written).toInt()
                val read = source.read(buffer, 0, want)
                if (read == -1) break
                out.writeFully(buffer, startIndex = 0, endIndex = read)
                written += read
            }
        }
    }
}

/** Serves the vendored emulator payload that ships inside the app bundle. */
class BundledAssetProvider(
    private val read: suspend (String) -> ByteArray?,
) : AssetProvider {
    override suspend fun open(path: String): AssetHandle? {
        val bytes = read(path) ?: return null
        return MemoryAsset(bytes, contentTypeFor(path))
    }
}

/** Serves guest disk images from app storage, streamed by range. */
class FileAssetProvider(
    private val fileSystem: FileSystem,
    private val root: Path,
) : AssetProvider {
    override suspend fun open(path: String): AssetHandle? {
        if (!path.isSafeRelativePath()) return null
        val target = root / path
        val metadata = fileSystem.metadataOrNull(target) ?: return null
        if (!metadata.isRegularFile) return null
        val size = metadata.size ?: return null
        return FileAsset(fileSystem, target, contentTypeFor(path), size)
    }
}

/**
 * Tries each provider in order. Guest images can come either from the app bundle or, once
 * sideloaded, from app storage, and the caller should not care which.
 */
class FallbackAssetProvider(
    private val providers: List<AssetProvider>,
) : AssetProvider {
    override suspend fun open(path: String): AssetHandle? {
        providers.forEach { provider ->
            provider.open(path)?.let { return it }
        }
        return null
    }
}

/** Dispatches by path prefix, longest prefix first. */
class PrefixAssetProvider(
    private val providers: List<Pair<String, AssetProvider>>,
) : AssetProvider {
    override suspend fun open(path: String): AssetHandle? {
        val match = providers
            .filter { (prefix, _) -> prefix.isEmpty() || path == prefix || path.startsWith("$prefix/") }
            .maxByOrNull { (prefix, _) -> prefix.length }
            ?: return null
        val (prefix, provider) = match
        val remainder = if (prefix.isEmpty()) path else path.removePrefix(prefix).trimStart('/')
        return provider.open(remainder)
    }
}

internal fun String.isSafeRelativePath(): Boolean =
    isNotEmpty() &&
        !startsWith("/") &&
        !contains("\\") &&
        split('/').none { it == ".." || it == "." || it.isEmpty() }

internal fun contentTypeFor(path: String): String = when (path.substringAfterLast('.', "")) {
    "html" -> "text/html; charset=utf-8"
    "js", "mjs" -> "text/javascript; charset=utf-8"
    "json" -> "application/json; charset=utf-8"
    "wasm" -> "application/wasm"
    "css" -> "text/css; charset=utf-8"
    "png" -> "image/png"
    "svg" -> "image/svg+xml"
    "txt" -> "text/plain; charset=utf-8"
    else -> "application/octet-stream"
}
