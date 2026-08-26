package com.umain.draugr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umain.draugr.credits.Credit
import com.umain.draugr.credits.Credits
import com.umain.draugr.ui.components.BracketPanel
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.PanelState
import com.umain.draugr.ui.theme.AccentText
import com.umain.draugr.ui.theme.MutedText
import com.umain.draugr.ui.theme.PrimaryText
import com.umain.draugr.ui.theme.SecondaryText

@Composable
fun CreditsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "<< BACK",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.clickable { onBack() },
        )
        GlitchText(
            text = "CREDITS",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            text = "DRAUGR IS A THIN SHELL AROUND OTHER PEOPLE'S WORK.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item(key = "author") {
                BracketPanel(
                    header = "BUILT BY",
                    state = PanelState.RUNNING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = Credits.author.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryText,
                    )
                    Text(
                        text = Credits.author.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                    Text(
                        text = Credits.author.handle,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText,
                    )
                }
            }

            Credits.sections.forEach { section ->
                item(key = section.title) {
                    BracketPanel(header = section.title, modifier = Modifier.fillMaxWidth()) {
                        section.entries.forEachIndexed { index, credit ->
                            if (index > 0) {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            CreditEntry(credit)
                        }
                    }
                }
            }

            item(key = "note") {
                BracketPanel(header = "NOTE", modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = Credits.NOTE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                }
            }

            item(key = "sign-off") {
                Text(
                    text = ":: THE HOWE IS OPEN ::",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CreditEntry(credit: Credit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = credit.name.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = credit.licence,
            style = MaterialTheme.typography.labelSmall,
            color = AccentText,
            textAlign = TextAlign.End,
        )
    }
    Text(
        text = credit.what.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = MutedText,
    )
    Text(
        text = credit.source,
        style = MaterialTheme.typography.labelSmall,
        color = SecondaryText,
    )
}
