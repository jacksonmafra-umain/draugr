package com.umain.draugr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umain.draugr.host.hostWarning
import com.umain.draugr.storage.Settings
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText

@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onSelfTest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        GlitchText(text = "SETTINGS", style = MaterialTheme.typography.displayMedium)

        BracketPanel(header = "DISPLAY", modifier = Modifier.fillMaxWidth()) {
            Toggle(
                label = "SCANLINES",
                detail = "COSTS FRAMES OVER A LIVE VM CANVAS",
                enabled = settings.scanlinesEnabled,
            ) {
                onSettingsChange(settings.copy(scanlinesEnabled = !settings.scanlinesEnabled))
            }
            Toggle(
                label = "BOOT LOG OPEN",
                detail = "START WITH THE SERIAL LOG EXPANDED",
                enabled = settings.bootLogExpandedByDefault,
            ) {
                onSettingsChange(
                    settings.copy(
                        bootLogExpandedByDefault = !settings.bootLogExpandedByDefault,
                    ),
                )
            }
        }

        hostWarning()?.let { warning ->
            BracketPanel(
                header = "HOST WARNING",
                state = PanelState.ERROR,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DangerText,
                )
            }
        }

        BracketPanel(header = "DIAGNOSTICS", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = ">> HOST SELF TEST",
                style = MaterialTheme.typography.titleMedium,
                color = AccentText,
                modifier = Modifier.clickable { onSelfTest() },
            )
            Text(
                text = "STARTS THE ASSET SERVER AND REPORTS CROSS-ORIGIN ISOLATION, " +
                    "SHAREDARRAYBUFFER, WASM THREADS AND RANGE SUPPORT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }

        BracketPanel(header = "NETWORK", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "THIS APP MAKES NO OUTBOUND REQUESTS. THE ONLY PEER IS THE EMBEDDED " +
                    "SERVER ON 127.0.0.1.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun Toggle(
    label: String,
    detail: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryText,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
        Text(
            text = if (enabled) "[ ON ]" else "[ OFF ]",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) AccentText else MutedText,
        )
    }
}
