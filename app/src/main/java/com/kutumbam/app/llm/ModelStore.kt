package com.kutumbam.app.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Models are multi-GB, so they never ship in the APK. They are either pushed with adb into the app's
 * external files dir, or imported through the file picker into private storage.
 */
class ModelStore(private val context: Context) {

    private val importedDir get() = File(context.filesDir, "models").apply { mkdirs() }
    private val pushedDir get() = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun list(): List<File> =
        (importedDir.listFiles().orEmpty().toList() + pushedDir.listFiles().orEmpty().toList())
            .filter { it.isFile && it.name.endsWith(".litertlm") }
            .sortedBy { it.name }

    /** Copies a picked file into private storage; returns the copy. */
    fun import(uri: Uri, onProgress: (Long) -> Unit = {}): File {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "model.litertlm"
        val target = File(importedDir, name)
        var copied = 0L
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onProgress(copied)
                }
            }
        }
        return target
    }

    fun pushDirHint(): String = pushedDir.absolutePath
}
