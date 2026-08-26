package com.umain.draugr.storage

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun appStorageDir(): Path {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    val dir = "$documents/draugr"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir.toPath()
}

actual val platformFileSystem: FileSystem get() = FileSystem.SYSTEM
