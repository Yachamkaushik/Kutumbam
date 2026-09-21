package com.kutumbam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.RangeStatus
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/** Each value next to the range printed on the same report. Plain wording, never a diagnosis. */
@Composable
fun ReportScreen(vm: AppViewModel) {
    val report by vm.report.collectAsState()
    val r = report ?: return

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Column {
                Text("${r.memberName}'s Lab Report", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                Text(r.date, fontSize = 12.sp, color = K.Muted)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            r.rows.forEach { row ->
                Card(Modifier.clickable { vm.openTrend(r.memberId, row.key) }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(row.testName, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                            StatusBadge(row.status)
                        }
                        Text(row.valueText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = K.Ink, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            when {
                                row.rangeLabel == null -> "No range printed on this report"
                                row.usedFallback -> "Standard reference (this report printed none): ${row.rangeLabel}"
                                else -> "Range on report: ${row.rangeLabel}"
                            },
                            fontSize = 12.sp, color = K.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                        Text("See trend →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Teal, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        val flagged = r.rows.firstOrNull { it.status == RangeStatus.ABOVE || it.status == RangeStatus.BELOW }
        val n = r.flaggedCount
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp).navigationBarsPadding().fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (n > 0) K.WarnBg else K.TealTint).border(1.dp, if (n > 0) K.WarnBorder else K.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (n > 0) Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
                Text(
                    if (n > 0) "${if (n == 1) "One value is" else "$n values are"} outside the range shown on this report. This isn't a diagnosis. Please show it to ${r.memberName}'s doctor."
                    else "Every value that has a range is within it. This isn't a diagnosis; the doctor's advice always comes first.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = if (n > 0) K.WarnText else K.Ink,
                )
            }
            if (r.usedFallback) Text(
                "Some ranges above are a small built-in standard reference, used because the report printed none. Your doctor's range may differ.",
                fontSize = 12.sp, lineHeight = 17.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp),
            )
            flagged?.let {
                Text("View trend →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = K.Teal, modifier = Modifier.padding(top = 10.dp).clickable { vm.openTrend(r.memberId, it.key) })
            }
        }
    }
}

@Composable
private fun StatusBadge(status: RangeStatus) {
    val (label, fg, bg) = when (status) {
        RangeStatus.IN_RANGE -> Triple("Normal", K.Green, Color(0xFFE9F7EF))
        RangeStatus.ABOVE, RangeStatus.BELOW -> Triple("Out of range", K.WarnIcon, K.WarnBg)
        RangeStatus.NO_RANGE -> Triple("No range", K.Muted, K.Divider)
    }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
