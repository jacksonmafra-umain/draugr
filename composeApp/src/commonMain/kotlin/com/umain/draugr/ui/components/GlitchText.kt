package com.umain.draugr.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.ui.theme.SecondaryText
import kotlinx.coroutines.delay

/**
 * Chromatic aberration on a 90ms tick: magenta left, cyan right, primary on top.
 * Boot headers and machine names only, never body copy.
 */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = PrimaryText,
    tickMillis: Long = 90L,
    // Headers wrap; a name sitting in a fixed-width HUD row must not.
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
) {
    var jitter by remember { mutableStateOf(0) }
    LaunchedEffect(text) {
        val pattern = intArrayOf(0, 1, -1, 0, 1, 0, -1, 1)
        var i = 0
        while (true) {
            jitter = pattern[i % pattern.size]
            i++
            delay(tickMillis)
        }
    }
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = SecondaryText.copy(alpha = 0.7f),
            maxLines = maxLines,
            softWrap = softWrap,
            modifier = Modifier.offset(x = (-1 + jitter).dp),
        )
        Text(
            text = text,
            style = style,
            color = PrimaryText.copy(alpha = 0.7f),
            maxLines = maxLines,
            softWrap = softWrap,
            modifier = Modifier.offset(x = (1 - jitter).dp),
        )
        Text(text = text, style = style, color = color, maxLines = maxLines, softWrap = softWrap)
    }
}
