package com.umain.draugr.input

/** One key on the terminal keyboard. */
data class TerminalKey(
    val label: String,
    val make: Int,
    val extended: Boolean = false,
    val weight: Float = 1f,
) {
    fun strokes(): IntArray = if (extended) {
        Scancodes.tapExtended(make)
    } else {
        Scancodes.tap(make)
    }
}

/** A modifier that latches until tapped again. */
enum class Modifier(val label: String, val make: Int) {
    CTRL("CTRL", Scancodes.CTRL),
    ALT("ALT", Scancodes.ALT),
    SHIFT("SHIFT", Scancodes.LSHIFT),
}

object KeyLayout {

    private fun row(chars: String): List<TerminalKey> = chars.map { char ->
        TerminalKey(
            label = char.uppercaseChar().toString(),
            make = Scancodes.make(char) ?: error("no scancode for $char"),
        )
    }

    val numberRow: List<TerminalKey> = row("1234567890") + listOf(
        TerminalKey("-", Scancodes.make('-')!!),
        TerminalKey("=", Scancodes.make('=')!!),
        TerminalKey("BSP", Scancodes.BACKSPACE, weight = 1.6f),
    )

    val topRow: List<TerminalKey> = row("qwertyuiop")

    val homeRow: List<TerminalKey> = row("asdfghjkl") + listOf(
        TerminalKey(";", Scancodes.make(';')!!),
        TerminalKey("ENT", Scancodes.ENTER, weight = 1.6f),
    )

    val bottomRow: List<TerminalKey> = row("zxcvbnm") + listOf(
        TerminalKey(",", Scancodes.make(',')!!),
        TerminalKey(".", Scancodes.make('.')!!),
        TerminalKey("/", Scancodes.make('/')!!),
    )

    val spaceRow: List<TerminalKey> = listOf(
        TerminalKey("ESC", Scancodes.ESC),
        TerminalKey("TAB", Scancodes.TAB),
        TerminalKey("SPACE", Scancodes.SPACE, weight = 4f),
        TerminalKey("DEL", Scancodes.DELETE, extended = true),
    )

    val arrows: List<TerminalKey> = listOf(
        TerminalKey("<", Scancodes.ARROW_LEFT, extended = true),
        TerminalKey("v", Scancodes.ARROW_DOWN, extended = true),
        TerminalKey("^", Scancodes.ARROW_UP, extended = true),
        TerminalKey(">", Scancodes.ARROW_RIGHT, extended = true),
    )

    val functionKeys: List<TerminalKey> = (1..12).map { number ->
        TerminalKey("F$number", Scancodes.functionKey(number))
    }

    /** Every row of the main block, in display order. */
    val mainRows: List<List<TerminalKey>> =
        listOf(numberRow, topRow, homeRow, bottomRow, spaceRow)
}
