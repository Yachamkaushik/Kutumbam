package com.kutumbam.app.eldermode.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.components.ElderBigButton
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.components.ElderNextMedicineHeroCard
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Primary landing hub for Elder Mode.
 * Features large accessible buttons, clear "My Day" summary,
 * Next Medicine hero card, and one-tap voice actions.
 */
@Composable
fun ElderHomeScreen(
    state: ElderState,
    viewModel: ElderViewModel,
    onExitElderMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = state.language

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(K.Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header with Exit and Language Switcher
        ElderHeader(
            memberName = state.memberName,
            relation = state.relation,
            language = state.language,
            onLanguageChange = { viewModel.setLanguage(it) },
            onBackClick = onExitElderMode,
            backButtonLabel = ElderStrings.exitElderMode(lang),
            showGreeting = true,
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // TODAY Summary Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(K.Card)
                    .border(2.dp, K.Border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = ElderStrings.today(lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = K.Teal,
                        letterSpacing = 0.5.sp,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "💊 ${ElderStrings.medicinesCount(lang, state.totalDoses)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = K.Ink,
                            )
                            if (state.nextDose != null) {
                                Text(
                                    text = "🔔 ${ElderStrings.nextMedAt(lang, state.nextDose.timeFormatted)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = K.Muted,
                                )
                            } else if (state.allTaken) {
                                Text(
                                    text = ElderStrings.allDosesTaken(lang),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = K.Green,
                                )
                            }
                        }

                        // Read My Day Speaker Action
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (state.isSpeaking) K.WarnIcon else K.Teal)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { viewModel.speakMyDay() },
                                )
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = if (state.isSpeaking) "Stop speaking" else ElderStrings.readMyDay(lang)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isSpeaking) KIcons.Close else KIcons.Volume,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    // Speaking state indicator banner
                    if (state.isSpeaking) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(K.TealTint)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "🔊 ${ElderStrings.voiceSpeakingTitle(lang)} (Tap speaker to stop)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = K.Teal,
                            )
                        }
                    }
                }
            }

            // NEXT MEDICINE Hero Card (if available)
            if (state.nextDose != null) {
                ElderNextMedicineHeroCard(
                    dose = state.nextDose,
                    countdown = state.nextDoseCountdown,
                    language = lang,
                    onHearClick = { viewModel.speakDose(state.nextDose) },
                    onTakenClick = { viewModel.markDoseTaken(state.nextDose) },
                )
            }

            // Primary Navigation Buttons
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. My Medicines
                ElderBigButton(
                    title = ElderStrings.myMedicines(lang),
                    icon = KIcons.Clipboard,
                    badgeText = "${state.totalDoses}",
                    backgroundColor = K.Card,
                    contentColor = K.Ink,
                    borderColor = K.Border,
                    onClick = { viewModel.navigate(ElderDestination.MEDICINES) },
                )

                // 2. Ask Kutumbam (Voice Q&A)
                ElderBigButton(
                    title = ElderStrings.askKutumbam(lang),
                    icon = KIcons.Mic,
                    backgroundColor = K.TealTint,
                    contentColor = K.Teal,
                    borderColor = K.Teal,
                    minHeight = 72.dp,
                    onClick = { viewModel.navigate(ElderDestination.ASK) },
                )

                // 3. My Health
                ElderBigButton(
                    title = ElderStrings.myHealth(lang),
                    icon = KIcons.Heart,
                    badgeText = if (state.flaggedItems.isNotEmpty()) "${state.flaggedItems.size} alert" else null,
                    backgroundColor = K.Card,
                    contentColor = K.Ink,
                    borderColor = if (state.flaggedItems.isNotEmpty()) K.WarnBorder else K.Border,
                    onClick = { viewModel.navigate(ElderDestination.HEALTH) },
                )

                // 4. My Day
                ElderBigButton(
                    title = ElderStrings.myDay(lang),
                    icon = KIcons.Home,
                    backgroundColor = K.Card,
                    contentColor = K.Ink,
                    borderColor = K.Border,
                    onClick = { viewModel.navigate(ElderDestination.MY_DAY) },
                )

                // 5. Emergency
                if (state.hasEmergencyInfo) {
                    ElderBigButton(
                        title = ElderStrings.emergency(lang),
                        icon = KIcons.Alert,
                        backgroundColor = K.WarnBg,
                        contentColor = K.WarnText,
                        borderColor = K.WarnBorder,
                        onClick = { viewModel.navigate(ElderDestination.EMERGENCY) },
                    )
                }
            }
        }
    }
}
