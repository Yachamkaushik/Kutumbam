package com.kutumbam.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kutumbam.app.llm.LlmBackend

/** Developer screen for step 1: prove the on-device model loads, on which accelerator, and how fast. */
@Composable
fun LlmLabScreen(vm: LlmLabViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("On-device LLM lab", style = MaterialTheme.typography.headlineSmall)
        Text("Models found (adb push to ${vm.pushHint}, or import):", style = MaterialTheme.typography.labelLarge)
        if (s.models.isEmpty()) Text("None yet.")
        s.models.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = f == s.selected, onClick = { vm.select(f) })
                Text("${f.name}  (${f.length() shr 20} MB)")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.refresh() }, enabled = !s.busy) { Text("Rescan") }
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !s.busy) { Text("Import…") }
        }

        Text("Backend order", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val auto = listOf(LlmBackend.NPU, LlmBackend.GPU, LlmBackend.CPU)
            FilterChip(s.order == auto, { vm.setOrder(auto) }, { Text("Auto (NPU→GPU→CPU)") })
            LlmBackend.entries.forEach { b ->
                FilterChip(s.order == listOf(b), { vm.setOrder(listOf(b)) }, { Text(b.name) })
            }
        }

        Button(onClick = { vm.load() }, enabled = !s.busy && s.selected != null) { Text("Load model") }
        Text(s.status)

        Button(onClick = { vm.runSample() }, enabled = !s.busy && s.loaded) { Text("Explain sample prescription") }
        Text(s.output)
        if (s.stats.isNotEmpty()) Text(s.stats, style = MaterialTheme.typography.labelMedium)
    }
}
