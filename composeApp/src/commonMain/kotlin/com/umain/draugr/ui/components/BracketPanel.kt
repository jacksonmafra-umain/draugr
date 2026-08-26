package com.umain.draugr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umain.draugr.ui.theme.Background
import com.umain.draugr.ui.theme.BorderColor
import com.umain.draugr.ui.theme.BorderColorAccent
import com.umain.draugr.ui.theme.BorderColorDanger
import com.umain.draugr.ui.theme.BorderColorHover
import com.umain.draugr.ui.theme.ComponentBackground
import com.umain.draugr.ui.theme.MutedText

/** Border treatment is driven by state, never chosen ad hoc at the call site. */
enum class PanelState { IDLE, RUNNING, FOCUSED, ERROR }

val PanelState.borderColor: Color
    get() = when (this) {
        PanelState.IDLE -> BorderColor
        PanelState.RUNNING -> BorderColorAccent
        PanelState.FOCUSED -> BorderColorHover
        PanelState.ERROR -> BorderColorDanger
    }

/**
 * The container primitive: a 1px frame with L-shaped corner brackets, and an optional header
 * label that sits on the top border with a background-colored gap punched behind it.
 */
@Composable
fun BracketPanel(
    modifier: Modifier = Modifier,
    header: String? = null,
    state: PanelState = PanelState.IDLE,
    filled: Boolean = false,
    armLength: Dp = 12.dp,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = state.borderColor
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .then(if (filled) Modifier.background(ComponentBackground) else Modifier)
                .drawBehind { drawFrame(border, armLength.toPx()) }
                .padding(contentPadding),
            content = content,
        )
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                modifier = Modifier
                    .offset(x = armLength + 6.dp, y = (-6).dp)
                    .background(Background)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

private fun DrawScope.drawFrame(color: Color, arm: Float) {
    val w = size.width
    val h = size.height
    val hairline = 1f

    drawLine(color, Offset(0f, 0f), Offset(w, 0f), hairline)
    drawLine(color, Offset(0f, h), Offset(w, h), hairline)
    drawLine(color, Offset(0f, 0f), Offset(0f, h), hairline)
    drawLine(color, Offset(w, 0f), Offset(w, h), hairline)

    // Corner brackets: same hue, roughly double the frame's presence.
    val bracket = color.copy(alpha = (color.alpha * 2.2f).coerceAtMost(1f))
    val thick = hairline * 2f

    drawLine(bracket, Offset(0f, 0f), Offset(arm, 0f), thick)
    drawLine(bracket, Offset(0f, 0f), Offset(0f, arm), thick)

    drawLine(bracket, Offset(w - arm, 0f), Offset(w, 0f), thick)
    drawLine(bracket, Offset(w, 0f), Offset(w, arm), thick)

    drawLine(bracket, Offset(0f, h - arm), Offset(0f, h), thick)
    drawLine(bracket, Offset(0f, h), Offset(arm, h), thick)

    drawLine(bracket, Offset(w - arm, h), Offset(w, h), thick)
    drawLine(bracket, Offset(w, h - arm), Offset(w, h), thick)
}
