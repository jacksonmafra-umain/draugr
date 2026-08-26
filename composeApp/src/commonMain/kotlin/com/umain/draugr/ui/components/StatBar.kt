package com.umain.draugr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.MutedText

data class Stat(val label: String, val value: String, val color: Color = AccentText)

/** A row of zero-padded, monospace-aligned readouts. */
@Composable
fun StatBar(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        stats.forEach { stat ->
            Column {
                Text(
                    text = stat.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = stat.color,
                )
            }
        }
    }
}

/** `[████████░░░░░░░░] 51%` — progress as ASCII, per the visual language. */
fun asciiProgressBar(fraction: Float, width: Int = 16): String {
    val clamped = fraction.coerceIn(0f, 1f)
    val filled = (clamped * width).toInt()
    val percent = (clamped * 100).toInt().toString().padStart(3, '0')
    return buildString {
        append('[')
        repeat(filled) { append('█') }
        repeat(width - filled) { append('░') }
        append("] ")
        append(percent)
        append('%')
    }
}
