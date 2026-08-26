package com.umain.draugr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umain.draugr.input.KeyLayout
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
 * The system IME is unusable over a web view canvas on iOS, so this is the keyboard. It emits
 * scancodes through the bridge, never text, and modifiers latch until tapped again.
 */
@Composable
fun TerminalKeyboard(
    onCodes: (IntArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(emptySet<KeyModifier>()) }

    fun press(key: TerminalKey) {
        val strokes = Scancodes.withModifiers(held.map { it.make }, key.strokes())
        onCodes(strokes)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Eight rows have to fit whatever height the surface can spare, which in landscape is
        // roughly half of what portrait offers. Squeezing them clipped the glyphs, so the key
        // height is derived instead of fixed.
        val gap = if (maxHeight < 360.dp) 2.dp else 4.dp
        val perRow = ((maxHeight - gap * (ROW_COUNT - 1)) / ROW_COUNT)
        val keyPadding = ((perRow - GLYPH_HEIGHT) / 2).coerceIn(2.dp, 10.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // Function keys get their own scrolling row: squeezing twelve of them in beside the
        // modifiers clipped the last few.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(KeyLayout.functionKeys, key = { it.label }) { key ->
                KeyCap(label = key.label, verticalPadding = keyPadding) { press(key) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            KeyModifier.entries.forEach { mod ->
                val active = mod in held
                KeyCap(
                    label = mod.label,
                    active = active,
                    verticalPadding = keyPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    held = if (active) held - mod else held + mod
                }
            }
            KeyCap(label = "ESC", verticalPadding = keyPadding, modifier = Modifier.weight(1f)) {
                press(TerminalKey("ESC", Scancodes.ESC))
            }
        }

        KeyLayout.mainRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { key ->
                    KeyCap(
                        label = key.label,
                        verticalPadding = keyPadding,
                        modifier = Modifier.weight(key.weight),
                    ) { press(key) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Text(
                text = if (held.isEmpty()) "" else held.joinToString("+") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = AccentText,
                modifier = Modifier.weight(3f).padding(start = 4.dp),
            )
            KeyLayout.arrows.forEach { key ->
                KeyCap(
                    label = key.label,
                    verticalPadding = keyPadding,
                    modifier = Modifier.weight(1f),
                ) { press(key) }
            }
        }
    }
    }
}

/** Function row, modifier row, five main rows, arrow row. */
private const val ROW_COUNT = 8

/** Roughly the height of a labelSmall glyph box, used to derive key padding. */
private val GLYPH_HEIGHT = 14.dp

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    verticalPadding: Dp = 8.dp,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) AccentText else MutedText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, if (active) BorderColorAccent else BorderColor)
            .background(if (active) ComponentBackgroundAccent else ComponentBackground)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = verticalPadding),
    )
}
