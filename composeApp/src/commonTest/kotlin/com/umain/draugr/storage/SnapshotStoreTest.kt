package com.umain.draugr.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class SnapshotStoreTest {

    private val fs = FakeFileSystem()
    private var clock = 1_700_000_000_000L

    private fun store() = SnapshotStore(
        fileSystem = fs,
        root = "/snapshots".toPath(),
        now = { clock },
    )

    @Test
    fun saves_and_reads_a_state_round_trip() {
        val subject = store()
        val state = ByteArray(1024) { (it % 97).toByte() }
        val entry = subject.save("freedos", state)
        assertEquals(1024, entry.sizeBytes)
        assertContentEquals(state, subject.read(entry))
    }

    @Test
    fun a_thumbnail_is_optional() {
        val subject = store()
        val withoutThumb = subject.save("freedos", ByteArray(8))
        assertFalse(withoutThumb.hasThumbnail)
        assertNull(subject.readThumbnail(withoutThumb))

        clock += 1000
        val withThumb = subject.save("freedos", ByteArray(8), thumbnail = ByteArray(4) { 7 })
        assertTrue(withThumb.hasThumbnail)
        assertContentEquals(ByteArray(4) { 7 }, subject.readThumbnail(withThumb))
    }

    @Test
    fun an_empty_thumbnail_is_treated_as_none() {
        val subject = store()
        val entry = subject.save("freedos", ByteArray(8), thumbnail = ByteArray(0))
        assertFalse(entry.hasThumbnail)
    }

    @Test
    fun listing_is_newest_first_and_scoped_to_one_machine() {
        val subject = store()
        subject.save("freedos", ByteArray(1))
        clock += 5000
        subject.save("freedos", ByteArray(2))
        clock += 5000
        subject.save("reactos", ByteArray(3))

        val freedos = subject.list("freedos")
        assertEquals(2, freedos.size)
        assertTrue(freedos[0].createdAtMillis > freedos[1].createdAtMillis)
        assertEquals(listOf("reactos"), subject.list("reactos").map { it.machineId })
    }

    @Test
    fun listing_an_unknown_machine_is_empty_not_an_error() {
        assertEquals(emptyList(), store().list("never-booted"))
    }

    @Test
    fun latest_returns_the_most_recent_save() {
        val subject = store()
        subject.save("freedos", ByteArray(1))
        clock += 9000
        val newest = subject.save("freedos", ByteArray(2))
        assertEquals(newest.id, subject.latest("freedos")?.id)
    }

    @Test
    fun delete_removes_the_state_and_its_thumbnail() {
        val subject = store()
        val entry = subject.save("freedos", ByteArray(4), thumbnail = ByteArray(2))
        subject.delete(entry)
        assertEquals(emptyList(), subject.list("freedos"))
        assertNull(subject.readThumbnail(entry))
    }

    @Test
    fun prune_keeps_only_the_newest_entries() {
        val subject = store()
        repeat(6) {
            subject.save("freedos", ByteArray(1))
            clock += 1000
        }
        subject.prune("freedos", keep = 2)
        val remaining = subject.list("freedos")
        assertEquals(2, remaining.size)
        assertTrue(remaining[0].createdAtMillis > remaining[1].createdAtMillis)
    }

    @Test
    fun server_path_matches_where_the_asset_server_mounts_snapshots() {
        val subject = store()
        val entry = subject.save("freedos", ByteArray(4))
        assertEquals("snapshots/freedos/${entry.id}.state", subject.serverPathOf(entry))
    }

    @Test
    fun timestamps_render_column_aligned() {
        val formatted = formatTimestamp(1_700_000_000_000L)
        assertEquals(19, formatted.length)
        assertEquals('-', formatted[4])
        assertEquals(' ', formatted[10])
        assertEquals(':', formatted[13])
    }
}
