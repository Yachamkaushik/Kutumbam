package com.kutumbam.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.DocumentType
import com.kutumbam.app.parse.FrequencyCode
import com.kutumbam.app.parse.MealTiming
import com.kutumbam.app.parse.ParsedLabValue
import com.kutumbam.app.parse.TestNames
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nothing is stored until the user reviews every detected field here and taps Confirm & Save. */
@Composable
fun ConfirmScreen(vm: AppViewModel) {
    val draft by vm.draft.collectAsState()
    val d = draft ?: return

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { vm.discard() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Text("Confirm Details", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
        Text(
            "We read this from the photo for ${d.memberName}. Check it's right before saving.",
            fontSize = 13.sp, color = K.Muted, lineHeight = 19.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            d.meds.forEach { m -> MedCard(m, vm) }
            d.labs.forEachIndexed { i, lab -> LabCard(lab) { vm.removeLab(i) } }
            d.vaccines.forEach { v -> VaccineCard(v, vm) }
            if (d.meds.isEmpty() && d.labs.isEmpty() && d.vaccines.isEmpty()) Text("Everything was removed. Discard, or go back and rescan.", fontSize = 13.sp, color = K.Muted)
            if (d.type == DocumentType.LAB_REPORT || d.labs.isNotEmpty()) {
                Text(
                    "Values are compared only with the range printed on your report. This is not a diagnosis; talk to your doctor about anything outside it.",
                    fontSize = 12.sp, color = K.Muted, lineHeight = 17.sp,
                )
            }
        }

        Row(Modifier.padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, K.Teal, RoundedCornerShape(12.dp)).clickable { vm.discard() }.padding(14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Discard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Teal) }
            val canSave = (d.meds.isNotEmpty() || d.labs.isNotEmpty() || d.vaccines.isNotEmpty()) && d.vaccines.all { it.date != null }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (canSave) K.Teal else K.Ring).clickable(enabled = canSave) { vm.save() }.padding(14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Confirm & Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
    }
}

@Composable
private fun MedCard(m: EditableMed, vm: AppViewModel) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name.ifBlank { "Unnamed medicine" }, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.updateMed(m.key) { it.copy(editing = !it.editing) } }, contentAlignment = Alignment.Center) {
                    Icon(if (m.editing) KIcons.Check else KIcons.Pencil, if (m.editing) "Done editing" else "Edit", Modifier.size(18.dp), tint = if (m.editing) K.Teal else K.Muted)
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.removeMed(m.key) }, contentAlignment = Alignment.Center) {
                    Icon(KIcons.Close, "Remove", Modifier.size(18.dp), tint = K.Muted)
                }
            }
            Spacer10()
            if (m.editing) MedEditor(m, vm) else {
                Row2("Strength", m.strength.ifBlank { "Not found" })
                Row2("Frequency", frequencyText(m.frequency))
                Row2("Meal timing", mealLabel(m.meal))
                Row2("Duration", m.durationDays.toIntOrNull()?.let { "$it days" } ?: "Not stated")
                if (m.times.isNotEmpty()) Row2("Reminders", m.times.joinToString(", ") { it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) })
            }
        }
    }
}

@Composable
private fun MedEditor(m: EditableMed, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(m.name, { v -> vm.updateMed(m.key) { it.copy(name = v) } }, label = { Text("Medicine name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(m.strength, { v -> vm.updateMed(m.key) { it.copy(strength = v) } }, label = { Text("Strength (e.g. 500 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Frequency", fontSize = 12.sp, color = K.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(FrequencyCode.OD, FrequencyCode.BD, FrequencyCode.TDS, FrequencyCode.QID, FrequencyCode.HS, FrequencyCode.SOS).forEach { c ->
                FilterChip(selected = m.frequency == c, onClick = { vm.setFrequency(m.key, c) }, label = { Text(c.name) })
            }
        }
        Text("Meal timing", fontSize = 12.sp, color = K.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MealTiming.entries.forEach { t ->
                FilterChip(selected = m.meal == t, onClick = { vm.updateMed(m.key) { it.copy(meal = t) } }, label = { Text(mealLabel(t)) })
            }
        }
        if (m.times.isNotEmpty()) {
            Text("Reminder times (tap to change)", fontSize = 12.sp, color = K.Muted)
            val context = LocalContext.current
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.times.forEachIndexed { i, t ->
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(K.TealTint)
                            .clickable { TimePickerDialog(context, { _, h, min -> vm.setTime(m.key, i, LocalTime.of(h, min)) }, t.hour, t.minute, false).show() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Teal) }
                }
            }
        }
        OutlinedTextField(
            m.durationDays, { v -> vm.updateMed(m.key) { it.copy(durationDays = v.filter(Char::isDigit).take(3)) } },
            label = { Text("Duration (days)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun LabCard(v: ParsedLabValue, onRemove: () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.testName, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                    Icon(KIcons.Close, "Remove", Modifier.size(18.dp), tint = K.Muted)
                }
            }
            Spacer10()
            Row2("Result", listOfNotNull(if (v.value % 1.0 == 0.0) v.value.toLong().toString() else v.value.toString(), v.unit).joinToString(" "))
            Row2(
                "Printed range",
                v.rangeText ?: if (TestNames.fallback(v.testName, v.unit) != null) "Not printed; standard reference will be used" else "Not printed on report",
            )
        }
    }
}

@Composable
private fun VaccineCard(v: EditableVaccine, vm: AppViewModel) {
    val context = LocalContext.current
    fun pick() {
        val t = v.date ?: java.time.LocalDate.now()
        android.app.DatePickerDialog(context, { _, y, m, day -> vm.setVaccineDate(v.key, java.time.LocalDate.of(y, m + 1, day)) }, t.year, t.monthValue - 1, t.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }
    Card(Modifier.clickable { pick() }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.removeVaccine(v.key) }, contentAlignment = Alignment.Center) {
                    Icon(KIcons.Close, "Remove", Modifier.size(18.dp), tint = K.Muted)
                }
            }
            Spacer10()
            Row2("Given on", v.date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) ?: "Date not found. Tap to set")
            Row2("Scheduled for", v.milestone)
        }
    }
}

@Composable
private fun Spacer10() = Box(Modifier.padding(top = 10.dp))

@Composable
private fun Row2(label: String, value: String) {
    HorizontalDivider(color = K.Divider)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = K.Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Ink, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun frequencyText(c: FrequencyCode) = when (c) {
    FrequencyCode.OD -> "Once daily (OD)"
    FrequencyCode.BD -> "Twice daily (BD)"
    FrequencyCode.TDS -> "Three times daily (TDS)"
    FrequencyCode.QID -> "Four times daily (QID)"
    FrequencyCode.HS -> "At bedtime (HS)"
    FrequencyCode.SOS -> "Only when needed (SOS)"
    FrequencyCode.CUSTOM -> "Custom"
}

private fun mealLabel(t: MealTiming) = when (t) {
    MealTiming.BEFORE_FOOD -> "Before food"
    MealTiming.AFTER_FOOD -> "After food"
    MealTiming.WITH_FOOD -> "With food"
    MealTiming.EMPTY_STOMACH -> "Empty stomach"
    MealTiming.UNSPECIFIED -> "Not specified"
}
