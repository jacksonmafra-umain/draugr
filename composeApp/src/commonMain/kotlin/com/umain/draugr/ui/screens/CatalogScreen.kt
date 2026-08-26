package com.umain.draugr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.umain.draugr.catalog.CatalogFilter
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.catalog.formatBytes
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.BorderColor
import com.umain.draugr.ui.theme.BorderColorAccent
import com.umain.draugr.ui.theme.ComponentBackgroundAccent
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.ui.theme.SecondaryText
import kotlinx.coroutines.delay

private val BOOT_LINES = listOf(
    "DRAUGR v0.1 :: SUMMONING CATALOG",
    "> PROBING HOST CAPABILITIES",
    "> WASM JIT ......... AVAILABLE",
    "> SHARED MEMORY ... AVAILABLE",
    "> MOUNTING MANIFEST",
)

@Composable
fun CatalogScreen(
    machines: List<MachineSpec>,
    onBoot: (MachineSpec) -> Unit,
    onInspect: (MachineSpec) -> Unit,
    onSettings: () -> Unit,
    /** Machines with a guest still running, so the row offers to go back to it. */
    runningIds: Set<String> = emptySet(),
    /** Hoisted so coming back from a machine does not reset the filter. */
    filter: CatalogFilter,
    onFilterChange: (CatalogFilter) -> Unit,
    /** The type-out sequence is a first-run flourish, not something to sit through repeatedly. */
    playIntro: Boolean,
    onIntroPlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(if (playIntro) 0 else BOOT_LINES.size) }
    var listVisible by remember { mutableStateOf(!playIntro) }

    LaunchedEffect(playIntro) {
        if (!playIntro) return@LaunchedEffect
        while (revealed < BOOT_LINES.size) {
            delay(180)
            revealed++
        }
        delay(140)
        listVisible = true
        onIntroPlayed()
    }

    val visible = machines.filter { filter.matches(it) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlitchText(
            text = BOOT_LINES.first(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .clickable { onSettings() }
                .padding(top = 24.dp, bottom = 8.dp),
        )
        BOOT_LINES.drop(1).take((revealed - 1).coerceAtLeast(0)).forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodyMedium, color = AccentText)
        }

        if (listVisible) {
            FilterChips(
                selected = filter,
                onSelect = onFilterChange,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                itemsIndexed(visible, key = { _, spec -> spec.id }) { index, spec ->
                    MachineRow(
                        index = index + 1,
                        spec = spec,
                        running = spec.id in runningIds,
                        onBoot = { onBoot(spec) },
                        onInspect = { onInspect(spec) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selected: CatalogFilter,
    onSelect: (CatalogFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CatalogFilter.entries.toList(), key = { it.name }) { entry ->
            val active = entry == selected
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) AccentText else MutedText,
                modifier = Modifier
                    .border(1.dp, if (active) BorderColorAccent else BorderColor)
                    .then(if (active) Modifier.background(ComponentBackgroundAccent) else Modifier)
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MachineRow(
    index: Int,
    spec: MachineSpec,
    running: Boolean,
    onBoot: () -> Unit,
    onInspect: () -> Unit,
) {
    val dimmed = !spec.bundled
    BracketPanel(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.4f else 1f)
            .clickable { onInspect() },
        header = if (running) "${index.toString().padStart(2, '0')} · RUNNING" else index.toString().padStart(2, '0'),
        state = when {
            running -> PanelState.RUNNING
            dimmed -> PanelState.IDLE
            else -> PanelState.FOCUSED
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = spec.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryText,
            )
            Text(
                text = "[${spec.cpuLabel}]",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
        Text(
            text = listOf(
                spec.ui.label,
                spec.engine.label,
                "${spec.memMb}MB",
                spec.sizeBytes.formatBytes(),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
        )
        if (spec.bundled) {
            Text(
                text = if (running) ">> RESUME" else ">> BOOT",
                style = MaterialTheme.typography.labelSmall,
                color = AccentText,
                modifier = Modifier.clickable { onBoot() }.padding(top = 8.dp),
            )
        } else {
            Text(
                text = "[SIDELOAD REQUIRED]",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
