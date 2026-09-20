package com.kutumbam.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Setup and diagnostics: load the on-device model and inspect raw OCR. Reached from "AI setup" on Home. */
@Composable
fun DevScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.statusBarsPadding()) {
        TextButton(onClick = onBack) { Text("← Back to Kutumbam") }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("AI model") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("OCR check") })
        }
        if (tab == 0) LlmLabScreen() else OcrLabScreen()
    }
}
