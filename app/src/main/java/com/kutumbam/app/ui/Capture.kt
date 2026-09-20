package com.kutumbam.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

class CaptureActions(val takePhoto: () -> Unit, val pickImage: () -> Unit)

/**
 * Camera via the system camera app (no CAMERA permission needed) and gallery via the photo picker.
 * Both hand back a Uri that the OCR pipeline can read.
 */
@Composable
fun rememberCapture(onImage: (Uri) -> Unit): CaptureActions {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending.value?.let(onImage)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onImage) }
    return CaptureActions(
        takePhoto = { camera.launch(newCaptureUri(context).also { pending.value = it }) },
        pickImage = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
