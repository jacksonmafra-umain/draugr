package com.umain.draugr.catalog

import com.umain.draugr.resources.Res
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Reads the bundled manifest. Sideload overrides are layered on top by the caller. */
class CatalogRepository(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): Catalog {
        val bytes = Res.readBytes("files/catalog.json")
        return parse(bytes.decodeToString())
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
