package com.kutumbam.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.MilestoneState
import com.kutumbam.app.parse.VaccineState
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Immunization timeline. Every status is computed by rules from the date of birth and the doses recorded. */
@Composable
fun ChildScreen(vm: AppViewModel) {
    val childState by vm.child.collectAsState()
    val c = childState ?: return
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun pickDate(scheduleId: String) {
        val t = LocalDate.now()
        DatePickerDialog(context, { _, y, m, d -> vm.markVaccine(scheduleId, LocalDate.of(y, m + 1, d)) }, t.year, t.monthValue - 1, t.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Text("${c.member.name} · Immunization", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
        Text(
            "Date of birth: ${c.dob.format(DAY)} · ${c.ageText}", fontSize = 12.sp, color = K.Muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GrowthCard(vm)
            c.milestones.forEach { ms ->
                val key = ms.milestone.name
                MilestoneCard(ms, c.today, expanded = key in expanded, onToggle = { expanded = if (key in expanded) expanded - key else expanded + key })
                if (key in expanded) {
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ms.vaccines.forEach { v -> DoseRow(v, onMark = { pickDate(v.vaccine.id) }, onUndo = { vm.unmarkVaccine(v.vaccine.id) }) }
                    }
                }
            }
            Text(
                "Follows India's Universal Immunization Programme. Your child's doctor may advise a different schedule, so always check with them or the card you were given.",
                fontSize = 12.sp, lineHeight = 17.sp, color = K.Muted, modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        val overdue = c.overdueDoses
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp).navigationBarsPadding().fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (overdue > 0) K.WarnBg else K.TealTint).padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (overdue > 0) Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
                Text(
                    when {
                        overdue > 0 -> "${if (overdue == 1) "1 dose is" else "$overdue doses are"} overdue. Reminders are set for upcoming doses. Please talk to ${c.member.name}'s doctor."
                        c.next != null && c.next!!.status == VaccineStatus.DUE -> "Due now: ${c.next!!.pendingTitle}. Nothing is overdue. Reminders are set."
                        c.next != null -> "Nothing is due or overdue. Next: ${c.next!!.pendingTitle} (${ImmunizationEngine.relativeText(c.next!!, c.today)})."
                        else -> "Every dose on the schedule is recorded."
                    },
                    fontSize = 13.sp, lineHeight = 19.sp, color = if (overdue > 0) K.WarnText else K.Ink,
                )
            }
            Text(
                "Ask the Locker about this →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = K.Teal,
                modifier = Modifier.padding(top = 10.dp).clickable { vm.openAsk("Which vaccines are due for ${c.member.name}?") },
            )
        }
    }
}

@Composable
private fun MilestoneCard(ms: MilestoneState, today: LocalDate, expanded: Boolean, onToggle: () -> Unit) {
    val (bg, fg, label) = statusStyle(ms.status)
    val rel = ImmunizationEngine.relativeText(ms, today)
    Card(Modifier.clickable(onClick = onToggle)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(KIcons.Heart, null, Modifier.size(18.dp), tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(ms.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                Text(listOf(ms.milestone.ageLabel, rel).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 1.dp))
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun DoseRow(v: VaccineState, onMark: () -> Unit, onUndo: () -> Unit) {
    val (_, fg, label) = statusStyle(v.status)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFDF9)).clickable(enabled = v.status != VaccineStatus.DONE, onClick = onMark).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(v.vaccine.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = K.Ink)
            Text(
                if (v.givenOn != null) "Given ${v.givenOn.format(DAY)}" else "$label · tap to mark as given",
                fontSize = 11.sp, color = if (v.givenOn != null) K.Green else fg,
            )
        }
        if (v.givenOn != null) Text("Undo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Muted, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndo).padding(8.dp))
    }
}

private fun statusStyle(s: VaccineStatus): Triple<Color, Color, String> = when (s) {
    VaccineStatus.DONE -> Triple(Color(0xFFE9F7EF), K.Green, "Done")
    VaccineStatus.UPCOMING -> Triple(K.TealTint, K.Teal, "Upcoming")
    VaccineStatus.DUE -> Triple(Color(0xFFDDEFEC), K.Teal, "Due now")
    VaccineStatus.OVERDUE -> Triple(K.WarnBg, K.WarnIcon, "Overdue")
}

@Composable
private fun GrowthCard(vm: AppViewModel) {
    val growth by vm.growth.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Growth", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                androidx.compose.material3.TextButton(onClick = { adding = true }) { Text("Add measurement") }
            }
            if (growth.isEmpty()) Text("No weight or height recorded yet. Add them to get growth questions for the doctor's visit.", fontSize = 12.sp, lineHeight = 18.sp, color = K.Muted)
            growth.takeLast(4).reversed().forEach { g ->
                val parts = listOfNotNull(
                    g.weightKg?.let { "${com.kutumbam.app.parse.formatNumber(it)} kg" }, g.heightCm?.let { "${com.kutumbam.app.parse.formatNumber(it)} cm tall" },
                    g.headCm?.let { "head ${com.kutumbam.app.parse.formatNumber(it)} cm" },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.parse(g.date).format(DAY) + " · " + parts.joinToString(", "), Modifier.weight(1f), fontSize = 13.sp, color = K.Ink)
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { vm.deleteMeasurement(g.id) }, contentAlignment = Alignment.Center) {
                        Icon(KIcons.Close, "Delete", Modifier.size(16.dp), tint = K.Muted)
                    }
                }
            }
            androidx.compose.material3.OutlinedButton(onClick = { vm.openVisit() }, modifier = Modifier.fillMaxWidth()) { Text("Pediatrician visit prep") }
        }
    }
    if (adding) MeasurementDialog(onDismiss = { adding = false }, onSave = { d, w, h, hc -> adding = false; vm.addMeasurement(d, w, h, hc) })
}

@Composable
private fun MeasurementDialog(onDismiss: () -> Unit, onSave: (LocalDate, Double?, Double?, Double?) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    val decimal = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
    fun clean(v: String) = v.filter { it.isDigit() || it == '.' }.take(6)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }) { Text(date.format(DAY)) }
                androidx.compose.material3.OutlinedTextField(weight, { weight = clean(it) }, label = { Text("Weight (kg)") }, singleLine = true, keyboardOptions = decimal)
                androidx.compose.material3.OutlinedTextField(height, { height = clean(it) }, label = { Text("Height or length (cm)") }, singleLine = true, keyboardOptions = decimal)
                androidx.compose.material3.OutlinedTextField(head, { head = clean(it) }, label = { Text("Head size (cm), optional") }, singleLine = true, keyboardOptions = decimal)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(date, weight.toDoubleOrNull(), height.toDoubleOrNull(), head.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
