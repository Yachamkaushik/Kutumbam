package com.kutumbam.app.eldermode.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.components.ElderMedicineCard
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K

/**
 * Dedicated Medicine List Screen for Elder Mode.
 * High-contrast, large touch-target cards with Taken, Later, Skip, and Hear actions.
 */
@Composable
fun ElderMedicinesScreen(
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
            Text(
                text = "💊 ${ElderStrings.myMedicines(lang)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = K.Ink,
            )

            if (state.doses.isEmpty()) {
                Text(
                    text = ElderStrings.emptyMedicines(lang),
                    fontSize = 20.sp,
                    color = K.Muted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                state.doses.forEach { dose ->
                    ElderMedicineCard(
                        dose = dose,
                        language = lang,
                        onHearClick = { viewModel.speakDose(dose) },
                        onTakenClick = { viewModel.markDoseTaken(dose) },
                        onLaterClick = { viewModel.markDoseLater(dose) },
                        onSkipClick = { viewModel.markDoseSkipped(dose) },
                    )
                }
            }
        }
    }
}
