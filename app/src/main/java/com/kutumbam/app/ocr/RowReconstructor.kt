package com.kutumbam.app.ocr

data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY get() = (top + bottom) / 2
    val height get() = (bottom - top).coerceAtLeast(1)
}

data class OcrLine(val text: String, val box: OcrBox)

/**
 * ML Kit returns text in reading blocks, which splits a table row ("Hemoglobin | 11.2 | g/dL | 13-17") into
 * separate lines. This re-joins lines that sit on the same visual row so the lab parser sees the row whole.
 */
object RowReconstructor {

    fun rows(lines: List<OcrLine>): List<String> {
        val sorted = lines.sortedBy { it.box.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val sameRow = row != null && row.let { r ->
                val cy = r.map { it.box.centerY }.average()
                val h = r.minOf { it.box.height }
                kotlin.math.abs(line.box.centerY - cy) < h * 0.6
            }
            if (sameRow) row!!.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { r -> r.sortedBy { it.box.left }.joinToString("  ") { it.text.trim() } }
    }
}
