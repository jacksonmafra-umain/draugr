package com.umain.draugr.vm

import com.umain.draugr.catalog.Engine
import com.umain.draugr.catalog.MachineAssets
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.UiKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeProtocolTest {

    private val origin = "http://127.0.0.1:51234/deadbeef"

    private val freedos = MachineSpec(
        id = "freedos",
        cpu = "x86",
        os = "FreeDOS",
        ui = UiKind.VGA_TEXT,
        engine = Engine.V86,
        memMb = 64,
        assets = MachineAssets(
            bios = "bios/seabios.bin",
            vgabios = "bios/vgabios.bin",
            fda = "images/freedos/freedos722.img",
        ),
        sizeBytes = 737_280,
        bundled = true,
    )

    @Test
    fun boot_config_makes_asset_paths_absolute_against_the_origin() {
        val config = BridgeProtocol.bootConfig(freedos, origin)
        assertTrue(config.contains("\"bios\":\"$origin/bios/seabios.bin\""), config)
        assertTrue(config.contains("\"fda\":\"$origin/images/freedos/freedos722.img\""), config)
        assertTrue(config.contains("\"engine\":\"v86\""), config)
        assertTrue(config.contains("\"memMb\":64"), config)
    }

    @Test
    fun boot_config_omits_assets_the_machine_does_not_have() {
        val config = BridgeProtocol.bootConfig(freedos, origin)
        assertTrue(!config.contains("\"hda\""), config)
        assertTrue(!config.contains("\"kernel\""), config)
    }

    @Test
    fun send_keys_emits_a_scancode_array() {
        assertEquals(
            "window.DRAUGR.sendKeys([29,46,157]);",
            BridgeProtocol.sendKeysCall(intArrayOf(29, 46, 157)),
        )
    }

    @Test
    fun send_text_escapes_quotes_and_newlines() {
        val call = BridgeProtocol.sendTextCall("echo \"hi\"\n")
        assertEquals("window.DRAUGR.sendText(\"echo \\\"hi\\\"\\n\");", call)
    }

    @Test
    fun serial_events_become_log_lines() {
        val event = BridgeProtocol.parseEvent("""{"type":"serial","line":"OK"}""")
        assertEquals(VmEvent.Serial("OK"), event)
    }

    @Test
    fun screen_events_carry_the_video_mode() {
        val text = BridgeProtocol.parseEvent(
            """{"type":"screen","width":80,"height":25,"graphical":false}""",
        )
        assertEquals(VmEvent.ScreenResized(80, 25, graphical = false), text)

        val graphical = BridgeProtocol.parseEvent(
            """{"type":"screen","width":640,"height":480,"graphical":true}""",
        )
        assertEquals(VmEvent.ScreenResized(640, 480, graphical = true), graphical)
    }

    @Test
    fun state_events_map_onto_the_state_machine() {
        val booting = BridgeProtocol.parseEvent("""{"type":"state","state":"booting"}""")
        assertTrue((booting as VmEvent.StateChanged).state is VmState.Booting)

        val running = BridgeProtocol.parseEvent("""{"type":"state","state":"running"}""")
        assertTrue((running as VmEvent.StateChanged).state is VmState.Running)

        val halted = BridgeProtocol.parseEvent("""{"type":"state","state":"halted","message":"triple fault"}""")
        assertEquals(
            VmState.Halted("triple fault"),
            (halted as VmEvent.StateChanged).state,
        )
    }

    @Test
    fun errors_become_faults_with_the_stage_attached() {
        val fault = BridgeProtocol.parseEvent(
            """{"type":"error","stage":"boot","message":"unknown engine"}""",
        )
        assertEquals(VmEvent.Fault("boot: unknown engine"), fault)
    }

    @Test
    fun malformed_payloads_do_not_throw() {
        assertEquals(VmEvent.Fault("UNPARSEABLE EVENT"), BridgeProtocol.parseEvent("not json"))
    }

    @Test
    fun replies_are_told_apart_from_events() {
        val reply = BridgeProtocol.parseReplyOrNull("""{"type":"reply","id":"7","payload":"AAA"}""")
        assertEquals(7L to "AAA", reply)
        assertNull(BridgeProtocol.parseReplyOrNull("""{"type":"serial","line":"x"}"""))
    }

    @Test
    fun a_null_reply_payload_survives_the_round_trip() {
        val reply = BridgeProtocol.parseReplyOrNull("""{"type":"reply","id":"9","payload":null}""")
        assertEquals(9L, reply?.first)
        assertNull(reply?.second)
    }

    @Test
    fun geometry_only_letterboxes_graphical_guests() {
        assertNull(Geometry(80, 25, graphical = false).aspectRatio)
        assertEquals(640f / 480f, Geometry(640, 480, graphical = true).aspectRatio)
        assertEquals("----x----", Geometry.UNKNOWN.label)
        assertEquals("80x25", Geometry(80, 25, graphical = false).label)
    }
}
