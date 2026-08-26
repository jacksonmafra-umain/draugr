package com.umain.draugr.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Whether the scanline overlay is painted. It costs frames over a live VM canvas. */
val LocalScanlinesEnabled = compositionLocalOf { true }

private val DraugrColorScheme = darkColorScheme(
    primary = PrimaryText,
    onPrimary = Background,
    secondary = AccentText,
    onSecondary = Background,
    tertiary = SecondaryText,
    onTertiary = Background,
    background = Background,
    onBackground = PrimaryText,
    surface = Background,
    onSurface = PrimaryText,
    surfaceVariant = ComponentBackground,
    onSurfaceVariant = MutedText,
    outline = BorderColor,
    error = DangerText,
    onError = Background,
)

@Composable
fun DraugrTheme(
    scanlinesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    applySystemBarStyle()
    val typography = draugrTypography()
    MaterialTheme(
        colorScheme = DraugrColorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalScanlinesEnabled provides scanlinesEnabled,
            LocalTextStyle provides typography.bodyLarge,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Background)) {
                content()
            }
        }
    }
}
