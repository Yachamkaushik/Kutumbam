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
| Report alert screen (values vs the range printed on the report, small labelled fallback table) | Built, verified on emulator |
| Trend chart from stored values, with plain-language summary (on-device AI rewrites it when a model is loaded) | Built, chart verified on emulator; AI summary needs a phone with the model |
| Medicine reminders (exact alarms, editable times, Taken button on the notification) | Built, verified on emulator |
| Elder voice mode (large-button hub, spoken My Day summary, per-medicine Taken/Later/Skip, 5-state voice Ask with the same safety rules as Ask the Locker, simplified health screen, emergency screen with 1-tap dialer; Telugu primary, Hindi/English) | Built, verified on emulator except audio quality and voice input |
| Child profile and immunization timeline (India UIP schedule engine, vaccination card scan, mark-as-given, daily due/overdue reminder) | Built, verified on emulator |
| Ask-the-Locker (retrieval over stored records, refuses medical advice, grounding check on the on-device AI, sources shown, voice input and read-aloud) | Built, verified on emulator except voice and the AI answer path |
| Refill predictor (walks the schedule from the moment the tablets were counted, using each dose's own tablet count; daily 9:30 reminder from 3 days ahead; recount after a refill; answers "when will it run out?" in Ask) | Built, verified on emulator |
| Duplicate-medicine warning (bundled brand to ingredient list; on the Confirm screen and on Home; never says which to stop) | Built, verified on emulator |
| Tablets per dose (1-0-2, ½-0-½ read from the prescription, editable, shown on reminders, Home and in the spoken script) | Built, verified on emulator except audio |
| Doctor visit prep sheet (questions from recently flagged lab values, medicine changes between the last two prescriptions, ended courses, overlaps and low supply; shareable as text) | Built, verified on emulator |
| Pediatrician visit prep sheet (same, plus growth entries with trends and immunization due/overdue) and a Growth card on the child screen | Built, verified on emulator |
| One-page health summary PDF (medicines, latest labs with printed ranges, child growth and vaccines, visit questions; saved to Downloads for iQOO Office Kit transfer, and shared via the share sheet) | Built, verified on emulator |
| Bottom tabs: Today, Health (Medicines / Reports / Vaccines), Ask, Visit | Built, verified on emulator |
| Yourself as a profile ("You"), set up first; reminders, visit sheet, Ask and the summary speak in the first person | Built, verified on emulator |
| Medical ID (blood group, allergies, conditions, emergency contact with a Call button), also printed on the summary PDF | Built, verified on emulator |
| Home readings (blood pressure, sugar, weight) with trend, history and limits you enter from your doctor; used in the visit sheet, the PDF and Ask | Built, verified on emulator |
| Notes ("things I noticed") that go onto the visit sheet and the PDF; doses-taken check on your own visit sheet | Built, unit-tested; notes screen not clicked through on the emulator |
| Share-in asks whose document it is | Built, verified on emulator |

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
  parse/   prescription, lab report, vaccination card, immunization schedule, range logic (pure Kotlin)
  data/    Room entities, DAO, repository
  reminder/ AlarmManager scheduling, notification receiver, boot re-arming
  speech/  on-device text-to-speech and the spoken medicine script
  locker/  retrieval, safety rules and answer checks for Ask-the-Locker (pure Kotlin)
  ui/      Home, Confirm, Ask, Elder mode, Report, Trend chart, Child immunization, capture helpers, developer screens, theme
app/src/test/   parser and range-check unit tests
```

## Privacy

All documents, extracted data and images stay in app-private storage. There are no network calls for medical content and no analytics.
