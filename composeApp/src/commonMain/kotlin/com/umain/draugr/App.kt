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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.umain.draugr.catalog.CatalogRepository
import com.umain.draugr.storage.SideloadStore
import com.umain.draugr.catalog.MachineSpec
import com.umain.draugr.ui.components.scanlineOverlay
import com.umain.draugr.ui.screens.CatalogScreen
import com.umain.draugr.ui.screens.MachineDetailScreen
import com.umain.draugr.storage.SettingsStore
import com.umain.draugr.ui.screens.SelfTestScreen
import com.umain.draugr.ui.screens.SettingsScreen
import com.umain.draugr.ui.screens.SnapshotScreen
import com.umain.draugr.ui.screens.VmScreen
import com.umain.draugr.vm.VmController
import kotlinx.coroutines.launch
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.DraugrTheme
import com.umain.draugr.ui.theme.MutedText

private sealed interface Route {
    data object Catalog : Route
    data object SelfTest : Route
    data class Detail(val spec: MachineSpec) : Route
    data class Vm(val spec: MachineSpec) : Route
    data class Snapshots(val spec: MachineSpec) : Route
    data object Settings : Route
}

/**
 * One controller per machine for the lifetime of the app session, so opening the state list and
 * coming back does not tear down a running guest.
 */
private val controllers = mutableMapOf<String, VmController>()

@Composable
private fun rememberVmController(spec: MachineSpec): VmController = remember(spec.id) {
    controllers.getOrPut(spec.id) { VmController(spec = spec) }
}

@Composable
fun DraugrApp() {
    var machines by remember { mutableStateOf<List<MachineSpec>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Route>(Route.Catalog) }
    val settingsStore = remember { SettingsStore() }
    var settings by remember { mutableStateOf(settingsStore.load()) }

    LaunchedEffect(Unit) {
        runCatching { CatalogRepository(sideload = SideloadStore()).load() }
            .onSuccess { machines = it.machines }
            .onFailure { failure = it.message ?: "CATALOG UNREADABLE" }
    }

    DraugrTheme(scanlinesEnabled = settings.scanlinesEnabled) {
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
                        onBoot = { route = Route.Vm(it) },
                        onInspect = { route = Route.Detail(it) },
                        onSettings = { route = Route.Settings },
                    )

                    Route.SelfTest -> SelfTestScreen(onBack = { route = Route.Settings })

                    Route.Settings -> SettingsScreen(
                        settings = settings,
                        onSettingsChange = { updated ->
                            settings = updated
                            settingsStore.save(updated)
                        },
                        onSelfTest = { route = Route.SelfTest },
                        onBack = { route = Route.Catalog },
                    )

                    is Route.Vm -> {
                        val scope = rememberCoroutineScope()
                        // Kept across the snapshot screen so the guest is not thrown away by a
                        // trip to the state list.
                        val controller = rememberVmController(current.spec)
                        LaunchedEffect(controller) { controller.start() }
                        LaunchedEffect(controller, settings.terminalZoom) {
                            controller.setZoom(settings.terminalZoom)
                        }
                        VmScreen(
                            spec = current.spec,
                            controller = controller,
                            onSnapshot = { controller.requestSnapshot() },
                            onRestore = { controller.requestRestoreLatest() },
                            onOpenSnapshots = { route = Route.Snapshots(current.spec) },
                            onZoomChange = { factor ->
                                settings = settings.copy(terminalZoom = factor)
                                settingsStore.save(settings)
                            },
                            onExit = {
                                controllers.remove(current.spec.id)
                                route = Route.Catalog
                            },
                        )
                    }

                    is Route.Snapshots -> {
                        val controller = rememberVmController(current.spec)
                        var entries by remember(current.spec.id) {
                            mutableStateOf(controller.savedSnapshots())
                        }
                        SnapshotScreen(
                            spec = current.spec,
                            entries = entries,
                            thumbnailOf = { controller.thumbnailOf(it) },
                            onRestore = { entry ->
                                controller.requestRestore(entry)
                                route = Route.Vm(current.spec)
                            },
                            onDelete = { entry ->
                                controller.delete(entry)
                                entries = controller.savedSnapshots()
                            },
                            onBack = { route = Route.Vm(current.spec) },
                        )
                    }

                    is Route.Detail -> MachineDetailScreen(
                        spec = current.spec,
                        fetchProgress = null,
                        onSummon = { route = Route.Vm(current.spec) },
                        onSideloaded = { updated ->
                            // The machine is bootable now, so the catalog entry has to change
                            // with it rather than staying dimmed until a restart.
                            machines = loaded.map { if (it.id == updated.id) updated else it }
                            route = Route.Detail(updated)
                        },
                        onBack = { route = Route.Catalog },
                    )
                }
            }
        }
    }
}
