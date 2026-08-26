package com.umain.draugr.vm

/**
 * Guest screen size. In text mode [w] and [h] are characters, not pixels, so the surface must
 * fill its box and let the page scale the glyphs; only a graphical guest gets letterboxed to an
 * aspect ratio.
 */
data class Geometry(val w: Int, val h: Int, val graphical: Boolean) {
    val known: Boolean get() = w > 0 && h > 0

    val aspectRatio: Float?
        get() = if (graphical && known) w.toFloat() / h.toFloat() else null

    val label: String get() = if (known) "${w}x$h" else "----x----"

    companion object {
        val UNKNOWN = Geometry(0, 0, graphical = false)
    }
}
