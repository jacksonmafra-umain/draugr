package com.umain.draugr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.components.Stat
import com.umain.draugr.ui.components.StatBar
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.Background
import com.umain.draugr.ui.theme.BorderColorAccent
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.vm.VmController
import com.umain.draugr.vm.VmState
import com.umain.draugr.vm.VmSurface
import com.umain.draugr.vm.isLive
import com.umain.draugr.vm.label
import kotlinx.coroutines.delay

@Composable
fun VmScreen(
    spec: MachineSpec,
    controller: VmController,
    onSnapshot: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val log by controller.log.collectAsState()
    val geometry by controller.geometry.collectAsState()

    var uptimeSeconds by remember { mutableStateOf(0L) }
    var logExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(state.label) {
        if (state is VmState.Running) {
            while (true) {
                delay(1000)
                uptimeSeconds++
            }
        }
    }

    // A booting guest is exactly when the log matters, so it stays open until it is running.
    LaunchedEffect(state.label) {
        if (state is VmState.Running) logExpanded = false
    }

    val panelState = when (state) {
        is VmState.Halted -> PanelState.ERROR
        is VmState.Running -> PanelState.RUNNING
        else -> PanelState.IDLE
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        BracketPanel(
            header = "MACHINE",
            state = panelState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlitchText(text = spec.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "[${state.label}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state is VmState.Halted) DangerText else AccentText,
                )
            }
            StatBar(
                stats = listOf(
                    Stat("UPTIME", formatUptime(uptimeSeconds)),
                    Stat("GEOMETRY", geometry.label),
                    Stat("RAM", "${spec.memMb}MB"),
                    Stat("ENGINE", spec.engine.label),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .background(Background)
                .border(1.dp, BorderColorAccent),
            contentAlignment = Alignment.Center,
        ) {
            // The surface is always letterboxed to a ratio. A text guest has no pixel size,
            // so it gets 4:3 and the page scales its glyphs to fit; a graphical guest gets its
            // own ratio. Sizing the web view by fill or matchParentSize measured it at zero
            // height inside this weighted box.
            VmSurface(
                bridge = controller.bridge,
                modifier = Modifier.aspectRatio(geometry.aspectRatio ?: TEXT_MODE_RATIO),
            )
        }

        BracketPanel(
            header = if (logExpanded) "BOOT LOG" else "BOOT LOG [COLLAPSED]",
            state = if (state.isLive) PanelState.RUNNING else PanelState.IDLE,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { logExpanded = !logExpanded },
        ) {
            if (logExpanded) {
                val listState = rememberLazyListState()
                LaunchedEffect(log.size) {
                    if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 180.dp).fillMaxWidth(),
                ) {
                    items(log) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentText,
                        )
                    }
                }
            } else {
                Text(
                    text = log.lastOrNull() ?: ":: NO OUTPUT YET",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Action("PAUSE", AccentText) {
                if (state is VmState.Suspended) controller.resume() else controller.pause()
            }
            Action("SNAPSHOT", PrimaryText) { onSnapshot() }
            Action("RESET", PrimaryText) { controller.reset() }
            Action("HALT", DangerText) {
                controller.halt()
                onExit()
            }
        }
    }
}

@Composable
private fun Action(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Text guests are 80x25 characters; 4:3 is the closest thing to their real shape. */
private const val TEXT_MODE_RATIO = 4f / 3f

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
