package com.kutumbam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalDate

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui by vm.home.collectAsState()
    val child by vm.child.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var supplyEdit by remember { mutableStateOf<SupplyRow?>(null) }
    val capture = rememberCapture(vm::processImage)
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        vm.rearmReminders()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Kutumbam", fontFamily = K.Display, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = K.Teal)
                Text("Your family's health, all in one place", fontSize = 13.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = { vm.show(Screen.DEV) }) { Text("AI setup", fontSize = 12.sp, color = K.Muted) }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ui.members.forEach { m -> Pill(m.name, selected = m.id == ui.selected?.id) { vm.select(m.id) } }
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, K.Teal, RoundedCornerShape(20.dp)).clickable { showAdd = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(KIcons.Plus, null, Modifier.size(16.dp), tint = K.Teal)
                    Text(if (ui.members.isEmpty()) " Add family member" else " Add", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
                }
            }
        }

        ui.alert?.let { AlertBanner(it.text) { vm.openReport(it.documentId) } }

        if (ui.duplicates.isNotEmpty()) {
            WarningCard("Possible duplicate medicines", ui.duplicates.map { it.text }, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
        }

        child?.let { c -> VaccinationSummary(c) { vm.openChild() } }

        val member = ui.selected
        if (member == null) {
            Card(Modifier.padding(horizontal = 20.dp)) {
                Text("Start by adding a family member. Each person gets their own medicines, reports and reminders, all stored only on this phone.", fontSize = 13.sp, color = K.Muted, lineHeight = 20.sp, modifier = Modifier.padding(16.dp))
            }
        } else {
            Text(
                "TODAY · ${member.name}".uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.doses.isEmpty()) {
                    Card { Text("Nothing scheduled today. Scan a prescription to add ${member.name}'s medicines.", fontSize = 13.sp, color = K.Muted, lineHeight = 20.sp, modifier = Modifier.padding(16.dp)) }
                }
                ui.doses.forEach { DoseCard(it) { vm.toggleDose(it) } }
            }
        }

        if (ui.supply.isNotEmpty()) {
            Text(
                "MEDICINE SUPPLY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.supply.forEach { SupplyCard(it) { supplyEdit = it } }
                Text("Estimated from the schedule and each dose's tablets, assuming every dose is taken. Tap a medicine after a refill, or to recount, to restart the estimate.", fontSize = 11.sp, color = K.Muted, lineHeight = 16.sp)
            }
        }

        if (member != null) {
            Card(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).clickable { vm.openVisit() }) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(KIcons.Pencil, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.Teal)
                    Column {
                        Text(if (child != null) "Pediatrician visit prep" else "Doctor visit prep", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                        Text(
                            "A short list of questions for ${member.name}'s next visit, from saved reports, medicines${if (child != null) ", growth and vaccines" else ""}.",
                            fontSize = 12.sp, lineHeight = 18.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        if (ui.reports.isNotEmpty()) {
            Text(
                "LAB REPORTS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.reports.take(3).forEach { r ->
                    Card(Modifier.clickable { vm.openReport(r.documentId) }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(java.time.LocalDate.parse(r.date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                                Text("${r.total} values", fontSize = 12.sp, color = K.Muted)
                            }
                            Text(
                                if (r.flagged > 0) "${r.flagged} outside range" else "All in range",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (r.flagged > 0) K.WarnIcon else K.Green,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (r.flagged > 0) K.WarnBg else Color(0xFFE9F7EF)).padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(KIcons.Camera, "Scan Document", Modifier.weight(1f)) {
                if (member == null) vm.say("Add a family member first.") else showScan = true
            }
            Tile(KIcons.Mic, "Ask a Question", Modifier.weight(1f)) { vm.openAsk() }
            Tile(KIcons.Volume, "Elder Mode", Modifier.weight(1f)) {
                if (member == null) vm.say("Add a family member first.") else vm.show(Screen.ELDER)
            }
        }
    }

    if (showScan) {
        AlertDialog(
            onDismissRequest = { showScan = false },
            title = { Text("Scan for ${ui.selected?.name}") },
            text = { Text("A clear, flat, well-lit photo of a printed prescription or lab report works best.") },
            confirmButton = { TextButton(onClick = { showScan = false; capture.takePhoto() }) { Text("Take photo") } },
            dismissButton = { TextButton(onClick = { showScan = false; capture.pickImage() }) { Text("Choose from gallery") } },
        )
    }
    supplyEdit?.let { row ->
        SupplyDialog(row, onDismiss = { supplyEdit = null }, onSave = { supplyEdit = null; vm.setSupply(row.medicineId, it) })
    }
    if (showAdd) AddMemberDialog(onDismiss = { showAdd = false }, onAdd = { n, r, d, s -> showAdd = false; vm.addMember(n, r, d, s) })
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) K.Teal else K.Card)
            .border(1.dp, if (selected) K.Teal else K.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else K.Ink) }
}

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.Card).border(BorderStroke(1.dp, K.Border), RoundedCornerShape(14.dp))) { content() }
}

/** A warm notice with a heading and one line per finding, for things to check with the doctor or pharmacist. */
@Composable
internal fun WarningCard(title: String, lines: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.WarnBg).border(1.dp, K.WarnBorder, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.WarnText)
            lines.forEach { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = K.WarnText) }
        }
    }
}

@Composable
private fun AlertBanner(text: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(K.WarnBg).border(1.dp, K.WarnBorder, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
        Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = K.WarnText)
    }
}

@Composable
private fun DoseCard(row: DoseRow, onToggle: () -> Unit) {
    Card {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(66.dp).clip(RoundedCornerShape(8.dp)).background(K.TealTint).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(row.timeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Teal, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                if (row.instruction.isNotEmpty()) Text(row.instruction, fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 1.dp))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                if (row.taken) Icon(KIcons.CheckCircle, "Taken", Modifier.size(22.dp), tint = K.Green)
                else Box(Modifier.size(20.dp).border(2.dp, K.Ring, CircleShape))
            }
        }
    }
}

@Composable
private fun SupplyCard(row: SupplyRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (row.urgent) K.WarnBg else K.Card)
            .border(1.dp, if (row.urgent) K.WarnBorder else K.Border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        if (row.urgent) Icon(KIcons.Alert, null, Modifier.padding(top = 2.dp).size(18.dp), tint = K.WarnIcon)
        Column(Modifier.weight(1f)) {
            Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (row.urgent) K.WarnText else K.Ink)
            Text(row.text, fontSize = 12.sp, lineHeight = 18.sp, color = if (row.urgent) K.WarnText else K.Muted, modifier = Modifier.padding(top = 2.dp))
        }
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null && value > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(K.Card).border(1.dp, K.Border, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = K.Teal)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VaccinationSummary(c: ChildUi, onClick: () -> Unit) {
    val overdue = c.overdueDoses
    val next = c.next
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (overdue > 0) K.WarnBg else K.TealTint).border(1.dp, if (overdue > 0) K.WarnBorder else K.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Icon(if (overdue > 0) KIcons.Alert else KIcons.Heart, null, Modifier.padding(top = 2.dp).size(18.dp), tint = if (overdue > 0) K.WarnIcon else K.Teal)
        Column {
            Text("Vaccinations · ${c.ageText}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (overdue > 0) K.WarnText else K.Teal)
            Text(
                when {
                    overdue > 0 -> "$overdue ${if (overdue == 1) "dose is" else "doses are"} overdue. Tap to see the schedule."
                    next != null && next.status == com.kutumbam.app.parse.VaccineStatus.DUE -> "Due now: ${next.pendingTitle}. Tap to see the schedule."
                    next != null -> "Next: ${next.pendingTitle} (${com.kutumbam.app.parse.ImmunizationEngine.relativeText(next, c.today)}). Tap to see the schedule."
                    else -> "Every dose on the schedule is recorded."
                },
                fontSize = 13.sp, lineHeight = 19.sp, color = if (overdue > 0) K.WarnText else K.Ink, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private val RELATIONS = listOf("Mother", "Father", "Spouse", "Child", "Grandparent", "Other")

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String, String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf(RELATIONS[0]) }
    var dob by remember { mutableStateOf("") }
    var selfOperates by remember { mutableStateOf(false) }
    val dobDate = runCatching { LocalDate.parse(dob) }.getOrNull()
    val needsDob = relation == "Child"
    val dobOk = if (needsDob) dobDate != null else (dob.isEmpty() || dobDate != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add family member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Amma)") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RELATIONS.forEach { r -> FilterChip(selected = relation == r, onClick = { relation = r }, label = { Text(r) }) }
                }
                OutlinedTextField(dob, { dob = it }, label = { Text(if (needsDob) "Date of birth (YYYY-MM-DD), needed for vaccines" else "Date of birth (YYYY-MM-DD)") }, singleLine = true, isError = !dobOk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uses the phone themselves", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(selfOperates, { selfOperates = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, relation, dobDate?.toString(), selfOperates) }, enabled = name.isNotBlank() && dobOk) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
