package com.umain.draugr.catalog

import com.umain.draugr.resources.Res
import com.umain.draugr.storage.SideloadStore
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads the bundled manifest. Sideload overrides are layered on top by the caller. */
class CatalogRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
    private val sideload: SideloadStore? = null,
) {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): Catalog {
        val bytes = Res.readBytes("files/catalog.json")
        return withSideloads(parse(bytes.decodeToString()))
    }

    /**
     * A licence-gated machine becomes bootable the moment a user-supplied image shows up, with
     * its disk asset pointed at wherever the image actually landed.
     */
    fun withSideloads(catalog: Catalog): Catalog {
        val store = sideload ?: return catalog
        return catalog.copy(
            machines = catalog.machines.map { spec ->
                if (spec.bundled) return@map spec
                val image = store.imageFor(spec.id) ?: return@map spec
                spec.copy(
                    bundled = true,
                    sizeBytes = image.sizeBytes,
                    assets = spec.assets.copy(hda = store.serverPathOf(image)),
                    note = "SIDELOADED: ${image.fileName}",
                )
            },
        )
    }

    fun parse(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)
}

enum class CatalogFilter(val label: String) {
    ALL("ALL"),
    X86("X86"),
    X86_64("X86_64"),
    RV64("RV64"),
    CONSOLE("CONSOLE"),
    GRAPHICAL("GRAPHICAL"),
    ;

    fun matches(spec: MachineSpec): Boolean = when (this) {
        ALL -> true
        X86 -> spec.cpu == "x86"
        X86_64 -> spec.cpu == "x86_64"
        RV64 -> spec.cpu == "riscv64"
        CONSOLE -> spec.ui == UiKind.CONSOLE || spec.ui == UiKind.VGA_TEXT
        GRAPHICAL -> spec.ui == UiKind.GRAPHICAL || spec.ui == UiKind.X_WINDOW
    }
}
