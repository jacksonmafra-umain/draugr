package com.umain.draugr.input

/** One key on the terminal keyboard. [weight] is relative width within its row. */
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

/**
 * Two layers, the way a phone keyboard does it. Cramming numbers, symbols, twelve function keys
 * and the arrows onto one board gave keys too small to hit and rows that ran off the screen.
 */
enum class KeyboardLayer { LETTERS, SYMBOLS }

/** Non-character keys the keyboard renders itself rather than emitting a scancode for. */
enum class KeyboardAction { SHIFT, CTRL, ALT, TOGGLE_LAYER }

object KeyLayout {

    private fun key(char: Char, weight: Float = 1f) = TerminalKey(
        label = char.uppercaseChar().toString(),
        make = Scancodes.make(char) ?: error("no scancode for $char"),
        weight = weight,
    )

    private fun row(chars: String) = chars.map { key(it) }

    // --- letters ---------------------------------------------------------------------------

    val lettersTop: List<TerminalKey> = row("qwertyuiop")
    val lettersHome: List<TerminalKey> = row("asdfghjkl")
    val lettersBottom: List<TerminalKey> = row("zxcvbnm")

    val backspace = TerminalKey("BSP", Scancodes.BACKSPACE, weight = 1.5f)
    val enter = TerminalKey("ENT", Scancodes.ENTER, weight = 1.5f)
    val space = TerminalKey("SPACE", Scancodes.SPACE, weight = 4f)
    val comma = TerminalKey(",", Scancodes.make(',')!!)
    val period = TerminalKey(".", Scancodes.make('.')!!)

    // --- symbols ---------------------------------------------------------------------------

    val symbolsTop: List<TerminalKey> = row("1234567890")
    val symbolsSecond: List<TerminalKey> = listOf(
        key('-'), key('='), key('['), key(']'), key(';'),
        key('\''), key('`'), key('\\'), key('/'),
    )
    val symbolsThird: List<TerminalKey> = listOf(
        TerminalKey("ESC", Scancodes.ESC, weight = 1.4f),
        TerminalKey("TAB", Scancodes.TAB, weight = 1.4f),
        TerminalKey("<", Scancodes.ARROW_LEFT, extended = true),
        TerminalKey("v", Scancodes.ARROW_DOWN, extended = true),
        TerminalKey("^", Scancodes.ARROW_UP, extended = true),
        TerminalKey(">", Scancodes.ARROW_RIGHT, extended = true),
        TerminalKey("DEL", Scancodes.DELETE, extended = true, weight = 1.4f),
    )

    val functionKeys: List<TerminalKey> = (1..12).map { number ->
        TerminalKey("F$number", Scancodes.functionKey(number))
    }

    /** Every key that emits a scancode, for tests that assert the board is well formed. */
    val allKeys: List<TerminalKey> = lettersTop + lettersHome + lettersBottom +
        listOf(backspace, enter, space, comma, period) +
        symbolsTop + symbolsSecond + symbolsThird + functionKeys
}
