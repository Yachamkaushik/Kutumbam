# Kutumbam — Elder Mode Feature Module

## Overview
This module (`com.kutumbam.app.eldermode`) implements a dedicated, voice-first, accessible **Elder Mode** for Kutumbam. It is built strictly as an isolated feature adhering to the Critical File-Safety Rule: existing project files are treated as read-only, ensuring no existing business logic or database structures are rewritten or duplicated.

## Architecture

```
com.kutumbam.app.eldermode/
├── models/
│   ├── ElderModels.kt        // Navigation destinations, Voice states, UI models
│   └── ElderStrings.kt       // Multilingual dictionary (Telugu primary, Hindi, English)
├── voice/
│   ├── ElderVoiceController.kt // Coordinates Speaker (TTS) & VoiceInput (STT) without collisions
│   └── ElderVoiceScripts.kt  // Deterministic spoken sentence generation
├── adapters/
│   └── ElderDataAdapter.kt   // Adapts Room entities & LockerData to Elder UI models
├── viewmodel/
│   └── ElderViewModel.kt     // Reactive state management and action handling
├── components/
│   ├── ElderButtons.kt       // Accessible, large (64dp) high-contrast buttons
│   ├── ElderHeader.kt        // Header, language selector, back button
│   └── ElderCards.kt         // Hero Next Medicine, Today's cards, Emergency Card
├── screens/
│   ├── ElderHomeScreen.kt    // Landing hub with My Day summary & 5 primary action buttons
│   ├── ElderMyDayScreen.kt   // Holistic daily schedule & spoken review
│   ├── ElderMedicinesScreen.kt // Detailed medicine cards with Taken/Later/Skip actions
│   ├── ElderAskScreen.kt     // 5-state voice assistant (IDLE, LISTENING, PROCESSING, ANSWER, ERROR)
│   ├── ElderHealthScreen.kt  // Simplified lab report & health summary
│   └── ElderEmergencyScreen.kt // Medical ID with 1-tap phone dialer
└── ui/
    └── ElderModeRoot.kt      // Root composable container
```

## Key Capabilities & Safety Principles

1. **Voice-First & Audio Coordination**:
   - Manages Android `TextToSpeech` (slower 0.85x cadence) and `SpeechRecognizer` with mutual exclusion.
   - Starting speech recognition automatically stops TTS; starting TTS stops voice recording.
2. **Deterministic Grounding & Medical Safety**:
   - Zero hallucination or ungrounded generative medical opinions.
   - Questions are retrieved deterministically from local records via `Retrieval.retrieve()`.
   - Open-ended medical advice queries trigger `AnswerRules.refusal()`.
   - Out-of-range lab results consistently advise: *"This reading is outside the range shown on the report. Please discuss this with your doctor."*
3. **Accessibility**:
   - Minimum 56dp–64dp tap targets on all interactive controls.
   - Complete TalkBack semantic content descriptions on every icon and button.
   - Status indicators do not rely on color alone (icon + clear text).
4. **Multilingual System**:
   - Supports **Telugu** as primary, with **Hindi** and **English** as secondary languages.
   - Medicine names strictly remain in Latin script (e.g. "Metformin 500 mg") as printed on medicine strips.

## Integration Note (REQUIRES USER APPROVAL)
To render this complete Elder Mode when navigating to `Screen.ELDER`, delegate to `ElderModeRoot` inside `app/src/main/java/com/kutumbam/app/ui/ElderScreen.kt`:
```kotlin
@Composable
fun ElderScreen(vm: AppViewModel) {
    com.kutumbam.app.eldermode.ui.ElderModeRoot(appViewModel = vm)
}
```
*(In accordance with the prompt's isolation rules, this change requires explicit user authorization before application).*
