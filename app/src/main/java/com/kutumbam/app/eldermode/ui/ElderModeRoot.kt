package com.kutumbam.app.eldermode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kutumbam.app.eldermode.models.ElderDestination
import com.kutumbam.app.eldermode.screens.ElderAskScreen
import com.kutumbam.app.eldermode.screens.ElderEmergencyScreen
import com.kutumbam.app.eldermode.screens.ElderHealthScreen
import com.kutumbam.app.eldermode.screens.ElderHomeScreen
import com.kutumbam.app.eldermode.screens.ElderMedicinesScreen
import com.kutumbam.app.eldermode.screens.ElderMyDayScreen
import com.kutumbam.app.eldermode.viewmodel.ElderViewModel
import com.kutumbam.app.ui.AppViewModel
import com.kutumbam.app.ui.Screen
import com.kutumbam.app.ui.theme.K
import kotlinx.coroutines.delay

/**
 * Main Composable entry point for the isolated Elder Mode feature.
 * Coordinates internal sub-screen navigation, back handling,
 * and seamless integration with [AppViewModel] when hosted in the main app.
 */
@Composable
fun ElderModeRoot(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel? = null,
    elderViewModel: ElderViewModel = viewModel(),
    onExit: (() -> Unit)? = null,
) {
    val state by elderViewModel.uiState.collectAsState()

    // Open on whichever family member was selected before entering Elder Mode, not just the first one.
    LaunchedEffect(appViewModel) {
        appViewModel?.home?.value?.selected?.id?.let { elderViewModel.selectMember(it) }
    }

    val exitAction: () -> Unit = {
        if (onExit != null) {
            onExit()
        } else {
            appViewModel?.stopSpeaking()
            appViewModel?.show(Screen.HOME)
        }
    }

    // Hardware / Gesture Back Navigation
    BackHandler {
        if (state.currentDest != ElderDestination.HOME) {
            elderViewModel.navigate(ElderDestination.HOME)
        } else {
            exitAction()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = K.Bg,
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when (state.currentDest) {
                ElderDestination.HOME -> ElderHomeScreen(
                    state = state,
                    viewModel = elderViewModel,
                    onExitElderMode = exitAction,
                )
                ElderDestination.MY_DAY -> ElderMyDayScreen(
                    state = state,
                    viewModel = elderViewModel,
                )
                ElderDestination.MEDICINES -> ElderMedicinesScreen(
                    state = state,
                    viewModel = elderViewModel,
                )
                ElderDestination.ASK -> ElderAskScreen(
                    state = state,
                    viewModel = elderViewModel,
                )
                ElderDestination.HEALTH -> ElderHealthScreen(
                    state = state,
                    viewModel = elderViewModel,
                )
                ElderDestination.EMERGENCY -> ElderEmergencyScreen(
                    state = state,
                    viewModel = elderViewModel,
                )
            }

            // High-contrast transient status message banner
            state.actionMessage?.let { messageText ->
                LaunchedEffect(messageText) {
                    delay(3500)
                    elderViewModel.clearMessage()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(K.Ink)
                        .clickable { elderViewModel.clearMessage() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = messageText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
