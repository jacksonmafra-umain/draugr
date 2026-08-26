package com.umain.draugr.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UiKind {
    @SerialName("CONSOLE")
    CONSOLE,

    @SerialName("X_WINDOW")
    X_WINDOW,

    @SerialName("GRAPHICAL")
    GRAPHICAL,

    @SerialName("VGA_TEXT")
    VGA_TEXT,
    ;

    val label: String
        get() = when (this) {
            CONSOLE -> "CONSOLE"
            X_WINDOW -> "X WINDOW"
            GRAPHICAL -> "GRAPHICAL"
            VGA_TEXT -> "VGA TEXT"
        }
}

@Serializable
enum class Engine {
    @SerialName("V86")
    V86,

    @SerialName("TINYEMU")
    TINYEMU,
    ;

    val label: String get() = if (this == V86) "V86" else "TINYEMU"
}

/**
 * Asset paths are relative to the server root, never absolute URLs: the origin is only known
 * once [com.umain.draugr.server.AssetServer] has claimed an ephemeral port.
 */
@Serializable
data class MachineAssets(
    val kernel: String? = null,
    val bios: String? = null,
    val vgabios: String? = null,
    val hda: String? = null,
    val fda: String? = null,
    val cdrom: String? = null,
    val initrd: String? = null,
    val stateImage: String? = null,
) {
    /** Asset paths that live under the sideload mount, for a bring-your-own-files machine. */
    fun sideloadPaths(): List<String> =
        listOfNotNull(kernel, bios, vgabios, hda, fda, cdrom, initrd, stateImage)
            .filter { it.startsWith("sideload/") }
}

@Serializable
data class MachineSpec(
    val id: String,
    val cpu: String,
    val os: String,
    val ui: UiKind,
    val engine: Engine,
    val memMb: Int,
    val vgaMemMb: Int = 8,
    val assets: MachineAssets,
    val sizeBytes: Long,
    val bundled: Boolean,
    /** Kernel command line, when the machine boots a kernel plus initrd rather than a disk. */
    val cmdline: String? = null,
    val note: String? = null,
    /** SHA-256 of the primary disk image, when one is expected for validation. */
    val sha256: String? = null,
) {
    val displayName: String get() = os.uppercase()
    val cpuLabel: String get() = cpu.uppercase()

    /** iOS WebContent gets jetsam-killed well before a large guest finishes booting. */
    val exceedsIosMemoryBudget: Boolean get() = memMb > IOS_MEM_BUDGET_MB

    companion object {
        const val IOS_MEM_BUDGET_MB = 512
    }
}

@Serializable
data class Catalog(
    val version: Int,
    val machines: List<MachineSpec>,
)

private const val KIB = 1024L
private const val MIB = 1_048_576L
private const val GIB = 1_073_741_824L

/** Rounds to one decimal so columns stay the same width when read in a monospace face. */
fun Long.formatBytes(): String {
    fun tenths(unit: Long): String {
        val t = (this * 10 + unit / 2) / unit
        return "${t / 10}.${t % 10}"
    }
    return when {
        this >= GIB -> "${tenths(GIB)}GB"
        this >= MIB -> "${tenths(MIB)}MB"
        this >= KIB -> "${(this + KIB / 2) / KIB}KB"
        else -> "${this}B"
    }
}
