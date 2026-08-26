package com.umain.draugr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umain.draugr.server.AssetServer
import com.umain.draugr.server.draugrAssetProvider
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.vm.WebProbe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Build order step two, kept as a permanent diagnostic: start the asset server, load the probe
 * page in the platform web view, and report whether the document is cross-origin isolated.
 */
@Composable
fun SelfTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<Map<String, String>?>(null) }
    val server = remember { AssetServer(draugrAssetProvider()) }

    LaunchedEffect(Unit) {
        runCatching { server.start() }
            .onSuccess { origin = it }
            .onFailure { failure = it.message ?: "SERVER DID NOT START" }
    }
    DisposableEffect(Unit) { onDispose { server.stop() } }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "<< BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.clickable { onBack() },
        )
        GlitchText(text = "HOST SELF TEST", style = MaterialTheme.typography.displayMedium)

        val currentOrigin = origin
        val currentReport = report
        BracketPanel(
            header = "SERVER",
            state = if (failure != null) PanelState.ERROR else PanelState.RUNNING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = failure?.uppercase() ?: currentOrigin ?: ":: STARTING",
                style = MaterialTheme.typography.bodyMedium,
                color = if (failure != null) DangerText else AccentText,
            )
        }

        if (currentReport != null) {
            val isolated = currentReport["crossOriginIsolated"] == "true"
            BracketPanel(
                header = "CAPABILITIES",
                state = if (isolated) PanelState.RUNNING else PanelState.ERROR,
                modifier = Modifier.fillMaxWidth(),
            ) {
                currentReport.forEach { (key, value) ->
                    val good = value == "true" || value == "206"
                    Text(
                        text = "${key.uppercase()} = ${value.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            value == "false" -> DangerText
                            good -> AccentText
                            else -> MutedText
                        },
                    )
                }
            }
        }

        if (currentReport != null) {
            val isolated = currentReport["crossOriginIsolated"] == "true"
            BracketPanel(
                header = "VERDICT",
                state = if (isolated) PanelState.RUNNING else PanelState.ERROR,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isolated) {
                        "ISOLATED. THREADED WASM ENGINES ARE AVAILABLE."
                    } else {
                        "NOT ISOLATED. NO SHAREDARRAYBUFFER, SO ONLY SINGLE-THREADED " +
                            "ENGINE BUILDS WILL RUN ON THIS HOST."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isolated) AccentText else DangerText,
                )
            }
        }

        if (currentOrigin != null) {
            WebProbe(
                url = "$currentOrigin/isolation-check.html",
                onMessage = { json -> report = parseProbeReport(json) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}

private val probeJson = Json { ignoreUnknownKeys = true }

internal fun parseProbeReport(raw: String): Map<String, String> {
    val element = runCatching { probeJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        ?: return mapOf("parse" to "failed")
    val ordered = listOf(
        "crossOriginIsolated",
        "secureContext",
        "sharedArrayBuffer",
        "wasm",
        "wasmThreads",
        "atomicsWait",
        "workers",
        "coop",
        "coep",
        "corp",
        "rangeStatus",
        "rangeSupported",
        "origin",
    )
    val out = LinkedHashMap<String, String>()
    ordered.forEach { key ->
        val value = element[key] ?: return@forEach
        val primitive = value as? JsonPrimitive ?: return@forEach
        out[key] = primitive.booleanOrNull?.toString() ?: value.jsonPrimitive.content
    }
    element["error"]?.let { out["error"] = it.jsonPrimitive.content }
    return out
}
