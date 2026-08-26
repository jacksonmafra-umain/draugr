package com.umain.draugr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.umain.draugr.catalog.formatBytes
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.components.Stat
import com.umain.draugr.ui.components.StatBar
import com.umain.draugr.ui.components.TerminalKeyboard
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.Background
import com.umain.draugr.ui.theme.BorderColorAccent
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.vm.VmController
import com.umain.draugr.vm.SuspendReason
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
    onRestore: () -> Unit,
    onOpenSnapshots: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val log by controller.log.collectAsState()
    val geometry by controller.geometry.collectAsState()
    val stats by controller.stats.collectAsState()

    var uptimeSeconds by remember { mutableStateOf(0L) }
    var logExpanded by remember { mutableStateOf(true) }
    // A machine you cannot type into is not much use, so the keyboard starts open.
    var keyboardVisible by remember { mutableStateOf(true) }

    // The keyboard and an expanded log cannot both have the room they want.
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) logExpanded = false
    }

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
                GlitchText(
                    text = spec.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A fault message can run to a whole sentence. Unbounded, it widened the panel
                // past the screen and took the frame off with it; the full text is in the log.
                Text(
                    text = "[${state.label}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state is VmState.Halted) DangerText else AccentText,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            StatBar(
                stats = listOf(
                    Stat("UPTIME", formatUptime(uptimeSeconds)),
                    Stat("GEOMETRY", geometry.label),
                    Stat("FETCHED", stats.fetchedBytes.formatBytes()),
                    Stat("IPS", formatIps(stats.instructionsPerSecond)),
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
        ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            // Anchored to the top: a letterboxed guest centred in a tall box left dead bands
            // above and below the output.
            contentAlignment = Alignment.TopCenter,
        ) {
            // Letterbox against whichever axis is tighter. A bare aspectRatio() is free to
            // exceed the box height, which in landscape drew the guest straight over the
            // keyboard. A text guest has no pixel size, so it gets 4:3 and the page scales its
            // glyphs to fit.
            val ratio = geometry.aspectRatio ?: TEXT_MODE_RATIO
            val boxRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else ratio
            VmSurface(
                bridge = controller.bridge,
                modifier = if (ratio >= boxRatio) {
                    Modifier.fillMaxWidth().aspectRatio(ratio)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(ratio)
                },
            )
        }

            // The keyboard floats over the guest rather than stacking under it. Stacking
            // overflowed the column in landscape and painted the guest across the keys.
            if (keyboardVisible) {
                TerminalKeyboard(
                    onCodes = { codes -> controller.sendKeys(codes) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Background.copy(alpha = 0.94f))
                        .padding(4.dp),
                )
            }
        }

        if (!keyboardVisible) {
        val suspended = state as? VmState.Suspended
        if (suspended?.reason == SuspendReason.HOST_TERMINATED) {
            BracketPanel(
                header = "HOST TERMINATED",
                state = PanelState.ERROR,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(
                    text = "THE WEB CONTENT PROCESS WAS KILLED, ALMOST CERTAINLY BY JETSAM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DangerText,
                )
                Text(
                    text = if (controller.canRestore) ">> RESTORE FROM SNAPSHOT" else ">> RESET",
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentText,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            if (controller.canRestore) onRestore() else controller.reset()
                        },
                )
            }
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
                    text = (state as? VmState.Halted)?.error?.let { ":: $it" }
                        ?: log.lastOrNull()
                        ?: ":: NO OUTPUT YET",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state is VmState.Halted) DangerText else MutedText,
                )
            }
        }

        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Action("KEYS", if (keyboardVisible) AccentText else PrimaryText) {
                    keyboardVisible = !keyboardVisible
                }
            }
            item {
                Action("PAUSE", AccentText) {
                    if (state is VmState.Suspended) controller.resume() else controller.pause()
                }
            }
            item { Action("SNAP", PrimaryText) { onSnapshot() } }
            item { Action("STATES", PrimaryText) { onOpenSnapshots() } }
            item { Action("RESET", PrimaryText) { controller.reset() } }
            item {
                Action("HALT", DangerText) {
                    controller.halt()
                    onExit()
                }
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
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Zero-padded so the HUD does not jitter as the number changes width. */
private fun formatIps(value: Long): String = when {
    value <= 0L -> "---"
    value >= 1_000_000_000L -> "${value / 1_000_000_000L}.${(value % 1_000_000_000L) / 100_000_000L}G"
    value >= 1_000_000L -> "${value / 1_000_000L}.${(value % 1_000_000L) / 100_000L}M"
    value >= 1_000L -> "${value / 1_000L}K"
    else -> value.toString()
}

/** Text guests are 80x25 characters; 4:3 is the closest thing to their real shape. */
private const val TEXT_MODE_RATIO = 4f / 3f

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
