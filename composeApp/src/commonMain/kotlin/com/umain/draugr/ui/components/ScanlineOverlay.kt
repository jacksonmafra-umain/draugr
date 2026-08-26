package com.umain.draugr.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.umain.draugr.ui.theme.LocalScanlinesEnabled
import com.umain.draugr.ui.theme.PrimaryText

/**
 * CRT scanlines plus a very slow vertical sweep. Applied at the app root and switched off
 * from settings, because it costs frames when it sits over a live VM canvas.
 */
fun Modifier.scanlineOverlay(spacingDp: Float = 3f): Modifier = composed {
    val enabled = LocalScanlinesEnabled.current
    if (!enabled) return@composed this
    val sweep = scanlineSweep()
    drawWithContent {
        drawContent()
        val step = spacingDp * density
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = PrimaryText.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
        val bandHeight = size.height * 0.35f
        val top = (sweep * (size.height + bandHeight)) - bandHeight
        drawRect(
            brush = Brush.verticalGradient(
                0f to PrimaryText.copy(alpha = 0f),
                0.5f to PrimaryText.copy(alpha = 0.02f),
                1f to PrimaryText.copy(alpha = 0f),
                startY = top,
                endY = top + bandHeight,
            ),
        )
    }
}

@Composable
private fun scanlineSweep(): Float = rememberInfiniteProgress(durationMillis = 9000)
