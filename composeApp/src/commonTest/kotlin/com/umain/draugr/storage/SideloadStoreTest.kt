package com.umain.draugr.storage

import com.umain.draugr.catalog.Engine
import com.umain.draugr.catalog.MachineAssets
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.UiKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class SideloadStoreTest {

    private val fs = FakeFileSystem()
    private val root = "/sideload".toPath()

    private fun store() = SideloadStore(fileSystem = fs, root = root)

    private val win95 = MachineSpec(
        id = "win95",
        cpu = "x86",
        os = "Windows 95",
        ui = UiKind.GRAPHICAL,
        engine = Engine.V86,
        memMb = 64,
        assets = MachineAssets(hda = "sideload/win95/hda.img"),
        sizeBytes = 0,
        bundled = false,
    )

    private fun writeImage(
        machineId: String,
        name: String,
        size: Int,
        bootable: Boolean = true,
    ) {
        val dir = root / machineId
        fs.createDirectories(dir)
        val bytes = ByteArray(size)
        if (bootable && size >= 512) {
            bytes[510] = 0x55.toByte()
            bytes[511] = 0xAA.toByte()
        }
        fs.write(dir / name) { write(bytes) }
    }

    @Test
    fun no_image_means_nothing_registered_and_nothing_discovered() {
        assertNull(store().imageFor("win95"))
        assertEquals(SideloadValidation.Missing, store().validate(win95, null))
    }

    @Test
    fun a_registered_image_is_found_again() {
        val subject = store()
        writeImage("win95", "hda.img", 4096)
        val image = SideloadedImage("win95", "hda.img", 4096, "abc")
        subject.register(image)
        assertEquals(image, subject.imageFor("win95"))
    }

    @Test
    fun an_image_dropped_in_over_usb_is_discovered_without_registration() {
        val subject = store()
        writeImage("win2000", "disk.img", 2048)
        val found = subject.imageFor("win2000")
        assertEquals("disk.img", found?.fileName)
        assertEquals(2048, found?.sizeBytes)
        assertEquals(64, found?.sha256?.length)
    }

    @Test
    fun discovery_prefers_the_largest_file_in_the_directory() {
        val subject = store()
        writeImage("win95", "readme.txt", 32)
        writeImage("win95", "hda.img", 8192)
        assertEquals("hda.img", subject.imageFor("win95")?.fileName)
    }

    @Test
    fun a_registered_image_whose_file_vanished_falls_back_to_discovery() {
        val subject = store()
        subject.register(SideloadedImage("win95", "gone.img", 10, "abc"))
        writeImage("win95", "present.img", 1024)
        assertEquals("present.img", subject.imageFor("win95")?.fileName)
    }

    @Test
    fun a_bootable_image_passes_validation() {
        val subject = store()
        writeImage("win95", "hda.img", 4096, bootable = true)
        assertEquals(SideloadValidation.Ok, subject.validate(win95, subject.imageFor("win95")))
    }

    @Test
    fun an_image_without_a_boot_signature_is_refused() {
        val subject = store()
        writeImage("win95", "hda.img", 4096, bootable = false)
        assertEquals(
            SideloadValidation.NotBootable,
            subject.validate(win95, subject.imageFor("win95")),
        )
    }

    @Test
    fun a_declared_size_is_enforced() {
        val subject = store()
        writeImage("win95", "hda.img", 4096)
        val spec = win95.copy(sizeBytes = 999_999)
        val result = subject.validate(spec, subject.imageFor("win95"))
        assertTrue(result is SideloadValidation.WrongSize, result.message)
        assertEquals(999_999, (result as SideloadValidation.WrongSize).expected)
    }

    @Test
    fun a_declared_hash_wins_over_the_size_and_signature_checks() {
        val subject = store()
        writeImage("win95", "hda.img", 4096, bootable = false)
        val image = subject.imageFor("win95")!!
        val spec = win95.copy(sha256 = image.sha256, sizeBytes = 1)
        assertEquals(SideloadValidation.Ok, subject.validate(spec, image))
    }

    @Test
    fun a_wrong_hash_is_reported_with_both_values() {
        val subject = store()
        writeImage("win95", "hda.img", 4096)
        val spec = win95.copy(sha256 = "0".repeat(64))
        val result = subject.validate(spec, subject.imageFor("win95"))
        assertTrue(result is SideloadValidation.WrongHash, result.message)
    }

    @Test
    fun remove_deletes_the_file_and_forgets_the_registration() {
        val subject = store()
        writeImage("win95", "hda.img", 1024)
        subject.register(SideloadedImage("win95", "hda.img", 1024, "abc"))
        subject.remove("win95")
        assertNull(subject.imageFor("win95"))
        assertTrue(!fs.exists(root / "win95" / "hda.img"))
    }

    @Test
    fun server_path_matches_the_asset_server_mount() {
        val image = SideloadedImage("win95", "hda.img", 1, "abc")
        assertEquals("sideload/win95/hda.img", store().serverPathOf(image))
    }
}
