package com.kutumbam.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The mockups' stroke icons (24x24, 2px round strokes), transcribed as path data. */
private fun stroke(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach {
        b.addPath(
            pathData = PathParser().parsePathString(it).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object KIcons {
    val Camera = stroke("camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M8 13a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
    val Mic = stroke("mic", "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v4", "M8 23h8")
    val Volume = stroke("volume", "M11 5L6 9H2v6h4l5 4V5z", "M19.07 4.93a10 10 0 0 1 0 14.14", "M15.54 8.46a5 5 0 0 1 0 7.07")
    val Alert = stroke("alert", "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z", "M12 9v4", "M12 17h.01")
    val Pencil = stroke("pencil", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z")
    val Back = stroke("back", "M19 12H5", "M12 19l-7-7 7-7")
    val CheckCircle = stroke("checkCircle", "M22 11.08V12a10 10 0 1 1-5.93-9.14", "M22 4L12 14.01l-3-3")
    val Check = stroke("check", "M20 6L9 17l-5-5")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Heart = stroke("heart", "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z")
    val Send = stroke("send", "M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z")
    val Home = stroke("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10")
    val Clipboard = stroke("clipboard", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M9 2h6v4H9z", "M9 12h6", "M9 16h4")
    val Chat = stroke("chat", "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z")
    val Download = stroke("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3")
    val Chevron = stroke("chevron", "M9 18l6-6-6-6")
    val Close = stroke("close", "M18 6L6 18", "M6 6l12 12")
}
