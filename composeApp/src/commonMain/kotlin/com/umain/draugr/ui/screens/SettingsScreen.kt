package com.umain.draugr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umain.draugr.host.hostWarning
import com.umain.draugr.platform.platformSnapshotsOnBackground
import com.umain.draugr.storage.Settings
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.BorderColor
import com.umain.draugr.ui.theme.DangerText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText

@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onSelfTest: () -> Unit,
    onCredits: () -> Unit,
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
            val snapshotOnBackground =
                settings.autoSnapshotOnBackground ?: platformSnapshotsOnBackground()
            Toggle(
                label = "SNAPSHOT ON BACKGROUND",
                detail = "SAVES GUEST RAM WHEN LEAVING; HEAVY, CAN KILL A LONG BOOT",
                enabled = snapshotOnBackground,
            ) {
                onSettingsChange(
                    settings.copy(autoSnapshotOnBackground = !snapshotOnBackground),
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

        BracketPanel(header = "ABOUT", modifier = Modifier.fillMaxWidth()) {
            Text(
                text = ">> CREDITS",
                style = MaterialTheme.typography.titleMedium,
                color = AccentText,
                modifier = Modifier.clickable { onCredits() },
            )
            Text(
                text = "THE EMULATORS, GUESTS AND LIBRARIES THIS IS BUILT ON, AND THEIR LICENCES.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }

        NetworkPanel(
            relayUrl = settings.networkRelayUrl,
            onRelayUrlChange = { onSettingsChange(settings.copy(networkRelayUrl = it)) },
        )
    }
}

@Composable
private fun NetworkPanel(
    relayUrl: String,
    onRelayUrlChange: (String) -> Unit,
) {
    var draft by remember(relayUrl) { mutableStateOf(relayUrl) }
    val enabled = relayUrl.isNotBlank()

    BracketPanel(
        header = if (enabled) "NETWORK [RELAY ON]" else "NETWORK [OFFLINE]",
        state = if (enabled) PanelState.ERROR else PanelState.IDLE,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (enabled) {
                "GUEST TRAFFIC LEAVES THIS DEVICE THROUGH THE RELAY BELOW. THE APP IS NO LONGER " +
                    "OFFLINE-ONLY WHILE THIS IS SET."
            } else {
                "THE APP MAKES NO OUTBOUND REQUESTS. GUESTS HAVE NO NETWORK. THE ONLY PEER IS " +
                    "THE EMBEDDED SERVER ON 127.0.0.1."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) DangerText else MutedText,
        )
        Text(
            text = "RELAY WEBSOCKET (ws:// OR wss://), BLANK FOR NONE:",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.padding(top = 10.dp),
        )
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AccentText),
            cursorBrush = SolidColor(AccentText),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onRelayUrlChange(draft) }),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "ws://192.168.0.10:4555",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MutedText,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = ">> APPLY",
                style = MaterialTheme.typography.titleMedium,
                color = AccentText,
                modifier = Modifier.clickable { onRelayUrlChange(draft) },
            )
            if (enabled) {
                Text(
                    text = ">> GO OFFLINE",
                    style = MaterialTheme.typography.titleMedium,
                    color = DangerText,
                    modifier = Modifier.clickable {
                        draft = ""
                        onRelayUrlChange("")
                    },
                )
            }
        }
        Text(
            text = "APPLIES TO THE NEXT BOOT. RUN tools/network-relay.mjs ON YOUR MACHINE.",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.padding(top = 8.dp),
        )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The text takes the leftover width and the state keeps its own. Without the weight the
        // description claimed the whole row and squeezed `[ ON ]` down to one character a line.
        Column(modifier = Modifier.weight(1f)) {
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
            maxLines = 1,
            softWrap = false,
        )
    }
}
