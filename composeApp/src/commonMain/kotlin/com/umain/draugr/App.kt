package com.umain.draugr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umain.draugr.ui.components.GlitchText
import com.umain.draugr.ui.components.scanlineOverlay
import com.umain.draugr.ui.theme.DraugrTheme
import com.umain.draugr.ui.theme.MutedText

@Composable
fun DraugrApp() {
    DraugrTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineOverlay()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlitchText(
                text = "DRAUGR",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = ":: V0.1",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}
