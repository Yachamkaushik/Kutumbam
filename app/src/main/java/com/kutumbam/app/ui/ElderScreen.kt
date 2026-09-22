package com.kutumbam.app.ui

import androidx.compose.runtime.Composable

/** Large-button, voice-first mode for a parent who doesn't operate the phone. Delegates to the eldermode module. */
@Composable
fun ElderScreen(vm: AppViewModel) {
    com.kutumbam.app.eldermode.ui.ElderModeRoot(appViewModel = vm)
}
