package com.kutumbam.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.export.MedicalIdInfo
import com.kutumbam.app.parse.ImmunizationEngine
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons
import java.time.LocalDate

private val BLOOD_GROUPS = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

/** One card a stranger or a doctor could read in a few seconds. Only what the person typed in, plus the medicines already saved. */
@Composable
fun MedicalIdScreen(vm: AppViewModel) {
    val ui by vm.home.collectAsState()
    val member = ui.selected ?: return
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    val info = MedicalIdInfo(member.bloodGroup, member.allergies, member.conditions, member.emergencyName, member.emergencyPhone)
    val age = member.dateOfBirth?.let { runCatching { ImmunizationEngine.ageText(LocalDate.parse(it), LocalDate.now()) }.getOrNull() }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.back() }, contentAlignment = Alignment.Center) { Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink) }
            Text("Medical ID", Modifier.weight(1f), fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
            TextButton(onClick = { editing = true }) { Text(if (info.isEmpty) "Add details" else "Edit", fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text(if (member.isSelf) "${member.name} (you)" else member.name, fontFamily = K.Display, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                        age?.let { Text(it.removeSuffix(" old"), fontSize = 13.sp, color = K.Muted) }
                    }
                    Field("Blood group", info.bloodGroup, big = true)
                    Field("Allergies", info.allergies)
                    Field("Conditions", info.conditions)
                    Field("Emergency contact", listOfNotNull(info.emergencyName?.takeIf { it.isNotBlank() }, info.emergencyPhone?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null })
                    if (!info.emergencyPhone.isNullOrBlank()) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${info.emergencyPhone.filter { it.isDigit() || it == '+' }}"))) },
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White), modifier = Modifier.fillMaxWidth(),
                        ) { Text("Call ${info.emergencyName?.takeIf { it.isNotBlank() } ?: "emergency contact"}", modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MEDICINES NOW", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted)
                    if (ui.supply.isEmpty()) Text("No medicines saved.", fontSize = 13.sp, color = K.Muted)
                    ui.supply.forEach { r ->
                        Column {
                            Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                            if (r.schedule.isNotEmpty()) Text(r.schedule, fontSize = 12.sp, color = K.Muted)
                        }
                    }
                }
            }
            Text(
                "Stored only on this phone and not shown on the lock screen. Anyone who can unlock the phone can see it. These details also appear on the Summary PDF.",
                fontSize = 12.sp, lineHeight = 17.sp, color = K.Muted,
            )
        }
    }
    if (editing) EditDialog(info, onDismiss = { editing = false }, onSave = { editing = false; vm.saveMedicalId(it) })
}

@Composable
private fun Field(label: String, value: String?, big: Boolean = false) {
    Column {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted)
        if (value.isNullOrBlank()) Text("Not added", fontSize = 14.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp))
        else Text(value, fontSize = if (big) 24.sp else 15.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium, color = K.Ink, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EditDialog(info: MedicalIdInfo, onDismiss: () -> Unit, onSave: (MedicalIdInfo) -> Unit) {
    var blood by remember { mutableStateOf(info.bloodGroup) }
    var allergies by remember { mutableStateOf(info.allergies.orEmpty()) }
    var conditions by remember { mutableStateOf(info.conditions.orEmpty()) }
    var name by remember { mutableStateOf(info.emergencyName.orEmpty()) }
    var phone by remember { mutableStateOf(info.emergencyPhone.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Medical ID") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Blood group", fontSize = 12.sp, color = K.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BLOOD_GROUPS.forEach { g -> FilterChip(selected = blood == g, onClick = { blood = if (blood == g) null else g }, label = { Text(g) }) }
                }
                OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies (medicines, food)") })
                OutlinedTextField(conditions, { conditions = it }, label = { Text("Conditions (e.g. Type 2 diabetes)") })
                OutlinedTextField(name, { name = it }, label = { Text("Emergency contact name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, label = { Text("Emergency contact phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(MedicalIdInfo(blood, allergies, conditions, name, phone)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
