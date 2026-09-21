package com.kutumbam.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.ui.AppViewModel
import com.kutumbam.app.ui.AskScreen
import com.kutumbam.app.ui.ChildScreen
import com.kutumbam.app.ui.VisitScreen
import com.kutumbam.app.ui.ConfirmScreen
import com.kutumbam.app.ui.DevScreen
import com.kutumbam.app.ui.ElderScreen
import com.kutumbam.app.ui.ReportScreen
import com.kutumbam.app.ui.TrendScreen
import com.kutumbam.app.ui.HealthScreen
import com.kutumbam.app.ui.HomeScreen
import com.kutumbam.app.ui.KBottomBar
import com.kutumbam.app.ui.MedicalIdScreen
import com.kutumbam.app.ui.TAB_SCREENS
import com.kutumbam.app.ui.Screen
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KutumbamTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app background is light, so the system bars need dark icons.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            KutumbamTheme {
                val screen by vm.screen.collectAsState()
                val busy by vm.busy.collectAsState()
                val message by vm.message.collectAsState()
                val members by vm.home.collectAsState()
                val pendingShare by vm.pendingShare.collectAsState()

                BackHandler(enabled = screen != Screen.HOME) { vm.back() }

                pendingShare?.let {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { vm.cancelShare() },
                        title = { Text("Whose document is this?") },
                        text = { Text("It will be filed under the person you choose.") },
                        confirmButton = {
                            Column {
                                members.members.forEach { m ->
                                    androidx.compose.material3.TextButton(onClick = { vm.chooseShareTarget(m.id) }) { Text(m.name) }
                                }
                            }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.cancelShare() }) { Text("Cancel") } },
                    )
                }

                Surface(Modifier.fillMaxSize(), color = K.Bg) {
                  Column(Modifier.fillMaxSize()) {
                    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    val showBar = screen in TAB_SCREENS && !keyboardOpen
                    Box(Modifier.weight(1f).fillMaxWidth().then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.HEALTH -> HealthScreen(vm)
                            Screen.CONFIRM -> ConfirmScreen(vm)
                            Screen.ELDER -> ElderScreen(vm)
                            Screen.REPORT -> ReportScreen(vm)
                            Screen.TREND -> TrendScreen(vm)
                            Screen.CHILD -> ChildScreen(vm)
                            Screen.ASK -> AskScreen(vm)
                            Screen.VISIT -> VisitScreen(vm)
                            Screen.MEDICAL_ID -> MedicalIdScreen(vm)
                            Screen.DEV -> DevScreen { vm.show(Screen.HOME) }
                        }
                        message?.let { text ->
                            LaunchedEffect(text) { delay(4000); vm.clearMessage() }
                            Box(
                                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp).clip(RoundedCornerShape(12.dp))
                                    .background(K.Ink).clickable { vm.clearMessage() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            ) { Text(text, color = Color.White, fontSize = 13.sp) }
                        }
                        busy?.let { label ->
                            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(K.Card).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = K.Teal)
                                    Text(label, Modifier.padding(top = 14.dp), color = K.Ink, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    if (showBar) KBottomBar(screen) { vm.selectTab(it) }
                  }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Photos shared into Kutumbam (e.g. from WhatsApp) go straight into the capture flow for the selected member. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        uri?.let(vm::onSharedImage)
    }
}
