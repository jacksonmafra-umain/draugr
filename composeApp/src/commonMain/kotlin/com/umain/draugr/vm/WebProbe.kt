package com.umain.draugr.vm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Loads [url] in the platform web view and forwards whatever the page posts back. Used by the
 * self test to prove the host really is cross-origin isolated.
 */
@Composable
expect fun WebProbe(
    url: String,
    onMessage: (String) -> Unit,
    modifier: Modifier,
)
