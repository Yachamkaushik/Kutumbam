package com.kutumbam.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.kutumbam.app.ui.theme.K
import java.io.File

/** Draws [SummaryContent] as a single A4 page. If the content is long the type shrinks a little so it still fits one page. */
object SummaryPdf {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 40f
    private val SCALES = floatArrayOf(1.12f, 1.06f, 1f, 0.93f, 0.86f, 0.8f, 0.74f, 0.68f)

    fun write(content: SummaryContent, file: File) {
        val scale = SCALES.firstOrNull { layout(null, content, it) <= H - MARGIN } ?: SCALES.last()
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
            layout(page.canvas, content, scale)
            doc.finishPage(page)
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Draws when [canvas] is given, otherwise only measures. Returns the y position after the last line. */
    private fun layout(canvas: Canvas?, c: SummaryContent, scale: Float): Float {
        val width = (W - 2 * MARGIN).toInt()
        val ink = 0xFF1B1B1B.toInt(); val muted = 0xFF6B6B6B.toInt(); val teal = K.Teal.toArgb()
        fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * scale; this.color = color; typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        var y = MARGIN

        fun text(s: String, p: TextPaint, x: Float = MARGIN, w: Int = width, gap: Float = 0f) {
            val l = StaticLayout.Builder.obtain(s, 0, s.length, p, w).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.12f).build()
            if (canvas != null) { canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore() }
            y += l.height + gap * scale
        }

        text(c.title, paint(20f, ink, bold = true), gap = 2f)
        text(c.subtitle, paint(9.5f, muted), gap = 10f)
        canvas?.drawRect(MARGIN, y, W - MARGIN, y + 1.5f, Paint().apply { color = teal })
        y += 12f * scale

        c.sections.forEach { s ->
            text(s.heading.uppercase(), paint(10f, teal, bold = true), gap = 4f)
            s.lines.forEach { line ->
                val bullet = paint(9.5f, ink)
                if (canvas != null) canvas.drawText("•", MARGIN, y + bullet.textSize, bullet)
                text(line, bullet, x = MARGIN + 11f, w = width - 11, gap = 2.5f)
            }
            y += 8f * scale
        }
        y = maxOf(y, 0f)
        // Footer sits at the bottom of the page when there is room, otherwise straight after the content.
        val footerPaint = paint(8.5f, muted)
        val footerHeight = c.footer.sumOf { StaticLayout.Builder.obtain(it, 0, it.length, footerPaint, width).build().height.toDouble() }.toFloat() + 10f * scale * c.footer.size
        val footerTop = maxOf(y + 6f, H - MARGIN - footerHeight)
        if (canvas != null) canvas.drawRect(MARGIN, footerTop - 6f, W - MARGIN, footerTop - 5f, Paint().apply { color = 0xFFD8D2C6.toInt() })
        y = footerTop
        c.footer.forEach { text(it, footerPaint, gap = 6f) }
        return y
    }
}

object ExportFiles {
    fun createSummary(context: Context, content: SummaryContent, fileName: String): File {
        val file = File(File(context.cacheDir, "exports"), fileName)
        SummaryPdf.write(content, file)
        return file
    }

    /** Copies the PDF into the phone's Downloads folder, where iQOO Office Kit's file transfer can pick it up. Null below Android 10. */
    fun saveToDownloads(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share the health summary",
        )
    }
}
