package com.kutumbam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.parse.TrendPoint
import com.kutumbam.app.parse.describeRange
import com.kutumbam.app.parse.formatNumber
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The line is drawn from stored numbers only. The sentence below is computed from the same numbers. */
@Composable
fun TrendScreen(vm: AppViewModel) {
    val trend by vm.trend.collectAsState()
    val ai by vm.aiSummary.collectAsState()
    val aiBusy by vm.aiBusy.collectAsState()
    val t = trend ?: return

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Text("${t.person} — ${t.testName}", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.Card).border(1.dp, K.Border, RoundedCornerShape(16.dp)).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
            TrendChart(t.points, t.low, t.high)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val range = describeRange(t.low, t.high, t.unit)
                if (range != null) {
                    Text("- - -", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = K.WarnIcon)
                    Text("  Range ${if (t.usedFallback) "(standard reference)" else "on report"}: $range", fontSize = 11.sp, color = K.Muted, modifier = Modifier.weight(1f))
                } else Box(Modifier.weight(1f))
                t.unit?.let { Text(it, fontSize = 11.sp, color = K.Muted) }
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5F1E7)).padding(16.dp)) {
            Text(ai ?: t.summary, fontSize = 13.sp, lineHeight = 21.sp, color = K.Ink)
            if (aiBusy) Text("Writing a plain-language summary on this phone…", fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp))
            else if (ai != null) Text("Written by the on-device AI from the numbers above.", fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        if (t.usedFallback) Text(
            "The range shown is a small built-in standard reference, because the report printed none.",
            fontSize = 12.sp, color = K.Muted, lineHeight = 17.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        Text(
            "This is a summary of the numbers on the reports, not a diagnosis. Please talk to a doctor about any concern.",
            fontSize = 12.sp, color = K.Muted, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>, low: Double?, high: Double?) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = K.Muted)
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
    val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    val distinctMonths = points.map { it.date.format(monthFmt) }.toSet().size == points.size
    val fmt = if (distinctMonths) monthFmt else dayFmt

    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val padL = 30.dp.toPx(); val padR = 30.dp.toPx(); val padT = 30.dp.toPx(); val padB = 32.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val all = points.map { it.value } + listOfNotNull(low, high)
        var lo = all.min(); var hi = all.max()
        if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.18
        lo -= pad; hi += pad
        fun y(v: Double) = padT + h * (1f - ((v - lo) / (hi - lo)).toFloat())

        val t0 = points.first().date.toEpochDay()
        val t1 = points.last().date.toEpochDay()
        fun x(p: TrendPoint) = if (t1 == t0) padL + w / 2 else padL + w * ((p.date.toEpochDay() - t0).toFloat() / (t1 - t0))

        // The range printed on the report: a soft band, with dashed edges and a label.
        if (low != null && high != null) drawRect(Color(0x1A0F6E63), Offset(padL, y(high)), Size(w, y(low) - y(high)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        high?.let { drawLine(K.WarnIcon, Offset(padL, y(it)), Offset(padL + w, y(it)), 1.5.dp.toPx(), pathEffect = dash) }
        low?.let { drawLine(K.WarnIcon, Offset(padL, y(it)), Offset(padL + w, y(it)), 1.5.dp.toPx(), pathEffect = dash) }
        // The measured values.
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p.value).toFloat()) else path.lineTo(x(p), y(p.value).toFloat()) }
        if (points.size > 1) drawPath(path, K.Teal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        points.forEachIndexed { i, p ->
            val cx = x(p); val cy = y(p.value).toFloat()
            val outside = RangeCheck.status(p.value, low, high).let { it == RangeStatus.ABOVE || it == RangeStatus.BELOW }
            drawCircle(if (outside) K.WarnIcon else K.Teal, 5.dp.toPx(), Offset(cx, cy))
            val v = measurer.measure(formatNumber(p.value), valueStyle.copy(color = if (outside) K.WarnIcon else K.Ink))
            drawText(v, topLeft = Offset((cx - v.size.width / 2f).coerceIn(0f, size.width - v.size.width), (cy - v.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
            val d = measurer.measure(p.date.format(fmt), label)
            drawText(d, topLeft = Offset((cx - d.size.width / 2f).coerceIn(0f, size.width - d.size.width), size.height - d.size.height - 2.dp.toPx()))
        }
    }
}
