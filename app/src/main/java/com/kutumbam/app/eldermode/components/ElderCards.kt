package com.kutumbam.app.eldermode.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.models.DoseStatus
import com.kutumbam.app.eldermode.models.ElderDose
import com.kutumbam.app.eldermode.models.ElderHealthItem
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.speech.AppLanguage
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * High-prominence Hero card for the upcoming medicine dose.
 */
@Composable
fun ElderNextMedicineHeroCard(
    dose: ElderDose,
    countdown: String,
    language: AppLanguage,
    onHearClick: () -> Unit,
    onTakenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealText = ElderStrings.mealInstruction(language, dose.meal)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(K.Card)
            .border(1.5.dp, K.Teal, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Tag & Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(K.Teal),
                    )
                    Text(
                        text = ElderStrings.nextMedicine(language),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = K.Teal,
                        letterSpacing = 0.5.sp,
                    )
                }

                if (countdown.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(K.TealTint)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = countdown,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Teal,
                        )
                    }
                }
            }

            // Medicine Name & Strength
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = dose.name,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                )
                if (dose.strength.isNotBlank()) {
                    Text(
                        text = dose.strength,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = K.Muted,
                    )
                }
            }

            // Time and Meal Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = dose.timeFormatted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                )
                if (mealText.isNotBlank()) {
                    Text(
                        text = "· $mealText",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = K.Muted,
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hear Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.Bg)
                        .border(1.dp, K.Border, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onHearClick)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(KIcons.Volume, null, Modifier.size(24.dp), tint = K.Ink)
                        Text(
                            text = ElderStrings.hear(language),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = K.Ink,
                        )
                    }
                }

                // Taken Button
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.Green)
                        .clickable(role = Role.Button, onClick = onTakenClick)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(KIcons.Check, null, Modifier.size(24.dp), tint = Color.White)
                        Text(
                            text = ElderStrings.taken(language),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual dose card with complete status flow (Taken, Remind Later, Skip, Hear).
 */
@Composable
fun ElderMedicineCard(
    dose: ElderDose,
    language: AppLanguage,
    onHearClick: () -> Unit,
    onTakenClick: () -> Unit,
    onLaterClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealText = ElderStrings.mealInstruction(language, dose.meal)
    val isTaken = dose.isTaken

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isTaken) K.TealTint.copy(alpha = 0.4f) else K.Card)
            .border(1.dp, if (isTaken) K.Green else K.Border, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Row: Time + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dose.timeFormatted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                )

                // Status Badge (Not color alone: text + icon)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTaken) K.Green else K.Bg)
                        .border(1.dp, if (isTaken) K.Green else K.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isTaken) KIcons.Check else KIcons.Alert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isTaken) Color.White else K.Muted,
                        )
                        Text(
                            text = if (isTaken) ElderStrings.taken(language) else ElderStrings.notTaken(language),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTaken) Color.White else K.Muted,
                        )
                    }
                }
            }

            // Name & Instruction
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${dose.name} ${dose.strength}".trim(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                )
                if (mealText.isNotBlank()) {
                    Text(
                        text = mealText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = K.Muted,
                    )
                }
            }

            // Action Row (scrolls sideways at narrow widths rather than squeezing the labels)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hear Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.Bg)
                        .border(1.dp, K.Border, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onHearClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KIcons.Volume, null, Modifier.size(20.dp), tint = K.Ink)
                        Text(ElderStrings.hear(language), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                    }
                }

                // Taken Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isTaken) K.Teal else K.Green)
                        .clickable(role = Role.Button, onClick = onTakenClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KIcons.Check, null, Modifier.size(20.dp), tint = Color.White)
                        Text(
                            text = if (isTaken) "Done" else ElderStrings.taken(language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                if (!isTaken) {
                    // Remind Later
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(K.Bg)
                            .border(1.dp, K.Border, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button, onClick = onLaterClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Later", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = K.Ink, maxLines = 1)
                    }

                    // Skip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(K.Bg)
                            .border(1.dp, K.Border, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button, onClick = onSkipClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ElderStrings.skip(language), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = K.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Accessible lab report summary card with non-diagnostic framing.
 */
@Composable
fun ElderHealthCard(
    item: ElderHealthItem,
    language: AppLanguage,
    onExplainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.isFlagged) K.WarnBg else K.Card)
            .border(1.dp, if (item.isFlagged) K.WarnBorder else K.Border, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Test Name & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.testName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (item.isFlagged) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(K.WarnIcon)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Outside range",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // Value and Range
            Text(
                text = item.valueText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isFlagged) K.WarnText else K.Ink,
            )

            if (!item.rangeText.isNullOrBlank()) {
                Text(
                    text = "Report Range: ${item.rangeText}",
                    fontSize = 16.sp,
                    color = K.Muted,
                )
            }

            // Safe Doctor Advisory
            if (item.isFlagged) {
                Text(
                    text = ElderStrings.doctorDiscussionAdvice(language),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = K.WarnText,
                    lineHeight = 20.sp,
                )
            }

            // Explain Audio Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(K.Card)
                    .border(1.dp, K.Teal, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onExplainClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(KIcons.Volume, null, Modifier.size(22.dp), tint = K.Teal)
                    Text(
                        text = ElderStrings.explainReport(language),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = K.Teal,
                    )
                }
            }
        }
    }
}

/**
 * Medical ID / Emergency card with direct 1-tap phone dialer.
 */
@Composable
fun ElderEmergencyCard(
    memberName: String,
    bloodGroup: String?,
    allergies: String?,
    conditions: String?,
    emergencyName: String?,
    emergencyPhone: String?,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(K.Card)
            .border(1.5.dp, K.WarnIcon, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KIcons.Alert, null, Modifier.size(20.dp), tint = K.WarnIcon)
                Text(
                    text = ElderStrings.emergency(language),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.WarnIcon,
                    letterSpacing = 0.5.sp,
                )
            }

            Text(
                text = memberName,
                fontFamily = K.Display,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = K.Ink,
            )

            if (!bloodGroup.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ElderStrings.bloodGroupLabel(language), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = K.Muted)
                    Text(bloodGroup, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = K.Ink)
                }
            }

            if (!allergies.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ElderStrings.allergiesLabel(language), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = K.Muted)
                    Text(allergies, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = K.WarnText)
                }
            }

            if (!conditions.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ElderStrings.conditionsLabel(language), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = K.Muted)
                    Text(conditions, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = K.Ink)
                }
            }

            if (!emergencyPhone.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.WarnIcon)
                        .clickable(role = Role.Button) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyPhone"))
                            context.startActivity(intent)
                        }
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ElderStrings.callEmergencyContact(language),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (!emergencyName.isNullOrBlank()) {
                            Text(
                                text = "$emergencyName · $emergencyPhone",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
