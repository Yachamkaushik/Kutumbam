# Kutumbam

An offline family health locker for Android, built for the iQOO City Battles hackathon.

An adult child manages prescriptions, lab reports and medicine schedules for their parents and children on one phone, including a parent who never touches a smartphone. There is no account and no server. Nothing medical leaves the device.

> Kutumbam restates what a prescription or report already says. It never diagnoses and never suggests starting, stopping or changing a dose. Anything outside a report's printed range is a prompt to talk to a doctor.

## How it works

```
photo / WhatsApp share  ->  ML Kit OCR  ->  rule-based parser  ->  confirm & correct  ->  Room
                                                                                            |
                                                          on-device LLM explains the confirmed data
```

- **OCR:** Google ML Kit Text Recognition v2, bundled model, fully offline.
- **Parser:** regex plus a shorthand dictionary (OD/BD/TDS/QID/HS/SOS, `1-0-1` dosing, before/after food, durations, and lab rows of test / value / unit / printed range). The LLM does not extract anything, because small models are unreliable at exact structured parsing.
- **Confirm screen:** nothing reaches the schedule until the user reviews and taps Confirm & Save.
- **LLM:** Gemma 4 E2B through [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), trying the Qualcomm NPU first, then GPU, then CPU. It only restates already-confirmed structured data. The app shows which backend actually ran.
- **Storage:** Room (SQLite) in app-private storage.

## Status

| Area | State |
|---|---|
| On-device LLM engine (NPU, GPU, CPU fallback) | Built, needs testing on a real phone |
| OCR and rule-based parser, with unit tests | Built |
| Family profiles, Room storage | Built |
| Home and Confirm Details screens | Built |
| Share-in from WhatsApp / Gallery | Built |
| Range check against the printed range | Built (logic and Home alert) |
| Reminders and alarms | Not started |
| Elder voice mode (large buttons, spoken in Telugu / Hindi / English) | Built, verified on emulator except audio quality |
| Ask-the-Locker, trend chart, immunization schedule | Not started |

## Requirements

- Android Studio (2026.1 or newer) with the Android SDK. compileSdk 37, minSdk 26.
- An arm64 Android device or emulator. A Snapdragon 8 Elite phone is needed to try the NPU.

## Build and run

Open this folder in Android Studio, wait for the Gradle sync, choose a device and press Run.

From the command line, using Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # parser and range-check tests
```

## The AI model

The model is several GB, so it is not in the repo. Download one file from [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm):

- `gemma-4-E2B-it.litertlm` (2.6 GB): CPU and GPU, any phone.
- `gemma-4-E2B-it_qualcomm_sm8750.litertlm` (3.0 GB): NPU build for Snapdragon 8 Elite (SM8750).

NPU builds are compiled per chip, so pick the one that matches your phone. Then either use **AI setup -> Import...** in the app, or push it with adb (open the app once first so the folder exists):

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.kutumbam.app/files/models/
```

In **AI setup -> AI model**, choose the file, leave the backend on Auto and tap **Load model**. The status line reports which backend ran.

## Project layout

```
app/src/main/java/com/kutumbam/app/
  llm/     LlmEngine interface, LiteRT-LM implementation, model storage, prompts
  ocr/     ML Kit wrapper and table-row reconstruction
  parse/   prescription, lab report, frequency, meal, duration and range logic (pure Kotlin)
  data/    Room entities, DAO, repository
  ui/      Home, Confirm, capture helpers, developer screens, theme
app/src/test/   parser and range-check unit tests
```

## Privacy

All documents, extracted data and images stay in app-private storage. There are no network calls for medical content and no analytics.
