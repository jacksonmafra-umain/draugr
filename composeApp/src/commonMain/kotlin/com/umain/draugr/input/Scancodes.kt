package com.umain.draugr.input

/**
 * PC set-1 scancodes for v86 and Linux keycodes for TinyEMU. The bridge takes codes, never
 * text, because the system IME is unusable over a web view canvas on iOS.
 *
 * A break code is the make code with bit 7 set. Extended keys are prefixed with 0xE0, and the
 * break of an extended key repeats the prefix.
 */
object Scancodes {

    const val EXTENDED_PREFIX = 0xE0
    private const val BREAK_BIT = 0x80

    const val ESC = 0x01
    const val BACKSPACE = 0x0E
    const val TAB = 0x0F
    const val ENTER = 0x1C
    const val CTRL = 0x1D
    const val LSHIFT = 0x2A
    const val ALT = 0x38
    const val SPACE = 0x39
    const val CAPS_LOCK = 0x3A

    /** Extended make codes, without the 0xE0 prefix. */
    const val ARROW_UP = 0x48
    const val ARROW_LEFT = 0x4B
    const val ARROW_RIGHT = 0x4D
    const val ARROW_DOWN = 0x50
    const val DELETE = 0x53

    private val PRINTABLE: Map<Char, Int> = buildMap {
        // Number row
        "1234567890-=".forEachIndexed { index, c -> put(c, 0x02 + index) }
        // Top letter row
        "qwertyuiop[]".forEachIndexed { index, c -> put(c, 0x10 + index) }
        // Home row
        "asdfghjkl;'".forEachIndexed { index, c -> put(c, 0x1E + index) }
        put('`', 0x29)
        put('\\', 0x2B)
        // Bottom row
        "zxcvbnm,./".forEachIndexed { index, c -> put(c, 0x2C + index) }
        put(' ', SPACE)
    }

    /** Function keys are not contiguous: F11 and F12 sit apart from F1 to F10. */
    fun functionKey(number: Int): Int = when (number) {
        in 1..10 -> 0x3A + number
        11 -> 0x57
        12 -> 0x58
        else -> error("no such function key: $number")
    }

    fun make(char: Char): Int? = PRINTABLE[char.lowercaseChar()]

    fun breakOf(make: Int): Int = make or BREAK_BIT

    /** Press and release one plain key. */
    fun tap(make: Int): IntArray = intArrayOf(make, breakOf(make))

    /** Press and release one extended key, prefix included on both halves. */
    fun tapExtended(make: Int): IntArray =
        intArrayOf(EXTENDED_PREFIX, make, EXTENDED_PREFIX, breakOf(make))

    /**
     * Wraps [inner] in the make and break codes of each held modifier, outermost first, so the
     * guest sees a well-formed chord rather than a stuck modifier.
     */
    fun withModifiers(modifiers: List<Int>, inner: IntArray): IntArray {
        val downs = modifiers.map { it }
        val ups = modifiers.reversed().map { breakOf(it) }
        return (downs + inner.toList() + ups).toIntArray()
    }

    /** Linux keycodes, for engines that speak evdev rather than PS/2. */
    object Linux {
        private val PRINTABLE: Map<Char, Int> = buildMap {
            "1234567890-=".forEachIndexed { index, c -> put(c, 2 + index) }
            "qwertyuiop[]".forEachIndexed { index, c -> put(c, 16 + index) }
            "asdfghjkl;'".forEachIndexed { index, c -> put(c, 30 + index) }
            put('`', 41)
            put('\\', 43)
            "zxcvbnm,./".forEachIndexed { index, c -> put(c, 44 + index) }
            put(' ', 57)
        }

        const val ESC = 1
        const val BACKSPACE = 14
        const val TAB = 15
        const val ENTER = 28
        const val CTRL = 29
        const val LSHIFT = 42
        const val ALT = 56
        const val UP = 103
        const val LEFT = 105
        const val RIGHT = 106
        const val DOWN = 108
        const val DELETE = 111

        fun code(char: Char): Int? = PRINTABLE[char.lowercaseChar()]

        fun functionKey(number: Int): Int = when (number) {
            in 1..10 -> 58 + number
            11 -> 87
            12 -> 88
            else -> error("no such function key: $number")
        }
    }
}
