package com.umain.draugr.storage

import androidx.compose.runtime.Composable

data class PickedImage(val fileName: String, val sizeBytes: Long)

/**
 * Opens the platform document picker and copies the chosen file into the machine's sideload
 * directory. Returns null when the user cancels or the copy fails.
 */
@Composable
expect fun rememberImagePicker(
    machineId: String,
    onPicked: (PickedImage?) -> Unit,
): () -> Unit
