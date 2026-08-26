package com.umain.draugr.storage

import okio.FileSystem
import okio.Path

/** One saved machine state, plus the framebuffer thumbnail captured at save time. */
data class SnapshotEntry(
    val machineId: String,
    val id: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val hasThumbnail: Boolean,
)

/**
 * Snapshots live one directory per machine, named by the millisecond they were taken so the
 * listing sorts itself and two saves in the same second cannot collide.
 */
class SnapshotStore(
    private val fileSystem: FileSystem = platformFileSystem,
    private val root: Path = appStorageDir() / "snapshots",
    private val now: () -> Long = { currentTimeMillis() },
) {
    fun save(machineId: String, state: ByteArray, thumbnail: ByteArray? = null): SnapshotEntry {
        val id = now().toString()
        val dir = root / machineId
        fileSystem.createDirectories(dir)
        fileSystem.write(dir / "$id$STATE_SUFFIX") { write(state) }
        if (thumbnail != null && thumbnail.isNotEmpty()) {
            fileSystem.write(dir / "$id$THUMB_SUFFIX") { write(thumbnail) }
        }
        return SnapshotEntry(
            machineId = machineId,
            id = id,
            createdAtMillis = id.toLong(),
            sizeBytes = state.size.toLong(),
            hasThumbnail = thumbnail != null && thumbnail.isNotEmpty(),
        )
    }

    /** Newest first. */
    fun list(machineId: String): List<SnapshotEntry> {
        val dir = root / machineId
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.list(dir)
            .filter { it.name.endsWith(STATE_SUFFIX) }
            .mapNotNull { path ->
                val id = path.name.removeSuffix(STATE_SUFFIX)
                val createdAt = id.toLongOrNull() ?: return@mapNotNull null
                SnapshotEntry(
                    machineId = machineId,
                    id = id,
                    createdAtMillis = createdAt,
                    sizeBytes = fileSystem.metadataOrNull(path)?.size ?: 0L,
                    hasThumbnail = fileSystem.exists(dir / "$id$THUMB_SUFFIX"),
                )
            }
            .sortedByDescending { it.createdAtMillis }
    }

    fun read(entry: SnapshotEntry): ByteArray =
        fileSystem.read(root / entry.machineId / "${entry.id}$STATE_SUFFIX") { readByteArray() }

    fun readThumbnail(entry: SnapshotEntry): ByteArray? {
        val path = root / entry.machineId / "${entry.id}$THUMB_SUFFIX"
        if (!fileSystem.exists(path)) return null
        return fileSystem.read(path) { readByteArray() }
    }

    fun delete(entry: SnapshotEntry) {
        val dir = root / entry.machineId
        fileSystem.delete(dir / "${entry.id}$STATE_SUFFIX", mustExist = false)
        fileSystem.delete(dir / "${entry.id}$THUMB_SUFFIX", mustExist = false)
    }

    fun latest(machineId: String): SnapshotEntry? = list(machineId).firstOrNull()

    /** Path the asset server serves this state under, relative to its root. */
    fun serverPathOf(entry: SnapshotEntry): String =
        "snapshots/${entry.machineId}/${entry.id}$STATE_SUFFIX"

    /**
     * Keeps the newest [keep] snapshots for a machine. Auto-snapshots happen on every
     * backgrounding, so without this the directory grows without bound.
     */
    fun prune(machineId: String, keep: Int = DEFAULT_KEEP) {
        list(machineId).drop(keep).forEach { delete(it) }
    }

    private companion object {
        const val STATE_SUFFIX = ".state"
        const val THUMB_SUFFIX = ".png"
        const val DEFAULT_KEEP = 10
    }
}
