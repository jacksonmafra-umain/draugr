package com.umain.draugr.storage

import okio.FileSystem
import okio.Path

/** Root for guest images, sideloaded files and snapshots. */
expect fun appStorageDir(): Path

expect val platformFileSystem: FileSystem
