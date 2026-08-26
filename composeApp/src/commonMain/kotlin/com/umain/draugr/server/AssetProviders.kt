package com.umain.draugr.server

import com.umain.draugr.resources.Res
import com.umain.draugr.storage.appStorageDir
import com.umain.draugr.storage.platformFileSystem
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Emulator payload comes from the app bundle; guest images come from app storage so they can be
 * streamed by range and, in the sideload case, written by the user.
 */
@OptIn(ExperimentalResourceApi::class)
/** Generated files, written by the controller before a boot. */
class GeneratedAssets {
    internal val provider = InMemoryAssetProvider()

    fun put(path: String, text: String) {
        // The path here is server-relative, the same string the boot config points at.
        provider.put(path.removePrefix("config/"), text.encodeToByteArray())
    }
}

fun draugrAssetProvider(generated: GeneratedAssets = GeneratedAssets()): AssetProvider {
    val bundled = BundledAssetProvider { path ->
        if (!path.isSafeRelativePath()) {
            null
        } else {
            runCatching { Res.readBytes("files/emulator/$path") }.getOrNull()
        }
    }
    val bundledImages = BundledAssetProvider { path ->
        if (!path.isSafeRelativePath()) {
            null
        } else {
            runCatching { Res.readBytes("files/emulator/images/$path") }.getOrNull()
        }
    }
    return PrefixAssetProvider(
        listOf(
            // A guest image may be packaged with the app or dropped in later by the user, and
            // the emulator should not care which.
            "images" to FallbackAssetProvider(
                listOf(
                    FileAssetProvider(platformFileSystem, appStorageDir() / "images"),
                    bundledImages,
                ),
            ),
            "sideload" to FileAssetProvider(platformFileSystem, appStorageDir() / "sideload"),
            // Saved states are megabytes: the page fetches them over the loopback server
            // rather than having them handed across the JS bridge as one giant string.
            "snapshots" to FileAssetProvider(platformFileSystem, appStorageDir() / "snapshots"),
            "config" to generated.provider,
            "" to bundled,
        ),
    )
}
