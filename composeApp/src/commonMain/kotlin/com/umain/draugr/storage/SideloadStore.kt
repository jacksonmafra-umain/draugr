package com.umain.draugr.storage

import com.umain.draugr.catalog.MachineSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

@Serializable
data class SideloadedImage(
    val machineId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
private data class SideloadManifest(val images: List<SideloadedImage> = emptyList())

sealed interface SideloadValidation {
    data object Ok : SideloadValidation
    data class WrongSize(val expected: Long, val actual: Long) : SideloadValidation
    data class WrongHash(val expected: String, val actual: String) : SideloadValidation
    data object NotBootable : SideloadValidation
    data object Missing : SideloadValidation

    val message: String
        get() = when (this) {
            Ok -> "IMAGE ACCEPTED"
            is WrongSize -> "EXPECTED $expected BYTES, GOT $actual"
            is WrongHash -> "SHA-256 MISMATCH, EXPECTED ${expected.take(16)}…"
            NotBootable -> "NO BOOT SIGNATURE AT OFFSET 510, IMAGE IS NOT BOOTABLE"
            Missing -> "NO IMAGE FOUND"
        }
}

/**
 * User-supplied disk images for the licence-gated machines. Nothing here ever ships with the
 * app; the catalog marks those entries `bundled = false` until an image turns up.
 */
class SideloadStore(
    private val fileSystem: FileSystem = platformFileSystem,
    private val root: Path = appStorageDir() / "sideload",
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    private val manifestPath: Path get() = root / "manifest.json"

    fun directoryFor(machineId: String): Path = root / machineId

    fun targetPath(machineId: String, fileName: String): Path =
        directoryFor(machineId) / fileName

    /** Path the asset server serves this image under, relative to its root. */
    fun serverPathOf(image: SideloadedImage): String =
        "sideload/${image.machineId}/${image.fileName}"

    fun register(image: SideloadedImage) {
        val current = readManifest().images.filterNot { it.machineId == image.machineId }
        writeManifest(SideloadManifest(current + image))
    }

    fun remove(machineId: String) {
        val image = imageFor(machineId)
        if (image != null) {
            fileSystem.delete(targetPath(machineId, image.fileName), mustExist = false)
        }
        writeManifest(
            SideloadManifest(readManifest().images.filterNot { it.machineId == machineId }),
        )
    }

    /**
     * A registered image, or one simply dropped into the machine's directory over USB. iTunes
     * File Sharing is the easiest way to get a large image onto a device, and a file that
     * arrives that way has never been through the picker.
     */
    fun imageFor(machineId: String): SideloadedImage? {
        readManifest().images.firstOrNull { it.machineId == machineId }?.let { registered ->
            if (fileSystem.exists(targetPath(machineId, registered.fileName))) return registered
        }
        return discover(machineId)
    }

    private fun discover(machineId: String): SideloadedImage? {
        val dir = directoryFor(machineId)
        if (!fileSystem.exists(dir)) return null
        val candidate = fileSystem.list(dir)
            .filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
            .maxByOrNull { fileSystem.metadataOrNull(it)?.size ?: 0L }
            ?: return null
        return SideloadedImage(
            machineId = machineId,
            fileName = candidate.name,
            sizeBytes = fileSystem.metadataOrNull(candidate)?.size ?: 0L,
            sha256 = sha256Of(candidate),
        )
    }

    fun sha256Of(path: Path): String =
        HashingSource.sha256(fileSystem.source(path)).use { hashing ->
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }

    /**
     * Size and hash are only checked when the manifest says what to expect. The boot signature
     * check is always worth doing: it catches the common mistake of handing over a zip or an
     * installer rather than a disk image.
     */
    fun validate(spec: MachineSpec, image: SideloadedImage?): SideloadValidation {
        if (image == null) return SideloadValidation.Missing
        val path = targetPath(image.machineId, image.fileName)
        if (!fileSystem.exists(path)) return SideloadValidation.Missing

        spec.sha256?.let { expected ->
            val actual = sha256Of(path)
            if (!expected.equals(actual, ignoreCase = true)) {
                return SideloadValidation.WrongHash(expected, actual)
            }
            return SideloadValidation.Ok
        }

        if (spec.sizeBytes > 0 && image.sizeBytes != spec.sizeBytes) {
            return SideloadValidation.WrongSize(spec.sizeBytes, image.sizeBytes)
        }
        if (!hasBootSignature(path)) return SideloadValidation.NotBootable
        return SideloadValidation.Ok
    }

    /** `0x55AA` at offset 510 is the boot signature every bootable sector carries. */
    private fun hasBootSignature(path: Path): Boolean {
        val size = fileSystem.metadataOrNull(path)?.size ?: return false
        if (size < 512) return false
        return fileSystem.read(path) {
            val sector = readByteArray(512)
            sector[510] == 0x55.toByte() && sector[511] == 0xAA.toByte()
        }
    }

    private fun readManifest(): SideloadManifest {
        if (!fileSystem.exists(manifestPath)) return SideloadManifest()
        val text = runCatching { fileSystem.read(manifestPath) { readUtf8() } }.getOrNull()
            ?: return SideloadManifest()
        return runCatching { json.decodeFromString(SideloadManifest.serializer(), text) }
            .getOrElse { SideloadManifest() }
    }

    private fun writeManifest(manifest: SideloadManifest) {
        fileSystem.createDirectories(root)
        fileSystem.write(manifestPath) {
            writeUtf8(json.encodeToString(SideloadManifest.serializer(), manifest))
        }
    }
}
