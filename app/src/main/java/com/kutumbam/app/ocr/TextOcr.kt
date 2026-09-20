package com.kutumbam.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(val text: String, val millis: Long)

/** ML Kit Text Recognition v2, bundled model: fully offline. Output is plain text; structure comes from the parser. */
class TextOcr(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): OcrResult {
        val started = System.currentTimeMillis()
        val image = InputImage.fromFilePath(context, uri)
        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val lines = visionText.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, OcrBox(b.left, b.top, b.right, b.bottom))
        }
        val text = if (lines.isEmpty()) visionText.text else RowReconstructor.rows(lines).joinToString("\n")
        return OcrResult(text, System.currentTimeMillis() - started)
    }
}
