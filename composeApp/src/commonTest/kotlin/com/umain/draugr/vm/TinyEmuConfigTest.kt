package com.umain.draugr.vm

import com.umain.draugr.catalog.Engine
import com.umain.draugr.catalog.MachineAssets
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.UiKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TinyEmuConfigTest {

    private val origin = "http://127.0.0.1:51234/deadbeef"

    private val buildroot = MachineSpec(
        id = "buildroot-rv64",
        cpu = "riscv64",
        os = "Buildroot",
        ui = UiKind.CONSOLE,
        engine = Engine.TINYEMU,
        memMb = 256,
        assets = MachineAssets(
            bios = "bios/bbl64.bin",
            kernel = "images/buildroot-rv64/kernel-riscv64.bin",
            hda = "images/buildroot-rv64/root-riscv64.bin",
        ),
        sizeBytes = 44_040_192,
        bundled = true,
    )

    @Test
    fun assets_are_absolute_urls_against_the_local_server() {
        val config = TinyEmuConfig.render(buildroot, origin)
        assertTrue(config.contains("bios: \"$origin/bios/bbl64.bin\""), config)
        assertTrue(
            config.contains("drive0: { file: \"$origin/images/buildroot-rv64/root-riscv64.bin\" }"),
            config,
        )
    }

    @Test
    fun the_machine_name_matches_what_tinyemu_expects() {
        assertEquals("riscv64", TinyEmuConfig.machineName("riscv64"))
        assertEquals("pc", TinyEmuConfig.machineName("x86_64"))
    }

    @Test
    fun memory_comes_from_the_spec() {
        assertTrue(TinyEmuConfig.render(buildroot, origin).contains("memory_size: 256,"))
        assertTrue(
            TinyEmuConfig.render(buildroot.copy(memMb = 1024), origin)
                .contains("memory_size: 1024,"),
        )
    }

    @Test
    fun a_console_guest_gets_no_display_block() {
        assertFalse(TinyEmuConfig.render(buildroot, origin).contains("display0"))
    }

    @Test
    fun an_x_window_guest_gets_a_framebuffer() {
        val config = TinyEmuConfig.render(buildroot.copy(ui = UiKind.X_WINDOW), origin)
        assertTrue(config.contains("display0: { device: \"simplefb\""), config)
    }

    @Test
    fun absent_assets_are_omitted_rather_than_written_as_null() {
        val config = TinyEmuConfig.render(
            buildroot.copy(assets = MachineAssets(kernel = "k.bin")),
            origin,
        )
        assertFalse(config.contains("bios:"), config)
        assertFalse(config.contains("drive0"), config)
        assertFalse(config.contains("null"), config)
    }

    @Test
    fun the_config_path_is_scoped_to_the_machine() {
        assertEquals("config/buildroot-rv64.cfg", TinyEmuConfig.pathFor(buildroot))
    }

    @Test
    fun the_boot_config_points_the_engine_at_its_generated_file() {
        val json = BridgeProtocol.bootConfig(buildroot, origin)
        assertTrue(json.contains("\"engine\":\"tinyemu\""), json)
        assertTrue(json.contains("\"configUrl\":\"$origin/config/buildroot-rv64.cfg\""), json)
        assertTrue(json.contains("\"cmdline\":\"loglevel=3"), json)
    }

    @Test
    fun a_v86_machine_carries_no_tinyemu_fields() {
        val v86 = buildroot.copy(engine = Engine.V86)
        val json = BridgeProtocol.bootConfig(v86, origin)
        assertFalse(json.contains("configUrl"), json)
        assertFalse(json.contains("cmdline"), json)
    }
}
