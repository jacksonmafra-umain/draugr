package com.umain.draugr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.umain.draugr.catalog.CatalogRepository
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.ui.components.scanlineOverlay
import com.umain.draugr.ui.screens.CatalogScreen
import com.umain.draugr.ui.screens.MachineDetailScreen
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.DraugrTheme
import com.umain.draugr.ui.theme.MutedText

private sealed interface Route {
    data object Catalog : Route
    data class Detail(val spec: MachineSpec) : Route
}

@Composable
fun DraugrApp() {
    var machines by remember { mutableStateOf<List<MachineSpec>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Route>(Route.Catalog) }

    LaunchedEffect(Unit) {
        runCatching { CatalogRepository().load() }
            .onSuccess { machines = it.machines }
            .onFailure { failure = it.message ?: "CATALOG UNREADABLE" }
    }

    DraugrTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scanlineOverlay()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val loaded = machines
            when {
                failure != null -> Text(
                    text = ":: ${failure!!.uppercase()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DangerText,
                    modifier = Modifier.align(Alignment.Center),
                )

                loaded == null -> Text(
                    text = ":: MOUNTING MANIFEST",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedText,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> when (val current = route) {
                    Route.Catalog -> CatalogScreen(
                        machines = loaded,
                        onBoot = { route = Route.Detail(it) },
                        onInspect = { route = Route.Detail(it) },
                    )

                    is Route.Detail -> MachineDetailScreen(
                        spec = current.spec,
                        fetchProgress = null,
                        onSummon = {},
                        onSideload = {},
                        onBack = { route = Route.Catalog },
                    )
                }
            }
        }
    }
}
