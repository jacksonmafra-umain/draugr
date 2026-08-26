package com.umain.draugr.platform

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back gesture while [enabled]. Android's back button otherwise leaves
 * the app from inside a running machine, which is never what the user meant. iOS has no system
 * back, so the on-screen affordance is the only route there.
 */
@Composable
expect fun BackGuard(enabled: Boolean, onBack: () -> Unit)
