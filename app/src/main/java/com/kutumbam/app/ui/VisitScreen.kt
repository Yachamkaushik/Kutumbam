package com.kutumbam.app.ui

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/** The question sheet for the next visit. Everything on it is a question or a stored fact; nothing is interpreted. */
@Composable
fun VisitScreen(vm: AppViewModel) {
    val sheet by vm.visit.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.pdfReady.collect { file -> context.startActivity(com.kutumbam.app.export.ExportFiles.shareIntent(context, file)) }
    }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Text(sheet?.title ?: "Visit prep", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
        val s = sheet
        if (s == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = K.Teal) }
            return@Column
        }
        Text(s.subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = K.Muted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotesCard(vm)
            var n = 0
            s.sections.forEach { section ->
                Text(
                    section.heading.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = K.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                section.note?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = K.Muted) }
                section.items.forEach { item ->
                    n++
                    Card {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(K.TealTint), contentAlignment = Alignment.Center) {
                                Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = K.Teal)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.text, fontSize = 14.sp, lineHeight = 21.sp, color = K.Ink)
                                item.source?.let { Text("From: $it", fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 4.dp)) }
                            }
                        }
                    }
                }
            }
            Text(s.footer, fontSize = 12.sp, lineHeight = 18.sp, color = K.Muted, modifier = Modifier.padding(vertical = 8.dp))
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.exportSummary() }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = K.Teal, contentColor = Color.White),
            ) {
                Icon(KIcons.Download, null, Modifier.size(18.dp))
                Text("  Summary PDF", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, s.title).putExtra(Intent.EXTRA_TEXT, s.asText())
                    context.startActivity(Intent.createChooser(send, "Share the question list"))
                },
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, K.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = K.Ink),
            ) {
                Icon(KIcons.Send, null, Modifier.size(18.dp), tint = K.Teal)
                Text("  Share list", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A short list of things noticed, in the person's own words, that then appears on the sheet. */
@Composable
private fun NotesCard(vm: AppViewModel) {
    val notes by vm.notes.collectAsState()
    val ui by vm.home.collectAsState()
    var adding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val self = ui.selected?.isSelf == true
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (self) "Things I've noticed" else "Things noticed", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                androidx.compose.material3.TextButton(onClick = { adding = true }) { Text("+ Add a note", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (notes.isEmpty()) Text("Jot down anything to mention to the doctor, like a new symptom or a question. It goes on the list below.", fontSize = 12.sp, lineHeight = 18.sp, color = K.Muted)
            notes.take(5).forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(java.time.LocalDate.parse(n.date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) + " · " + n.text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, color = K.Ink)
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { vm.deleteNote(n.id) }, contentAlignment = Alignment.Center) { Icon(KIcons.Close, "Delete", Modifier.size(14.dp), tint = K.Muted) }
                }
            }
        }
    }
    if (adding) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adding = false }, title = { Text("Add a note") },
            text = { androidx.compose.material3.OutlinedTextField(text, { text = it.take(200) }, label = { Text("What did you notice?") }, minLines = 2, maxLines = 4) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.addNote(text); adding = false }, enabled = text.isNotBlank()) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
