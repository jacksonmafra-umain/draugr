package com.umain.draugr.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.formatBytes
import com.umain.draugr.storage.SnapshotEntry
import com.umain.draugr.storage.formatTimestamp
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun SnapshotScreen(
    spec: MachineSpec,
    entries: List<SnapshotEntry>,
    thumbnailOf: (SnapshotEntry) -> ByteArray?,
    onRestore: (SnapshotEntry) -> Unit,
    onDelete: (SnapshotEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<SnapshotEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "<< BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.clickable { onBack() },
        )
        GlitchText(
            text = "SNAPSHOTS",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = ":: ${spec.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (entries.isEmpty()) {
            BracketPanel(header = "EMPTY", modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO SAVED STATES. TAKE ONE FROM THE MACHINE SCREEN, OR LET THE APP " +
                        "BACKGROUND ITSELF WHILE THE GUEST IS RUNNING.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                val confirming = pending?.id == entry.id
                BracketPanel(
                    header = formatTimestamp(entry.createdAtMillis),
                    state = if (confirming) PanelState.ERROR else PanelState.IDLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumbnail(bytes = thumbnailOf(entry))
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = entry.sizeBytes.formatBytes(),
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryText,
                            )
                            Text(
                                text = if (entry.hasThumbnail) "FRAMEBUFFER CAPTURED" else "TEXT MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = ">> RESTORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentText,
                            modifier = Modifier.clickable { onRestore(entry) },
                        )
                        Text(
                            text = if (confirming) ">> CONFIRM DELETE" else ">> DELETE",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerText,
                            modifier = Modifier.clickable {
                                // Deleting a state is not undoable, so it takes two taps.
                                if (confirming) {
                                    pending = null
                                    onDelete(entry)
                                } else {
                                    pending = entry
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(bytes: ByteArray?) {
    val bitmap: ImageBitmap? = remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    Box(
        modifier = Modifier.size(width = 96.dp, height = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "[ NO\n  IMAGE ]",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                modifier = Modifier.height(72.dp),
            )
        }
    }
}
