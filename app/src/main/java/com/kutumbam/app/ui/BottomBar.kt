package com.kutumbam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/** The four places people move between. Everything else opens on top of these. */
enum class MainTab(val screen: Screen, val label: String, val icon: ImageVector) {
    TODAY(Screen.HOME, "Today", KIcons.Home),
    HEALTH(Screen.HEALTH, "Health", KIcons.Heart),
    ASK(Screen.ASK, "Ask", KIcons.Chat),
    VISIT(Screen.VISIT, "Visit", KIcons.Clipboard),
}

val TAB_SCREENS = MainTab.entries.map { it.screen }.toSet()

@Composable
fun KBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(K.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(K.Border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MainTab.entries.forEach { tab ->
                val selected = tab.screen == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(tab.screen) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) K.TealTint else K.Card),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.icon, tab.label, Modifier.size(20.dp), tint = if (selected) K.Teal else K.Muted) }
                    Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) K.Teal else K.Muted)
                }
            }
        }
    }
}
