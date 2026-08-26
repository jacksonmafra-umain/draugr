package com.umain.draugr.storage

import com.umain.draugr.DraugrApplication
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

actual fun appStorageDir(): Path {
    val dir = DraugrApplication.appContext.filesDir.resolve("draugr")
    if (!dir.exists()) dir.mkdirs()
    return dir.toOkioPath()
}

actual val platformFileSystem: FileSystem get() = FileSystem.SYSTEM
