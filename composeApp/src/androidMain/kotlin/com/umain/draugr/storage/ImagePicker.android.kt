package com.umain.draugr.storage

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source

@Composable
actual fun rememberImagePicker(
    machineId: String,
    onPicked: (PickedImage?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = SideloadStore()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, null, null, null, null)
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        }
                        ?: "sideload.img"

                    val target = store.targetPath(machineId, name)
                    platformFileSystem.createDirectories(store.directoryFor(machineId))
                    // Streamed, not read into memory: these images run to gigabytes.
                    var copied = 0L
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        platformFileSystem.sink(target).buffer().use { sink ->
                            val source = input.source().buffer()
                            val chunk = 1L * 1024 * 1024
                            while (true) {
                                val read = source.read(sink.buffer, chunk)
                                if (read == -1L) break
                                copied += read
                                sink.emitCompleteSegments()
                            }
                        }
                    }
                    PickedImage(fileName = name, sizeBytes = copied)
                }.getOrNull()
            }
            if (result != null) {
                store.register(
                    SideloadedImage(
                        machineId = machineId,
                        fileName = result.fileName,
                        sizeBytes = result.sizeBytes,
                        sha256 = store.sha256Of(store.targetPath(machineId, result.fileName)),
                    ),
                )
            }
            onPicked(result)
        }
    }

    return { launcher.launch(arrayOf("*/*")) }
}
