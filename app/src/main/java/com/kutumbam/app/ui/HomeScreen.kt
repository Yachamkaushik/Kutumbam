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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kutumbam.app.parse.VaccineStatus
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SIDE = 20.dp

/** Today: who it is for, what to take now, and at most one small card of things that need a look. */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val ui by vm.home.collectAsState()
    val child by vm.child.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    val capture = rememberCapture(vm::processImage)
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        vm.rearmReminders()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val member = ui.selected

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = SIDE, end = 12.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Kutumbam", fontFamily = K.Display, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = K.Teal)
                Text("Your family's health, all in one place", fontSize = 13.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = { vm.show(Screen.DEV) }) { Text("AI setup", fontSize = 12.sp, color = K.Muted) }
        }
        MemberPills(ui, vm) { showAdd = true }

        if (member == null) {
            Welcome { showAdd = true }
        } else {
            val taken = ui.doses.count { it.taken }
            Column(Modifier.padding(horizontal = SIDE).padding(top = 20.dp)) {
                Text("Today", fontFamily = K.Display, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                Text(
                    "${member.name} · ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))}",
                    fontSize = 13.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp),
                )
                if (ui.doses.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    val fraction = taken.toFloat() / ui.doses.size
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(K.TealTint)) {
                        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(K.Teal))
                    }
                    Text("$taken of ${ui.doses.size} doses taken", fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Row(Modifier.padding(horizontal = SIDE).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showScan = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White),
                ) {
                    Icon(KIcons.Camera, null, Modifier.size(18.dp))
                    Text("  Scan document", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { vm.show(Screen.ELDER) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, K.Border), colors = ButtonDefaults.outlinedButtonColors(contentColor = K.Ink),
                ) {
                    Icon(KIcons.Volume, null, Modifier.size(18.dp), tint = K.Teal)
                    Text("  Elder mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
            AttentionCard(ui, child, vm)

            Column(Modifier.padding(horizontal = SIDE).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.doses.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card { Text("Nothing scheduled today. Scan a prescription to add ${member.name}'s medicines.", fontSize = 13.sp, color = K.Muted, lineHeight = 20.sp, modifier = Modifier.padding(16.dp)) }
                } else {
                    doseGroups(ui.doses).forEach { (title, rows) -> DoseGroup(title, rows) { vm.toggleDose(it) } }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showScan) ScanDialog(member?.name, capture) { showScan = false }
    if (showAdd) AddMemberDialog(onDismiss = { showAdd = false }, onAdd = { n, r, d, s -> showAdd = false; vm.addMember(n, r, d, s) })
}

// ---- shared pieces (also used by the Health tab)

@Composable
internal fun ScanDialog(name: String?, capture: CaptureActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan for $name") },
        text = { Text("A clear, flat, well-lit photo of a printed prescription or lab report works best.") },
        confirmButton = { TextButton(onClick = { onDismiss(); capture.takePhoto() }) { Text("Take photo") } },
        dismissButton = { TextButton(onClick = { onDismiss(); capture.pickImage() }) { Text("Choose from gallery") } },
    )
}

@Composable
internal fun MemberPills(ui: HomeUi, vm: AppViewModel, onAdd: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SIDE), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.members.forEach { m -> Pill(m.name, selected = m.id == ui.selected?.id) { vm.select(m.id) } }
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, K.Teal, RoundedCornerShape(20.dp)).clickable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KIcons.Plus, null, Modifier.size(16.dp), tint = K.Teal)
                Text(if (ui.members.isEmpty()) " Add family member" else " Add", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Teal)
            }
        }
    }
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
internal fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = K.Ink, textAlign = TextAlign.Center)
            Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = K.Muted, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White)) { Text(action) }
            }
        }
    }
}

@Composable
private fun Welcome(onAdd: () -> Unit) {
    Column(Modifier.padding(horizontal = SIDE).padding(top = 28.dp)) {
        EmptyState(
            "Welcome to Kutumbam",
            "Add a family member to begin. Each person gets their own medicines, reports and reminders, all stored only on this phone.",
            "Add a family member", onAdd,
        )
    }
}

// ---- Today: attention card

private class AttentionItem(val icon: ImageVector, val text: String, val warm: Boolean, val onClick: () -> Unit)

@Composable
private fun AttentionCard(ui: HomeUi, child: ChildUi?, vm: AppViewModel) {
    val items = buildList {
        ui.alert?.let { a -> add(AttentionItem(KIcons.Alert, a.short.ifEmpty { "A lab value is outside its range" }, true) { vm.openReport(a.documentId) }) }
        val low = ui.supply.count { it.urgent }
        if (low > 0) add(AttentionItem(KIcons.Alert, if (low == 1) "1 medicine is running low" else "$low medicines are running low", true) { vm.openHealth(0) })
        if (ui.duplicates.isNotEmpty()) add(AttentionItem(KIcons.Alert, "Possible duplicate medicines", true) { vm.openHealth(0) })
        child?.let { c ->
            val next = c.next
            when {
                c.overdueDoses > 0 -> add(AttentionItem(KIcons.Alert, "${c.overdueDoses} vaccine ${if (c.overdueDoses == 1) "dose is" else "doses are"} overdue", true) { vm.openChild() })
                next != null && next.status == VaccineStatus.DUE -> add(AttentionItem(KIcons.Heart, "Vaccine due now: ${next.pendingTitle}", false) { vm.openChild() })
                next != null -> add(AttentionItem(KIcons.Heart, "Next vaccine: ${next.pendingTitle} (${com.kutumbam.app.parse.ImmunizationEngine.relativeText(next, c.today)})", false) { vm.openChild() })
            }
        }
    }
    val warm = items.any { it.warm }
    Column(
        Modifier.padding(horizontal = SIDE).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (warm) K.WarnBg else K.Card).border(1.dp, if (warm) K.WarnBorder else K.Border, RoundedCornerShape(14.dp)),
    ) {
        if (items.isEmpty()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(KIcons.CheckCircle, null, Modifier.size(18.dp), tint = K.Green)
                Text("All clear. Nothing needs attention.", fontSize = 13.sp, color = K.Ink)
            }
        } else {
            Text(
                "NEEDS A LOOK", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = if (warm) K.WarnText else K.Muted,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 2.dp),
            )
            items.forEach { it ->
                Row(Modifier.fillMaxWidth().clickable(onClick = it.onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(it.icon, null, Modifier.size(17.dp), tint = if (it.warm) K.WarnIcon else K.Teal)
                    Text(it.text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, color = if (it.warm) K.WarnText else K.Ink)
                    Icon(KIcons.Chevron, null, Modifier.size(16.dp), tint = if (it.warm) K.WarnText else K.Muted)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---- Today: schedule

private fun doseGroups(doses: List<DoseRow>): List<Pair<String, List<DoseRow>>> {
    fun period(t: LocalTime) = when (t.hour) { in 0..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..20 -> "Evening"; else -> "Night" }
    return doses.sortedBy { it.time }.groupBy { period(it.time) }.entries.map { it.key to it.value }
}

@Composable
private fun DoseGroup(title: String, rows: List<DoseRow>, onToggle: (DoseRow) -> Unit) {
    Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    Card {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(K.Border))
                DoseLine(row) { onToggle(row) }
            }
        }
    }
}

@Composable
private fun DoseLine(row: DoseRow, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(66.dp).clip(RoundedCornerShape(8.dp)).background(K.TealTint).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(row.timeText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = K.Teal, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (row.taken) K.Muted else K.Ink)
            if (row.instruction.isNotEmpty()) Text(row.instruction, fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 1.dp))
        }
        Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (row.taken) Icon(KIcons.CheckCircle, "Taken", Modifier.size(22.dp), tint = K.Green)
            else Box(Modifier.size(20.dp).border(2.dp, K.Ring, CircleShape))
        }
    }
}

// ---- Add member

private val RELATIONS = listOf("Mother", "Father", "Spouse", "Child", "Grandparent", "Other")

@Composable
internal fun AddMemberDialog(onDismiss: () -> Unit, onAdd: (String, String, String?, Boolean) -> Unit) {
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
                OutlinedTextField(dob, { dob = it }, label = { Text(if (needsDob) "Date of birth (YYYY-MM-DD), needed for vaccines" else "Date of birth (YYYY-MM-DD)") }, singleLine = true, isError = !dobOk, keyboardOptions = KeyboardOptions.Default)
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
