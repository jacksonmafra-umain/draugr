package com.umain.draugr.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class SettingsStoreTest {

    private val fs = FakeFileSystem()
    private val path = "/settings/settings.json".toPath()

    private fun store() = SettingsStore(fileSystem = fs, path = path)

    @Test
    fun defaults_apply_when_nothing_is_saved() {
        val settings = store().load()
        assertTrue(settings.scanlinesEnabled)
        assertTrue(settings.bootLogExpandedByDefault)
    }

    @Test
    fun a_saved_setting_survives_a_reload() {
        store().save(Settings(scanlinesEnabled = false))
        assertEquals(false, store().load().scanlinesEnabled)
    }

    @Test
    fun a_corrupt_file_falls_back_to_defaults_rather_than_throwing() {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("{ not json") }
        assertTrue(store().load().scanlinesEnabled)
    }

    @Test
    fun unknown_keys_from_a_newer_build_are_ignored() {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("""{"scanlinesEnabled":false,"somethingNew":42}""") }
        assertEquals(false, store().load().scanlinesEnabled)
    }
}
