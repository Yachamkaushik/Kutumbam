package com.kutumbam.app.eldermode.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.components.ElderEmergencyCard
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Dedicated Emergency / Medical ID Screen for Elder Mode.
 * Instant access to emergency phone dialing, blood group,
 * critical allergies, and current medical conditions.
 */
@Composable
fun ElderEmergencyScreen(
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(KIcons.Alert, null, Modifier.size(28.dp), tint = K.WarnText)
                Text(
                    text = ElderStrings.emergency(lang),
                    fontFamily = K.Display,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = K.WarnText,
                )
            }

            ElderEmergencyCard(
                memberName = state.memberName,
                bloodGroup = state.bloodGroup,
                allergies = state.allergies,
                conditions = state.conditions,
                emergencyName = state.emergencyName,
                emergencyPhone = state.emergencyPhone,
                language = lang,
            )
        }
    }
}
