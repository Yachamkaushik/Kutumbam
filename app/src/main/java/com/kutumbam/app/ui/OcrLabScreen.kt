package com.kutumbam.app.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

/** Developer screen for step 2: photo -> ML Kit OCR -> rule-based parser, with the raw text shown for debugging. */
@Composable
fun OcrLabScreen(vm: OcrLabViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::process) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(vm::process) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera(context, { cameraUri = it }, camera::launch)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Scan lab", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, enabled = !s.busy) { Text("Take photo") }
            OutlinedButton(
                onClick = { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !s.busy,
            ) { Text("Pick image") }
        }
        if (s.busy && s.doc == null) Text("Reading…")
        s.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        s.doc?.let { doc ->
            Text("OCR ${s.ocrMillis} ms · parse ${s.parseMillis} ms · detected: ${doc.type}" + (doc.date?.let { " · dated $it" } ?: ""))
            HorizontalDivider()
            if (doc.medicines.isNotEmpty()) Text("Medicines (${doc.medicines.size})", style = MaterialTheme.typography.titleMedium)
            doc.medicines.forEach { m ->
                Text(
                    "• ${m.name} ${m.strength.orEmpty()}  [${m.form ?: "?"}]\n" +
                        "   ${m.frequency?.code ?: "no frequency"}${m.frequency?.let { " (${it.raw}) at ${it.times.joinToString()}" } ?: ""}\n" +
                        "   ${m.meal}  ·  ${m.durationDays?.let { "$it days" } ?: "no duration"}",
                )
            }
            if (doc.labValues.isNotEmpty()) Text("Lab values (${doc.labValues.size})", style = MaterialTheme.typography.titleMedium)
            doc.labValues.forEach { v ->
                Text("• ${v.testName}: ${v.value} ${v.unit.orEmpty()}   printed range: ${v.rangeText ?: "none"}")
            }
            if (doc.medicines.isEmpty() && doc.labValues.isEmpty()) Text("Nothing structured found. Check the raw text below.")

            if (doc.medicines.isNotEmpty()) {
                Button(onClick = { vm.explain("English") }, enabled = !s.busy && vm.llmReady) {
                    Text(if (vm.llmReady) "Explain with on-device AI" else "Load a model in the AI tab first")
                }
                if (s.explanation.isNotEmpty()) Text(s.explanation)
                if (s.explainStats.isNotEmpty()) Text(s.explainStats, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            Text("Raw OCR text", style = MaterialTheme.typography.titleSmall)
            Text(s.rawText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun launchCamera(context: android.content.Context, remember: (Uri) -> Unit, launch: (Uri) -> Unit) {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    remember(uri)
    launch(uri)
}
