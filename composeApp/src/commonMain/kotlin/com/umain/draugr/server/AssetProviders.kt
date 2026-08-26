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
fun draugrAssetProvider(): AssetProvider {
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
            "" to bundled,
        ),
    )
}
