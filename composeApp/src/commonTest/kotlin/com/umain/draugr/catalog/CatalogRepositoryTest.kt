package com.umain.draugr.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogRepositoryTest {

    private val manifest = """
        {
          "version": 1,
          "machines": [
            {
              "id": "freedos",
              "cpu": "x86",
              "os": "FreeDOS",
              "ui": "VGA_TEXT",
              "engine": "V86",
              "memMb": 64,
              "assets": { "fda": "images/freedos/freedos722.img" },
              "sizeBytes": 737280,
              "bundled": true
            },
            {
              "id": "win95",
              "cpu": "x86",
              "os": "Windows 95",
              "ui": "GRAPHICAL",
              "engine": "V86",
              "memMb": 64,
              "assets": { "hda": "sideload/win95/hda.img" },
              "sizeBytes": 1288490188,
              "bundled": false
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parses_machines_and_defaults_vga_memory() {
        val catalog = CatalogRepository().parse(manifest)
        assertEquals(2, catalog.machines.size)
        assertEquals(8, catalog.machines.first().vgaMemMb)
        assertEquals(Engine.V86, catalog.machines.first().engine)
        assertEquals(UiKind.VGA_TEXT, catalog.machines.first().ui)
    }

    @Test
    fun licence_gated_machines_are_not_bundled() {
        val catalog = CatalogRepository().parse(manifest)
        assertFalse(catalog.machines.single { it.id == "win95" }.bundled)
        assertTrue(catalog.machines.single { it.id == "freedos" }.bundled)
    }

    @Test
    fun console_filter_includes_vga_text() {
        val catalog = CatalogRepository().parse(manifest)
        val console = catalog.machines.filter { CatalogFilter.CONSOLE.matches(it) }
        assertEquals(listOf("freedos"), console.map { it.id })
    }

    @Test
    fun graphical_filter_excludes_text_guests() {
        val catalog = CatalogRepository().parse(manifest)
        val graphical = catalog.machines.filter { CatalogFilter.GRAPHICAL.matches(it) }
        assertEquals(listOf("win95"), graphical.map { it.id })
    }

    @Test
    fun ios_memory_budget_flags_large_guests() {
        val small = CatalogRepository().parse(manifest).machines.first()
        assertFalse(small.exceedsIosMemoryBudget)
        assertTrue(small.copy(memMb = 1024).exceedsIosMemoryBudget)
    }

    @Test
    fun byte_formatting_is_monospace_friendly() {
        assertEquals("720KB", 737_280L.formatBytes())
        assertEquals("1.2GB", 1_288_490_188L.formatBytes())
    }
}
