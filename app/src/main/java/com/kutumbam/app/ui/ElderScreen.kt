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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/** Large-button mode for a parent who doesn't operate the phone: one big speaker, today's medicines, nothing else. */
@Composable
fun ElderScreen(vm: AppViewModel) {
    val ui by vm.home.collectAsState()
    val speaking by vm.speaking.collectAsState()
    val member = ui.selected ?: return
    val lang = AppLanguage.fromCode(member.preferredLanguage)

    Column(Modifier.fillMaxSize().background(K.Bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.padding(start = 12.dp, top = 12.dp).size(56.dp).clip(CircleShape).clickable { vm.stopSpeaking(); vm.show(Screen.HOME) },
            contentAlignment = Alignment.Center,
        ) { Icon(KIcons.Back, "Back", Modifier.size(28.dp), tint = K.Ink) }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLanguage.entries.forEach { l ->
                val on = l == lang
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(if (on) K.Teal else K.Card)
                        .border(1.dp, if (on) K.Teal else K.Border, RoundedCornerShape(24.dp))
                        .clickable { vm.setLanguage(l) }.padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(l.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else K.Ink) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(K.Teal).clickable { vm.speakToday() }, contentAlignment = Alignment.Center) {
                Icon(if (speaking) KIcons.Close else KIcons.Volume, if (speaking) "Stop" else "Read today's medicines", Modifier.size(48.dp), tint = Color.White)
            }
            Text(
                if (speaking) "Tap to stop" else "Tap to hear ${member.name}'s medicines",
                fontSize = 16.sp, color = K.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (ui.doses.isEmpty()) Text("No medicines scheduled today.", fontSize = 20.sp, color = K.Muted)
            ui.doses.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(K.Card).border(1.dp, K.Border, RoundedCornerShape(18.dp)).padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("${d.timeText} · ${d.name}", fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                        if (d.instruction.isNotEmpty()) Text(d.instruction, fontSize = 16.sp, color = K.Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(64.dp).clip(CircleShape).clickable { vm.toggleDose(d) }, contentAlignment = Alignment.Center) {
                        if (d.taken) Box(Modifier.size(48.dp).clip(CircleShape).background(K.Green), contentAlignment = Alignment.Center) {
                            Icon(KIcons.Check, "Taken", Modifier.size(26.dp), tint = Color.White)
                        } else Box(Modifier.size(48.dp).border(3.dp, K.Ring, CircleShape))
                    }
                }
            }
        }
    }
}
