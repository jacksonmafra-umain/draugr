package com.umain.draugr.vm

/**
 * Guest screen size. In text mode [w] and [h] are characters, not pixels, so the surface must
 * fill its box and let the page scale the glyphs; only a graphical guest gets letterboxed to an
 * aspect ratio.
 */
data class Geometry(val w: Int, val h: Int, val graphical: Boolean) {
    val known: Boolean get() = w > 0 && h > 0

    /**
     * A graphical guest reports pixels, so its ratio is exact. A text guest reports a character
     * grid, and a monospace glyph is roughly 0.6 of its height, which is what makes an 80x25
     * screen look like a terminal rather than a square.
     */
    val aspectRatio: Float?
        get() = when {
            !known -> null
            graphical -> w.toFloat() / h.toFloat()
            else -> w.toFloat() * GLYPH_ASPECT / h.toFloat()
        }

    val label: String get() = if (known) "${w}x$h" else "----x----"

    companion object {
        private const val GLYPH_ASPECT = 0.6f
        val UNKNOWN = Geometry(0, 0, graphical = false)
    }
}
