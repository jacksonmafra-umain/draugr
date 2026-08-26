package com.umain.draugr.input

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScancodesTest {

    @Test
    fun letters_map_to_set_one_make_codes() {
        assertEquals(0x1E, Scancodes.make('a'))
        assertEquals(0x10, Scancodes.make('q'))
        assertEquals(0x2C, Scancodes.make('z'))
        assertEquals(0x39, Scancodes.make(' '))
    }

    @Test
    fun case_does_not_change_the_scancode() {
        assertEquals(Scancodes.make('a'), Scancodes.make('A'))
    }

    @Test
    fun unknown_characters_have_no_scancode() {
        assertNull(Scancodes.make('é'))
    }

    @Test
    fun break_codes_set_the_high_bit() {
        assertEquals(0x9E, Scancodes.breakOf(0x1E))
        assertContentEquals(intArrayOf(0x1E, 0x9E), Scancodes.tap(0x1E))
    }

    @Test
    fun extended_keys_repeat_the_prefix_on_release() {
        assertContentEquals(
            intArrayOf(0xE0, 0x48, 0xE0, 0xC8),
            Scancodes.tapExtended(Scancodes.ARROW_UP),
        )
    }

    @Test
    fun function_keys_account_for_the_gap_before_f11() {
        assertEquals(0x3B, Scancodes.functionKey(1))
        assertEquals(0x44, Scancodes.functionKey(10))
        assertEquals(0x57, Scancodes.functionKey(11))
        assertEquals(0x58, Scancodes.functionKey(12))
    }

    @Test
    fun ctrl_c_is_a_well_formed_chord() {
        val chord = Scancodes.withModifiers(
            modifiers = listOf(Scancodes.CTRL),
            inner = Scancodes.tap(Scancodes.make('c')!!),
        )
        assertContentEquals(intArrayOf(0x1D, 0x2E, 0xAE, 0x9D), chord)
    }

    @Test
    fun modifiers_release_in_reverse_order() {
        val chord = Scancodes.withModifiers(
            modifiers = listOf(Scancodes.CTRL, Scancodes.ALT),
            inner = Scancodes.tap(Scancodes.DELETE),
        )
        assertContentEquals(
            intArrayOf(0x1D, 0x38, 0x53, 0xD3, 0xB8, 0x9D),
            chord,
        )
    }

    @Test
    fun a_chord_with_no_modifiers_is_just_the_key() {
        assertContentEquals(
            Scancodes.tap(Scancodes.ENTER),
            Scancodes.withModifiers(emptyList(), Scancodes.tap(Scancodes.ENTER)),
        )
    }

    @Test
    fun linux_keycodes_differ_from_pc_scancodes_where_they_must() {
        assertEquals(30, Scancodes.Linux.code('a'))
        assertEquals(103, Scancodes.Linux.UP)
        assertEquals(59, Scancodes.Linux.functionKey(1))
        assertEquals(88, Scancodes.Linux.functionKey(12))
    }

    @Test
    fun every_key_on_the_board_emits_a_well_formed_stroke_pair() {
        KeyLayout.allKeys.forEach { key ->
            val strokes = key.strokes()
            assertEquals(if (key.extended) 4 else 2, strokes.size, key.label)
        }
    }

    @Test
    fun no_row_exceeds_ten_columns_so_keys_stay_hittable() {
        listOf(
            KeyLayout.lettersTop,
            KeyLayout.lettersHome,
            KeyLayout.lettersBottom,
            KeyLayout.symbolsTop,
            KeyLayout.symbolsSecond,
            KeyLayout.symbolsThird,
        ).forEach { row ->
            assertTrue(row.size <= 10, "row of ${row.size}: ${row.map { it.label }}")
        }
    }

    @Test
    fun the_letters_layer_is_a_qwerty_board() {
        assertEquals("QWERTYUIOP", KeyLayout.lettersTop.joinToString("") { it.label })
        assertEquals("ASDFGHJKL", KeyLayout.lettersHome.joinToString("") { it.label })
        assertEquals("ZXCVBNM", KeyLayout.lettersBottom.joinToString("") { it.label })
    }

    @Test
    fun labels_are_short_enough_never_to_wrap() {
        KeyLayout.allKeys.forEach { key ->
            assertTrue(key.label.length <= 5, key.label)
        }
    }

    @Test
    fun arrows_and_delete_are_extended_keys() {
        val navigation = KeyLayout.symbolsThird.filter { it.label in setOf("<", ">", "^", "v", "DEL") }
        assertEquals(5, navigation.size)
        navigation.forEach { assertTrue(it.extended, it.label) }
    }
}
