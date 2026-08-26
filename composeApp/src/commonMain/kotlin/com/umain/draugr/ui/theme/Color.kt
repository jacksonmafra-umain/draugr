package com.umain.draugr.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF000000)
val PrimaryText = Color(0xFF00FFFF)
val AccentText = Color(0xFF22DE80)
val SecondaryText = Color(0xFFE879F9)
val MutedText = PrimaryText.copy(alpha = 0.6f)

/** Reserved for halt, crash and jetsam states. Nothing else may use it. */
val DangerText = Color(0xFFFF3864)

val BorderColor = PrimaryText.copy(alpha = 0.3f)
val BorderColorHover = PrimaryText.copy(alpha = 0.5f)
val BorderColorAccent = AccentText.copy(alpha = 0.5f)
val BorderColorDanger = DangerText.copy(alpha = 0.5f)

val ComponentBackground = Color(0x33083344)
val ComponentBackgroundHover = Color(0x4D083344)
val ComponentBackgroundAccent = Color(0x33052E16)
