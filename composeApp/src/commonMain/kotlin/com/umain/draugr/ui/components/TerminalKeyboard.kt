package com.umain.draugr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umain.draugr.input.KeyLayout
import com.umain.draugr.input.KeyboardLayer
import com.umain.draugr.input.Modifier as KeyModifier
import com.umain.draugr.input.Scancodes
import com.umain.draugr.input.TerminalKey
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.BorderColor
import com.umain.draugr.ui.theme.BorderColorAccent
import com.umain.draugr.ui.theme.ComponentBackground
import com.umain.draugr.ui.theme.ComponentBackgroundAccent
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText

/**
 * A phone keyboard, not a shrunken desktop one. The system IME is unusable over a web view
 * canvas on iOS, so this is the only way to type into a guest.
 *
 * At most ten columns per row so keys stay hittable. The letters layer holds letters and the
 * keys you reach for constantly; numbers, symbols, arrows and the function row live behind
 * `?123`. Putting all of it on one board clipped the function row off the screen and split
 * `BSP` across two lines.
 */
@Composable
fun TerminalKeyboard(
    onCodes: (IntArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(emptySet<KeyModifier>()) }
    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }

    fun press(key: TerminalKey) {
        onCodes(Scancodes.withModifiers(held.map { it.make }, key.strokes()))
        // Shift behaves like a phone's: one character, then it releases. Ctrl and Alt latch,
        // because a terminal needs Ctrl held across several keys.
        if (KeyModifier.SHIFT in held) held = held - KeyModifier.SHIFT
    }

    fun toggle(modifier: KeyModifier) {
        held = if (modifier in held) held - modifier else held + modifier
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = if (maxHeight < 320.dp) 3.dp else 5.dp
        val rowCount = ROW_COUNT + if (held.isEmpty()) 0 else 1
        // Capped as well as floored: a key that fills a tenth of a tall screen looks nothing
        // like a key, and the height it takes is height the guest loses.
        val keyHeight = ((maxHeight - gap * (rowCount - 1)) / rowCount)
            .coerceIn(MIN_KEY_HEIGHT, MAX_KEY_HEIGHT)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            when (layer) {
                KeyboardLayer.LETTERS -> {
                    KeyRow(gap) {
                        KeyLayout.lettersTop.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                    }
                    // Half-key indent either side, the way every phone keyboard does it.
                    KeyRow(gap) {
                        Spacer(Modifier.weight(0.5f))
                        KeyLayout.lettersHome.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                        Spacer(Modifier.weight(0.5f))
                    }
                    KeyRow(gap) {
                        Cap(
                            label = "SHIFT",
                            height = keyHeight,
                            weight = 1.5f,
                            active = KeyModifier.SHIFT in held,
                        ) { toggle(KeyModifier.SHIFT) }
                        KeyLayout.lettersBottom.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                        Cap(KeyLayout.backspace.label, keyHeight, KeyLayout.backspace.weight) {
                            press(KeyLayout.backspace)
                        }
                    }
                    KeyRow(gap) {
                        Cap("?123", keyHeight, 1.5f, muted = true) {
                            layer = KeyboardLayer.SYMBOLS
                        }
                        Cap("CTRL", keyHeight, 1.5f, active = KeyModifier.CTRL in held) {
                            toggle(KeyModifier.CTRL)
                        }
                        Cap("ALT", keyHeight, 1.2f, active = KeyModifier.ALT in held) {
                            toggle(KeyModifier.ALT)
                        }
                        Cap(KeyLayout.space.label, keyHeight, KeyLayout.space.weight) {
                            press(KeyLayout.space)
                        }
                        Cap(KeyLayout.period.label, keyHeight, 1f) { press(KeyLayout.period) }
                        Cap(KeyLayout.enter.label, keyHeight, KeyLayout.enter.weight) {
                            press(KeyLayout.enter)
                        }
                    }
                }

                KeyboardLayer.SYMBOLS -> {
                    KeyRow(gap) {
                        KeyLayout.symbolsTop.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                    }
                    KeyRow(gap) {
                        Spacer(Modifier.weight(0.5f))
                        KeyLayout.symbolsSecond.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                        Spacer(Modifier.weight(0.5f))
                    }
                    KeyRow(gap) {
                        KeyLayout.symbolsThird.forEach { key ->
                            Cap(key.label, keyHeight, key.weight) { press(key) }
                        }
                    }
                    // Twelve function keys never fit one phone row, so this one scrolls.
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(keyHeight),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        item {
                            FixedCap("ABC", keyHeight, muted = true) {
                                layer = KeyboardLayer.LETTERS
                            }
                        }
                        items(KeyLayout.functionKeys, key = { it.label }) { key ->
                            FixedCap(key.label, keyHeight) { press(key) }
                        }
                    }
                }
            }

            if (held.isNotEmpty()) {
                Text(
                    text = ":: ${held.joinToString(" + ") { it.label }} HELD",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyRow(gap: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun RowScope.Cap(
    label: String,
    height: Dp,
    weight: Float,
    active: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .height(height)
            .border(1.dp, if (active) BorderColorAccent else BorderColor)
            .background(if (active) ComponentBackgroundAccent else ComponentBackground)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        CapLabel(label, active, muted)
    }
}

/** For the scrolling function row, where a key cannot take a weight. */
@Composable
private fun FixedCap(
    label: String,
    height: Dp,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(height)
            .border(1.dp, BorderColor)
            .background(ComponentBackground)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CapLabel(label, active = false, muted = muted)
    }
}

@Composable
private fun CapLabel(label: String, active: Boolean, muted: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = when {
            active -> AccentText
            muted -> MutedText
            else -> PrimaryText
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

/** Four rows in either layer; a fifth appears only while a modifier is held. */
private const val ROW_COUNT = 4

private val MIN_KEY_HEIGHT = 30.dp
private val MAX_KEY_HEIGHT = 52.dp
