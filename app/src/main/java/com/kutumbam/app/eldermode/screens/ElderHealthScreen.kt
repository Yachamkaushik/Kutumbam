package com.kutumbam.app.eldermode.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutumbam.app.eldermode.components.ElderHeader
import com.kutumbam.app.eldermode.components.ElderHealthCard
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.models.ElderState
import com.kutumbam.app.eldermode.models.ElderStrings
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.theme.K
import com.kutumbam.app.ui.theme.KIcons

/**
 * Simplified "My Health" Screen for Elder Mode.
 * Presents only clear, high-signal lab values, out-of-range alerts,
 * non-diagnostic doctor advisories, and audio readouts.
 */
@Composable
fun ElderHealthScreen(
    state: ElderState,
    viewModel: ElderViewModel,
    modifier: Modifier = Modifier,
) {
    val lang = state.language
    val flaggedCount = state.flaggedItems.size

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
                text = ElderStrings.myHealth(lang),
                fontFamily = K.Display,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = K.Ink,
            )

            // Health Status Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (flaggedCount > 0) K.WarnBg else K.TealTint)
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.latestReportDate != null) {
                        Text(
                            text = ElderStrings.reportDate(lang, state.latestReportDate),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (flaggedCount > 0) K.WarnText else K.Teal,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (flaggedCount > 0) KIcons.Alert else KIcons.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (flaggedCount > 0) K.WarnIcon else K.Green,
                        )
                        Text(
                            text = if (flaggedCount > 0) ElderStrings.outsideRange(lang, flaggedCount) else ElderStrings.allLabsNormal(lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (flaggedCount > 0) K.WarnText else K.Teal,
                        )
                    }
                }
            }

            // Health Items List
            if (state.recentHealthItems.isEmpty()) {
                Text(
                    text = ElderStrings.emptyReports(lang),
                    fontSize = 19.sp,
                    color = K.Muted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                state.recentHealthItems.forEach { item ->
                    ElderHealthCard(
                        item = item,
                        language = lang,
                        onExplainClick = { viewModel.speakLab(item) },
                    )
                }
            }
        }
    }
}
