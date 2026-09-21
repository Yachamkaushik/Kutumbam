package com.kutumbam.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.kutumbam.app.reminder.ReminderScheduler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        val context = androidx.compose.ui.platform.LocalContext.current
        var testStatus by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                ReminderScheduler.scheduleTest(context)
                testStatus = "Test reminder in 5 seconds (exact alarms allowed: ${ReminderScheduler.canScheduleExact(context)}). Lock the phone or leave the app to see it."
            },
            modifier = Modifier.padding(16.dp),
        ) { Text("Send a test reminder in 5 s") }
        if (testStatus.isNotEmpty()) Text(testStatus, Modifier.padding(horizontal = 16.dp))
    }
}
