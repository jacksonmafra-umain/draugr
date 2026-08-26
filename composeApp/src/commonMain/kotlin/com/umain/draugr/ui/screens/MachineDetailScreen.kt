package com.umain.draugr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.formatBytes
import com.umain.draugr.storage.SideloadStore
import com.umain.draugr.storage.SideloadValidation
import com.umain.draugr.storage.rememberImagePicker
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.components.asciiProgressBar
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.ui.theme.SecondaryText

@Composable
fun MachineDetailScreen(
    spec: MachineSpec,
    fetchProgress: Float?,
    onSummon: () -> Unit,
    onSideloaded: (MachineSpec) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideload = remember { SideloadStore() }
    var picking by remember { mutableStateOf(false) }
    var validation by remember(spec.id) {
        mutableStateOf<SideloadValidation?>(null)
    }
    val pickImage = rememberImagePicker(machineId = spec.id) { picked ->
        picking = false
        if (picked == null) return@rememberImagePicker
        val image = sideload.imageFor(spec.id)
        val result = sideload.validate(spec, image)
        validation = result
        if (result is SideloadValidation.Ok && image != null) {
            onSideloaded(
                spec.copy(
                    bundled = true,
                    sizeBytes = image.sizeBytes,
                    assets = spec.assets.copy(hda = sideload.serverPathOf(image)),
                    note = "SIDELOADED: ${image.fileName}",
                ),
            )
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "<< BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.clickable { onBack() },
        )
        GlitchText(text = spec.displayName, style = MaterialTheme.typography.displayMedium)

        BracketPanel(header = "SPEC", modifier = Modifier.fillMaxWidth()) {
            SpecRow("ID", spec.id)
            SpecRow("CPU", spec.cpuLabel)
            SpecRow("UI", spec.ui.label)
            SpecRow("ENGINE", spec.engine.label)
            SpecRow("RAM", "${spec.memMb}MB")
            SpecRow("VGA RAM", "${spec.vgaMemMb}MB")
            SpecRow("PAYLOAD", spec.sizeBytes.formatBytes())
        }

        BracketPanel(header = "ASSETS", modifier = Modifier.fillMaxWidth()) {
            val assets = listOfNotNull(
                spec.assets.bios?.let { "BIOS" to it },
                spec.assets.vgabios?.let { "VGABIOS" to it },
                spec.assets.kernel?.let { "KERNEL" to it },
                spec.assets.initrd?.let { "INITRD" to it },
                spec.assets.hda?.let { "HDA" to it },
                spec.assets.fda?.let { "FDA" to it },
                spec.assets.cdrom?.let { "CDROM" to it },
            )
            assets.forEach { (kind, path) -> SpecRow(kind, path) }
        }

        if (spec.exceedsIosMemoryBudget) {
            BracketPanel(
                header = "MEMORY WARNING",
                state = PanelState.ERROR,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "REQUESTS ${spec.memMb}MB, ABOVE THE ${MachineSpec.IOS_MEM_BUDGET_MB}MB " +
                        "IOS BUDGET. WEBCONTENT WILL LIKELY BE JETSAM-KILLED BEFORE BOOT COMPLETES.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DangerText,
                )
            }
        }

        spec.note?.let { note ->
            BracketPanel(header = "NOTE", modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = note.uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }

        if (fetchProgress != null) {
            BracketPanel(header = "FETCH", state = PanelState.RUNNING, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = asciiProgressBar(fetchProgress),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AccentText,
                )
            }
        }

        if (spec.bundled) {
            Text(
                text = ">> SUMMON",
                style = MaterialTheme.typography.titleMedium,
                color = AccentText,
                modifier = Modifier.clickable { onSummon() },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "[SIDELOAD REQUIRED]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                )
                Text(
                    text = "THIS IMAGE IS LICENCE GATED AND NEVER SHIPS WITH THE APP. PLACE YOUR " +
                        "OWN COPY THROUGH THE SIDELOAD PATH TO ENABLE IT.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
                Text(
                    text = if (picking) ":: COPYING IMAGE" else ">> SIDELOAD IMAGE",
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText,
                    modifier = Modifier.clickable {
                        picking = true
                        pickImage()
                    },
                )
                validation?.let { result ->
                    Text(
                        text = ":: ${result.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is SideloadValidation.Ok) AccentText else DangerText,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A long asset path used to run straight into its label. The label keeps its width, the
        // value takes the rest and wraps right-aligned inside it.
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
