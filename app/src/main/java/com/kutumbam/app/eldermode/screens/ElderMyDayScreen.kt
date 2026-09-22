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
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Dedicated "My Day" Screen for Elder Mode.
 * Provides spoken and visual holistic day review: medicines,
 * schedule timeline, and flagged health alerts.
 */
@Composable
fun ElderMyDayScreen(
    state: ElderState,
    viewModel: ElderViewModel,
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
        // Top Header
        ElderHeader(
            memberName = state.memberName,
            relation = state.relation,
            language = state.language,
            onLanguageChange = { viewModel.setLanguage(it) },
            onBackClick = { viewModel.navigate(ElderDestination.HOME) },
            backButtonLabel = ElderStrings.backToHome(lang),
            showGreeting = false,
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Screen Title
            Text(
                text = ElderStrings.myDay(lang),
                fontFamily = K.Display,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = K.Ink,
            )

            // Giant "Read My Day" Speaker Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(K.TealTint)
                    .border(2.dp, K.Teal, RoundedCornerShape(24.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = { viewModel.speakMyDay() },
                    )
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = if (state.isSpeaking) "Stop speaking" else ElderStrings.readMyDay(lang)
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (state.isSpeaking) K.WarnIcon else K.Teal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isSpeaking) KIcons.Close else KIcons.Volume,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = Color.White,
                        )
                    }
                    Column {
                        Text(
                            text = if (state.isSpeaking) ElderStrings.stopSpeaking(lang) else ElderStrings.readMyDay(lang),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Teal,
                        )
                        Text(
                            text = if (state.isSpeaking) "Tap to pause" else "Tap to hear day summary aloud",
                            fontSize = 15.sp,
                            color = K.Muted,
                        )
                    }
                }
            }

            // Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(K.Card)
                    .border(2.dp, K.Border, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SUMMARY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = K.Teal,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "• Total medicines: ${state.totalDoses}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = K.Ink,
                    )
                    Text(
                        text = "• Completed: ${state.takenDosesCount} taken",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.takenDosesCount > 0) K.Green else K.Ink,
                    )
                    Text(
                        text = "• Remaining: ${state.pendingDosesCount} to take",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.pendingDosesCount > 0) K.WarnText else K.Muted,
                    )
                }
            }

            // Flagged Reading Warning (if any)
            state.flaggedItems.firstOrNull()?.let { flagged ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(K.WarnBg)
                        .border(2.dp, K.WarnBorder, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "RECENT LAB ALERT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.WarnIcon,
                        )
                        Text(
                            text = "${flagged.testName}: ${flagged.valueText}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.WarnText,
                        )
                        Text(
                            text = ElderStrings.doctorDiscussionAdvice(lang),
                            fontSize = 15.sp,
                            color = K.WarnText,
                        )
                    }
                }
            }

            // Today's Doses Schedule
            Text(
                text = "Today's Schedule",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = K.Ink,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.doses.isEmpty()) {
                Text(
                    text = ElderStrings.emptyMedicines(lang),
                    fontSize = 18.sp,
                    color = K.Muted,
                )
            } else {
                state.doses.forEach { dose ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(K.Card)
                            .border(1.5.dp, if (dose.isTaken) K.Green else K.Border, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${dose.timeFormatted} · ${dose.name}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = K.Ink,
                            )
                            if (dose.instruction.isNotBlank()) {
                                Text(
                                    text = dose.instruction,
                                    fontSize = 15.sp,
                                    color = K.Muted,
                                )
                            }
                        }

                        // Audio button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(K.Bg)
                                .clickable { viewModel.speakDose(dose) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(KIcons.Volume, "Hear dose", Modifier.size(24.dp), tint = K.Teal)
                        }
                    }
                }
            }
        }
    }
}
