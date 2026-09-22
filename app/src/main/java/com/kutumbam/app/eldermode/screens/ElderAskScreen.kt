package com.kutumbam.app.eldermode.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.components.ElderMicButton
import com.kutumbam.app.eldermode.components.ElderPillButton
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.models.VoiceUiState
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Dedicated Voice Assistant Screen for Elder Mode ("Ask Kutumbam").
 * Strict adherence to 5 explicit visual states:
 * IDLE, LISTENING, PROCESSING, ANSWER, ERROR.
 * Purely on-device grounded answers from local records.
 */
@Composable
fun ElderAskScreen(
    state: ElderState,
    viewModel: ElderViewModel,
    modifier: Modifier = Modifier,
) {
    val lang = state.language
    val voiceState = state.voiceState

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startVoiceQuestion()
        } else {
            viewModel.showMessage(ElderStrings.micPermissionNeeded(lang))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(K.Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header
        ElderHeader(
            memberName = state.memberName,
            relation = state.relation,
            language = state.language,
            onLanguageChange = { viewModel.setLanguage(it) },
            onBackClick = {
                viewModel.stopListening()
                viewModel.stopSpeaking()
                viewModel.navigate(ElderDestination.HOME)
            },
            backButtonLabel = ElderStrings.backToHome(lang),
            showGreeting = false,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = ElderStrings.askKutumbam(lang),
                fontFamily = K.Display,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = K.Ink,
            )

            // Giant State-Aware Microphone Button
            ElderMicButton(
                isListening = voiceState == VoiceUiState.LISTENING,
                onClick = {
                    if (voiceState == VoiceUiState.LISTENING) {
                        viewModel.stopListening()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                buttonSize = 112.dp,
            )

            // 5 Visual States Feedback Box
            when (voiceState) {
                VoiceUiState.IDLE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = ElderStrings.voiceIdleTitle(lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Ink,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "You can ask: \"What is my next medicine?\" or \"What was my last sugar test?\"",
                            fontSize = 16.sp,
                            color = K.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                VoiceUiState.LISTENING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = ElderStrings.voiceListeningTitle(lang),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Teal,
                            textAlign = TextAlign.Center,
                        )
                        if (state.recognizedText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(K.Card)
                                    .border(1.5.dp, K.Teal, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "\"${state.recognizedText}\"",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = K.Ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                VoiceUiState.PROCESSING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(color = K.Teal, modifier = Modifier.size(48.dp))
                        Text(
                            text = ElderStrings.voiceProcessingTitle(lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Teal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                VoiceUiState.ANSWER -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(K.Card)
                                .border(2.dp, K.Teal, RoundedCornerShape(24.dp))
                                .padding(20.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(KIcons.Volume, null, Modifier.size(24.dp), tint = K.Teal)
                                    Text(
                                        text = if (state.isSpeaking) "Speaking answer..." else "Answer",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = K.Teal,
                                    )
                                }

                                Text(
                                    text = state.voiceAnswerText,
                                    fontSize = 22.sp,
                                    lineHeight = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = K.Ink,
                                )

                                if (state.voiceAnswerSource.isNotBlank()) {
                                    Text(
                                        text = "Source: ${state.voiceAnswerSource}",
                                        fontSize = 14.sp,
                                        color = K.Muted,
                                    )
                                }
                            }
                        }

                        // Answer Actions: Hear Again & Ask Another
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ElderPillButton(
                                text = ElderStrings.hearAgain(lang),
                                icon = KIcons.Volume,
                                onClick = { viewModel.repeatSpokenAnswer() },
                                modifier = Modifier.weight(1f),
                                backgroundColor = K.Bg,
                                contentColor = K.Ink,
                                borderColor = K.Border,
                            )

                            ElderPillButton(
                                text = ElderStrings.askAnother(lang),
                                icon = KIcons.Mic,
                                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.weight(1f),
                                backgroundColor = K.Teal,
                                contentColor = Color.White,
                                borderColor = K.Teal,
                            )
                        }
                    }
                }

                VoiceUiState.ERROR -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(K.WarnBg)
                                .border(2.dp, K.WarnBorder, RoundedCornerShape(20.dp))
                                .padding(18.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(KIcons.Alert, null, Modifier.size(20.dp), tint = K.WarnIcon)
                                    Text(
                                        text = ElderStrings.voiceErrorTitle(lang),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = K.WarnText,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                if (state.voiceErrorMessage.isNotBlank()) {
                                    Text(
                                        text = state.voiceErrorMessage,
                                        fontSize = 15.sp,
                                        color = K.WarnText,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        ElderPillButton(
                            text = "Try Again",
                            icon = KIcons.Mic,
                            onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            backgroundColor = K.Teal,
                            contentColor = Color.White,
                            borderColor = K.Teal,
                        )
                    }
                }
            }
        }
    }
}
