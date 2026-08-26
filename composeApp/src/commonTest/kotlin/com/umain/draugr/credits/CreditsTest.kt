package com.umain.draugr.credits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditsTest {

    @Test
    fun every_credit_states_a_licence_and_a_source() {
        Credits.sections.flatMap { it.entries }.forEach { credit ->
            assertTrue(credit.licence.isNotBlank(), "${credit.name} has no licence")
            assertTrue(credit.source.isNotBlank(), "${credit.name} has no source")
            assertTrue(credit.what.isNotBlank(), "${credit.name} says nothing")
        }
    }

    @Test
    fun nothing_is_credited_twice() {
        val names = Credits.sections.flatMap { it.entries }.map { it.name }
        assertEquals(names.size, names.toSet().size, names.toString())
    }

    @Test
    fun the_engines_and_their_bios_blobs_are_all_named() {
        val names = Credits.sections.flatMap { it.entries }.map { it.name }
        listOf("v86", "TinyEMU", "SeaBIOS", "VGABIOS").forEach {
            assertTrue(it in names, "$it is shipped but not credited")
        }
    }

    @Test
    fun every_bundled_guest_is_credited() {
        val names = Credits.sections.flatMap { it.entries }.map { it.name }
        listOf("FreeDOS", "Alpine Linux", "Buildroot", "Fedora 33", "ReactOS").forEach {
            assertTrue(it in names, "$it is bundled but not credited")
        }
    }

    @Test
    fun licence_labels_stay_short_enough_to_sit_beside_a_name() {
        Credits.sections.flatMap { it.entries }.forEach { credit ->
            assertTrue(credit.licence.length <= 14, "${credit.name}: ${credit.licence}")
        }
    }

    @Test
    fun the_author_is_credited_with_a_name_and_somewhere_to_find_them() {
        assertTrue(Credits.author.name.isNotBlank())
        assertTrue(Credits.author.handle.isNotBlank())
        assertTrue(Credits.author.role.isNotBlank())
    }

    @Test
    fun the_note_repeats_the_two_promises_that_matter() {
        assertTrue(Credits.NOTE.contains("NO MICROSOFT DERIVED IMAGE"))
        assertTrue(Credits.NOTE.contains("NO OUTBOUND NETWORK REQUESTS"))
    }
}
