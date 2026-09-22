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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SIDE = 20.dp

/** Everything stored for the selected person, in three short sections instead of one long page. */
@Composable
fun HealthScreen(vm: AppViewModel) {
    val ui by vm.home.collectAsState()
    val child by vm.child.collectAsState()
    val selected by vm.healthTab.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var addSelf by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var supplyEdit by remember { mutableStateOf<SupplyRow?>(null) }
    val capture = rememberCapture(vm::processImage)
    val member = ui.selected

    val sections = listOfNotNull("Medicines", "Readings", "Reports", if (child != null) "Vaccines" else null)
    val tab = selected.coerceIn(0, sections.lastIndex)

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = SIDE, end = 12.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Health", Modifier.weight(1f), fontFamily = K.Display, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = K.Ink)
            if (member != null) TextButton(onClick = { vm.openMedicalId() }) {
                Icon(KIcons.Heart, null, Modifier.size(16.dp), tint = K.Teal)
                Text("  Medical ID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
            }
        }
        MemberPills(ui, vm, onAddSelf = { addSelf = true }) { showAdd = true }
        if (member != null) {
            SegmentedTabs(sections, tab) { vm.setHealthTab(it) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SIDE), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (sections[tab]) {
                    "Medicines" -> MedicinesTab(ui, member.name, onScan = { showScan = true }, onEdit = { supplyEdit = it })
                    "Readings" -> ReadingsTab(vm, isChild = child != null)
                    "Reports" -> ReportsTab(ui, vm, onScan = { showScan = true })
                    else -> child?.let { VaccinesTab(it, vm) }
                }
                Box(Modifier.padding(bottom = 16.dp))
            }
        } else {
            Column(Modifier.padding(SIDE)) { EmptyState("No one added yet", "Add a family member to see their medicines, reports and vaccines here.", "Add a family member", { showAdd = true }, KIcons.Heart) }
        }
    }

    if (showScan) ScanDialog(member?.name, capture) { showScan = false }
    supplyEdit?.let { row -> SupplyDialog(row, onDismiss = { supplyEdit = null }, onSave = { supplyEdit = null; vm.setSupply(row.medicineId, it) }) }
    if (showAdd) AddMemberDialog(onDismiss = { showAdd = false }, onAdd = { n, r, d, s -> showAdd = false; vm.addMember(n, r, d, s) })
    if (addSelf) AddMemberDialog(forSelf = true, onDismiss = { addSelf = false }, onAdd = { n, _, d, _ -> addSelf = false; vm.addMember(n, "Self", d, true, isSelf = true) })
}

@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = SIDE, vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.TealTint).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) K.Card else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) K.Teal else K.Muted) }
        }
    }
}

// ---- medicines

@Composable
private fun MedicinesTab(ui: HomeUi, person: String, onScan: () -> Unit, onEdit: (SupplyRow) -> Unit) {
    if (ui.duplicates.isNotEmpty()) {
        val shown = ui.duplicates.take(3).map { it.text }
        val more = ui.duplicates.size - shown.size
        WarningCard("Possible duplicate medicines", if (more > 0) shown + "+ $more more overlaps" else shown)
    }
    if (ui.supply.isEmpty()) {
        EmptyState("No medicines yet", "Scan a prescription and $person's medicines will appear here with their schedule and supply.", "Scan a prescription", onScan, KIcons.Clipboard)
        return
    }
    ui.supply.forEach { row -> MedicineCard(row) { if (!row.asNeeded) onEdit(row) } }
    Text(
        "Supply is estimated from the schedule and each dose's tablets, assuming every dose is taken. Tap a medicine after a refill, or to recount, to restart the estimate.",
        fontSize = 11.sp, lineHeight = 16.sp, color = K.Muted, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun MedicineCard(row: SupplyRow, onClick: () -> Unit) {
    val urgent = row.urgent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (urgent) K.WarnBg else K.Card)
            .border(1.dp, if (urgent) K.WarnBorder else K.Border, RoundedCornerShape(14.dp)).clickable(enabled = !row.asNeeded, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (urgent) K.WarnText else K.Ink)
            if (row.schedule.isNotEmpty()) Text(row.schedule, fontSize = 12.sp, color = K.Muted)
            Text(row.text, fontSize = 12.sp, lineHeight = 18.sp, color = if (urgent) K.WarnText else K.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (urgent) Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
    }
}

@Composable
private fun SupplyDialog(row: SupplyRow, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var count by remember { mutableStateOf(row.count?.toString().orEmpty()) }
    val value = count.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Count the tablets you have today, including a new strip, and enter the total.", fontSize = 13.sp, color = K.Muted)
                if (row.suggested != null) {
                    TextButton(onClick = { count = row.suggested.toString() }) { Text("Use ${row.suggested}: ${row.suggestedNote}", fontSize = 12.sp) }
                }
                OutlinedTextField(
                    count, { count = it.filter(Char::isDigit).take(3) }, label = { Text("Tablets you have now") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null && value > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- reports

@Composable
private fun ReportsTab(ui: HomeUi, vm: AppViewModel, onScan: () -> Unit) {
    ui.alert?.let { a -> WarningCard("Latest report", listOf(a.text), Modifier.clickable { vm.openReport(a.documentId) }) }
    if (ui.reports.isEmpty()) {
        EmptyState("No lab reports yet", "Scan a lab report and its values will be checked against the range printed on it.", "Scan a report", onScan, KIcons.FileText)
        return
    }
    ui.reports.forEach { r ->
        Card(Modifier.clickable { vm.openReport(r.documentId) }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalDate.parse(r.date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                    Text("${r.total} values", fontSize = 12.sp, color = K.Muted)
                }
                Text(
                    if (r.flagged > 0) "${r.flagged} outside range" else "All in range",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (r.flagged > 0) K.WarnIcon else K.Green,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (r.flagged > 0) K.WarnBg else Color(0xFFE9F7EF)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Icon(KIcons.Chevron, null, Modifier.padding(start = 6.dp).size(16.dp), tint = K.Muted)
            }
        }
    }
}

// ---- vaccines

@Composable
private fun VaccinesTab(c: ChildUi, vm: AppViewModel) {
    val overdue = c.overdueDoses
    val next = c.next
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (overdue > 0) K.WarnBg else K.TealTint)
            .border(1.dp, if (overdue > 0) K.WarnBorder else K.Border, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${c.member.name} · ${c.ageText}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (overdue > 0) K.WarnText else K.Teal)
        Text(
            when {
                overdue > 0 -> "$overdue ${if (overdue == 1) "dose is" else "doses are"} overdue. Please talk to ${c.member.name}'s doctor."
                next != null && next.status == VaccineStatus.DUE -> "Due now: ${next.pendingTitle}."
                next != null -> "Next: ${next.pendingTitle} (${ImmunizationEngine.relativeText(next, c.today)})."
                else -> "Every dose on the schedule is recorded."
            },
            fontSize = 14.sp, lineHeight = 20.sp, color = if (overdue > 0) K.WarnText else K.Ink,
        )
    }
    Button(
        onClick = { vm.openChild() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White),
    ) { Text("Open immunization schedule and growth", modifier = Modifier.padding(vertical = 4.dp)) }
}
