package com.kutumbam.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.formatNumber
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import com.kutumbam.app.vitals.Limits
import com.kutumbam.app.vitals.Reading
import com.kutumbam.app.vitals.SUGAR_CONTEXTS
import com.kutumbam.app.vitals.VitalKind
import com.kutumbam.app.vitals.VitalRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private enum class AddDialog { BP, SUGAR, WEIGHT, LIMITS }

/** Home readings: blood pressure, sugar and weight, each with its latest value, a small trend and a short history. */
@Composable
fun ReadingsTab(vm: AppViewModel, isChild: Boolean) {
    val readings by vm.readings.collectAsState()
    val limits by vm.limits.collectAsState()
    var dialog by remember { mutableStateOf<AddDialog?>(null) }
    val kinds = VitalKind.entries.filter { !(isChild && it == VitalKind.WEIGHT) }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { k ->
            val target = when (k) { VitalKind.BP -> AddDialog.BP; VitalKind.SUGAR -> AddDialog.SUGAR; VitalKind.WEIGHT -> AddDialog.WEIGHT }
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(K.Teal).clickable { dialog = target }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KIcons.Plus, null, Modifier.size(15.dp), tint = Color.White)
                Text(" ${k.label}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
    kinds.forEach { k -> ReadingCard(k, readings.filter { it.kind == k }, limits, vm::deleteReading) }
    if (VitalKind.BP in kinds) {
        TextButton(onClick = { dialog = AddDialog.LIMITS }) { Text(if (limits.isEmpty()) "Add limits from your doctor" else "Edit limits from your doctor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
    Text(
        "Readings are stored only on this phone. The app compares them only with a limit you enter yourself, from your doctor. It never decides what is normal.",
        fontSize = 11.sp, lineHeight = 16.sp, color = K.Muted,
    )

    when (dialog) {
        AddDialog.BP -> BpDialog({ dialog = null }) { s, d -> dialog = null; vm.addBloodPressure(s, d) }
        AddDialog.SUGAR -> SugarDialog({ dialog = null }) { v, c -> dialog = null; vm.addSugar(v, c) }
        AddDialog.WEIGHT -> WeightDialog({ dialog = null }) { v -> dialog = null; vm.addWeight(v) }
        AddDialog.LIMITS -> LimitsDialog(limits, { dialog = null }) { a, b, c, d -> dialog = null; vm.setLimits(a, b, c, d) }
        null -> {}
    }
}

@Composable
private fun ReadingCard(kind: VitalKind, all: List<Reading>, limits: Limits, onDelete: (Long) -> Unit) {
    var showHistory by remember { mutableStateOf(false) }
    val sorted = all.sortedWith(compareBy({ it.date }, { it.time }, { it.id }))
    val latest = sorted.lastOrNull()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(kind.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted)
            if (latest == null) {
                Text("No readings yet.", fontSize = 13.sp, color = K.Muted)
                return@Column
            }
            Text(latest.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = K.Ink)
            val today = LocalDate.now()
            Text("${if (latest.date == today) "Today" else latest.date.format(DAY)}, ${latest.time.format(CLOCK)}", fontSize = 12.sp, color = K.Muted)
            val limit = VitalRules.limitText(kind, latest.context, limits)
            when (VitalRules.status(latest, limits)) {
                VitalRules.Status.ABOVE -> Pill("Above the limit you set ($limit)", K.WarnBg, K.WarnIcon)
                VitalRules.Status.WITHIN -> Pill("Within the limit you set ($limit)", Color(0xFFE9F7EF), K.Green)
                VitalRules.Status.NO_LIMIT -> {}
            }
            val series = sorted.takeLast(14).map { it.value }
            if (series.size >= 2) Sparkline(series, Modifier.padding(top = 6.dp))
            TextButton(onClick = { showHistory = !showHistory }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(if (showHistory) "Hide history" else "History (${sorted.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (showHistory) sorted.takeLast(8).asReversed().forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.date.format(DAY)}, ${r.time.format(CLOCK)}", Modifier.weight(1f), fontSize = 12.sp, color = K.Muted)
                    Text(r.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { onDelete(r.id) }, contentAlignment = Alignment.Center) {
                        Icon(KIcons.Close, "Delete", Modifier.size(14.dp), tint = K.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val pad = 6.dp.toPx()
        fun point(i: Int) = Offset(pad + (size.width - 2 * pad) * i / (values.size - 1), pad + (size.height - 2 * pad) * (1f - ((values[i] - lo) / span).toFloat()))
        val path = Path().apply { values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
        drawPath(path, K.Teal, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(K.Teal, 4.dp.toPx(), point(values.lastIndex))
    }
}

// ---- dialogs

private val NUMBER = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private fun clean(v: String) = v.filter { it.isDigit() || it == '.' }.take(6)

@Composable
private fun BpDialog(onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    val error = VitalRules.validateBp(sys.toDoubleOrNull(), dia.toDoubleOrNull())
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Blood pressure") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sys, { sys = clean(it) }, label = { Text("Top number (systolic)") }, singleLine = true, keyboardOptions = NUMBER)
                OutlinedTextField(dia, { dia = clean(it) }, label = { Text("Bottom number (diastolic)") }, singleLine = true, keyboardOptions = NUMBER)
                if (sys.isNotEmpty() && dia.isNotEmpty() && error != null) Text(error, fontSize = 12.sp, color = K.WarnText)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(sys.toDouble(), dia.toDouble()) }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SugarDialog(onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var context by remember { mutableStateOf(SUGAR_CONTEXTS[0]) }
    val error = VitalRules.validateSugar(value.toDoubleOrNull())
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Blood sugar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = clean(it) }, label = { Text("Reading (mg/dL)") }, singleLine = true, keyboardOptions = NUMBER)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SUGAR_CONTEXTS.forEach { c -> FilterChip(selected = context == c, onClick = { context = c }, label = { Text(c) }) }
                }
                if (value.isNotEmpty() && error != null) Text(error, fontSize = 12.sp, color = K.WarnText)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.toDouble(), context) }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WeightDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf("") }
    val error = VitalRules.validateWeight(value.toDoubleOrNull())
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = clean(it) }, label = { Text("Weight (kg)") }, singleLine = true, keyboardOptions = NUMBER)
                if (value.isNotEmpty() && error != null) Text(error, fontSize = 12.sp, color = K.WarnText)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.toDouble()) }, enabled = error == null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LimitsDialog(limits: Limits, onDismiss: () -> Unit, onSave: (Double?, Double?, Double?, Double?) -> Unit) {
    fun text(key: String) = limits[key]?.let { formatNumber(it) }.orEmpty()
    var sys by remember { mutableStateOf(text(VitalRules.BP_SYSTOLIC)) }
    var dia by remember { mutableStateOf(text(VitalRules.BP_DIASTOLIC)) }
    var fasting by remember { mutableStateOf(text(VitalRules.SUGAR_FASTING)) }
    var after by remember { mutableStateOf(text(VitalRules.SUGAR_AFTER)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Limits from your doctor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter only numbers your doctor gave you. Leave a box empty for no limit.", fontSize = 13.sp, color = K.Muted)
                OutlinedTextField(sys, { sys = clean(it) }, label = { Text("Blood pressure: top number below") }, singleLine = true, keyboardOptions = NUMBER)
                OutlinedTextField(dia, { dia = clean(it) }, label = { Text("Blood pressure: bottom number below") }, singleLine = true, keyboardOptions = NUMBER)
                OutlinedTextField(fasting, { fasting = clean(it) }, label = { Text("Fasting sugar below (mg/dL)") }, singleLine = true, keyboardOptions = NUMBER)
                OutlinedTextField(after, { after = clean(it) }, label = { Text("Sugar after a meal below (mg/dL)") }, singleLine = true, keyboardOptions = NUMBER)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(sys.toDoubleOrNull(), dia.toDoubleOrNull(), fasting.toDoubleOrNull(), after.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
