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
fun draugrAssetProvider(): AssetProvider = PrefixAssetProvider(
    listOf(
        "images" to FileAssetProvider(platformFileSystem, appStorageDir() / "images"),
        "sideload" to FileAssetProvider(platformFileSystem, appStorageDir() / "sideload"),
        "" to BundledAssetProvider { path ->
            if (!path.isSafeRelativePath()) {
                null
            } else {
                runCatching { Res.readBytes("files/emulator/$path") }.getOrNull()
            }
        },
    ),
)
