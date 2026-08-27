package com.umain.draugr.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

@Serializable
data class Settings(
    /** Scanlines cost frames over a live VM canvas, so they can be switched off. */
    val scanlinesEnabled: Boolean = true,
    val bootLogExpandedByDefault: Boolean = true,
    /** Multiplier over the fit-to-width scale of a text guest. 1.0 shows all 80 columns. */
    val terminalZoom: Float = 1f,
    /**
     * WebSocket relay for guest networking. Empty means the guest has no network at all, which
     * is the default and keeps the app making no outbound requests. A non-empty ws:// or wss://
     * URL routes guest traffic through that relay, and the app is then no longer offline-only.
     */
    val networkRelayUrl: String = "",
    /**
     * Auto-snapshot the guest when the app backgrounds. Null follows the platform default (on
     * for iOS, off for Android). Reading a whole guest's RAM on background is what pressures a
     * memory-tight device into killing the app mid-boot, so Android leaves it off.
     */
    val autoSnapshotOnBackground: Boolean? = null,
) {
    val networkEnabled: Boolean get() = networkRelayUrl.isNotBlank()
}

/** The cycle the machine screen steps through. Fit first, then progressively larger glyphs. */
val TERMINAL_ZOOM_STEPS = listOf(1f, 1.25f, 1.5f, 2f, 3f)

fun nextTerminalZoom(current: Float): Float {
    val index = TERMINAL_ZOOM_STEPS.indexOfFirst { it > current + 0.001f }
    return if (index == -1) TERMINAL_ZOOM_STEPS.first() else TERMINAL_ZOOM_STEPS[index]
}

fun formatZoom(value: Float): String {
    val tenths = (value * 10).toInt()
    return "${tenths / 10}.${tenths % 10}X"
}

class SettingsStore(
    private val fileSystem: FileSystem = platformFileSystem,
    private val path: Path = appStorageDir() / "settings.json",
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
) {
    fun load(): Settings {
        if (!fileSystem.exists(path)) return Settings()
        val text = runCatching { fileSystem.read(path) { readUtf8() } }.getOrNull()
            ?: return Settings()
        return runCatching { json.decodeFromString(Settings.serializer(), text) }
            .getOrElse { Settings() }
    }

    fun save(settings: Settings) {
        path.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(path) {
            writeUtf8(json.encodeToString(Settings.serializer(), settings))
        }
    }
}
