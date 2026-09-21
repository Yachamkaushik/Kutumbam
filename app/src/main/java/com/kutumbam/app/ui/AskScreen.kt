package com.kutumbam.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.locker.SourceKind
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/** Questions answered only from this person's stored records; every answer shows where it came from. */
@Composable
fun AskScreen(vm: AppViewModel) {
    val ask by vm.ask.collectAsState()
    val speaking by vm.speaking.collectAsState()
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.say("Microphone permission is needed to ask by voice. You can also type.")
    }

    LaunchedEffect(ask.items.size, ask.items.lastOrNull()?.answer?.length) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.back() }, contentAlignment = Alignment.Center) {
                Icon(KIcons.Back, "Back", Modifier.size(22.dp), tint = K.Ink)
            }
            Column {
                Text("Ask the Locker", fontFamily = K.Display, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                Text("Answers come only from ${ask.memberName}'s stored records", fontSize = 12.sp, color = K.Muted)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (ask.items.isEmpty()) {
                Text(
                    "Ask about ${ask.memberName}'s medicines, lab reports or vaccinations, by typing or with the mic. " +
                        "I can't give medical advice. For that, please ask the doctor.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = K.Muted,
                )
                ask.suggestions.forEach { tip ->
                    Text(
                        tip, fontSize = 13.sp, color = K.Teal, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(K.TealTint).clickable { vm.ask(tip) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            ask.items.forEach { item ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        item.question, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).background(K.Teal).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Column(Modifier.widthIn(max = 320.dp)) {
                    Column(Modifier.clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(K.Card).border(1.dp, K.Border, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            if (item.thinking && item.answer.isEmpty()) "Looking through ${ask.memberName}'s records…" else item.answer,
                            fontSize = 13.sp, lineHeight = 20.sp, color = if (item.thinking && item.answer.isEmpty()) K.Muted else K.Ink,
                        )
                        if (!item.thinking && item.answer.isNotEmpty()) {
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.speakAnswer(item) }, contentAlignment = Alignment.Center) {
                                    Icon(if (speaking) KIcons.Close else KIcons.Volume, if (speaking) "Stop" else "Read aloud", Modifier.size(16.dp), tint = K.Teal)
                                }
                                Text(if (speaking) "Stop" else "Read aloud", fontSize = 11.sp, color = K.Teal, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!item.thinking) {
                        item.sources.forEach { s ->
                            val open = s.kind == SourceKind.LAB_REPORT && s.documentId != null
                            Text(
                                "Source: ${s.label}${if (open) "  →" else ""}", fontSize = 11.sp, color = if (open) K.Teal else K.Muted,
                                fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp).then(if (open) Modifier.clickable { vm.openReport(s.documentId!!) } else Modifier),
                            )
                        }
                        if (item.basis.isNotEmpty()) Text(item.basis, fontSize = 10.sp, color = K.Muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            }
            ask.status?.let { Text(it, fontSize = 12.sp, color = K.Muted) }
        }

        Row(
            Modifier.fillMaxWidth().background(K.Card).border(1.dp, K.Border).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (ask.listening) K.Teal else K.Card).border(1.dp, if (ask.listening) K.Teal else K.Border, CircleShape)
                    .clickable { if (ask.listening) vm.stopListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                contentAlignment = Alignment.Center,
            ) { Icon(KIcons.Mic, if (ask.listening) "Stop listening" else "Ask by voice", Modifier.size(18.dp), tint = if (ask.listening) Color.White else K.Teal) }

            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(K.Bg).padding(horizontal = 16.dp, vertical = 11.dp)) {
                if (ask.listening) Text(ask.partial.ifEmpty { "Listening…" }, fontSize = 13.sp, color = K.Teal)
                else {
                    if (input.isEmpty()) Text("Ask about medicines or reports…", fontSize = 13.sp, color = K.Muted)
                    BasicTextField(
                        input, { input = it }, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = K.Ink), cursorBrush = SolidColor(K.Teal),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { vm.ask(input); input = "" }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (input.isNotBlank()) K.Teal else K.Ring).clickable(enabled = input.isNotBlank()) { vm.ask(input); input = "" },
                contentAlignment = Alignment.Center,
            ) { Icon(KIcons.Send, "Send", Modifier.size(17.dp), tint = Color.White) }
        }
    }
}
