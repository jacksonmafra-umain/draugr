package com.umain.draugr.platform

import androidx.compose.runtime.Composable

/** iOS has no system back button; navigation happens through the screens' own controls. */
@Composable
actual fun BackGuard(enabled: Boolean, onBack: () -> Unit) = Unit
