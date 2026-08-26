package com.umain.draugr.storage

import kotlin.time.Clock

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** `2026-08-26 10:42:07`, zero padded so a listing stays column aligned. */
fun formatTimestamp(millis: Long): String {
    val instant = kotlin.time.Instant.fromEpochMilliseconds(millis)
    val text = instant.toString()
    val date = text.substringBefore('T')
    val time = text.substringAfter('T').take(8).trimEnd('Z')
    return "$date ${time.padEnd(8, '0')}"
}
