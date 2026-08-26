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
)

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
