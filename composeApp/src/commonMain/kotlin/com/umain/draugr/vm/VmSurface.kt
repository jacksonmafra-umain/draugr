package com.umain.draugr.vm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The one platform view in the app. Everything else is shared Compose. */
@Composable
expect fun VmSurface(bridge: VmBridge, modifier: Modifier)
