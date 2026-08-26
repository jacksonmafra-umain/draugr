package com.umain.draugr.ui.theme

import androidx.compose.runtime.Composable

/**
 * Paints the platform chrome black. Replaces the Activity window-color side effect the
 * Android-only original relied on.
 */
@Composable
expect fun applySystemBarStyle()
