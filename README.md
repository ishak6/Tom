# Tom — Offline-First Bengali & English Android Voice Assistant

Tom is a native Android voice-assistant project built with Kotlin and Jetpack Compose. It provides Bengali offline speech recognition through Sherpa-ONNX, Android on-device English speech recognition where a compatible offline language pack is installed, text-to-speech responses, local voice commands, and optional user-enabled Android Accessibility actions.

> **Project status:** A working Android debug APK is produced by GitHub Actions. This is a prototype / academic project, not a fully completed always-on assistant. Features listed as planned below are not claimed as implemented.

## Current implemented features

- Native Android UI using Kotlin and Jetpack Compose; no WebView.
- Bengali streaming ASR using a locally stored Sherpa-ONNX model.
- English recognition through Android's on-device speech recognizer when the device supports it and the offline language pack is installed. Tom does not silently fall back to network recognition.
- Bengali / English interface language selection.
- Text-to-speech responses when the installed Android TTS engine supports the selected language.
- Local commands for greetings, time, date, battery level, help, alarms and timers (alarm/timer requests open the Android Clock confirmation UI).
- Local command history (last 10 unique entries), with a clear-history control.
- Optional, user-enabled Accessibility service for supported navigation actions, media-volume adjustment, settings shortcuts, screenshot, lock screen and launching installed apps.
- Permission and device-status dashboard.
- GitHub Actions workflow builds and uploads a debug APK.

## Requirements

- Android 8.0 (API 26) or newer.
- Microphone permission for speech input.
- For Bengali offline recognition, download the Bengali model once from the app. The compressed download is approximately 88 MB; allow storage space for extraction.
- For English offline recognition, install an English offline speech pack in Android voice / speech settings and use a device whose speech service supports on-device recognition.
- To use phone navigation and app-launch commands, manually enable **Tom Phone Control** in Android Settings → Accessibility. This permission is optional.

## Build the APK

The repository includes a GitHub Actions workflow at `.github/workflows/android.yml`.

1. Open the repository's **Actions** tab.
2. Select **Android CI**.
3. Open the latest successful **Build Tom APK** run.
4. Download the **Tom-debug-apk** artifact.
5. Extract the ZIP to obtain the APK.

The workflow downloads the Sherpa-ONNX Android runtime, installs Android SDK 35 / Build Tools 35.0.0, runs `gradle assembleDebug`, and uploads the debug APK.

## Run and use

1. Install the APK on a compatible Android device.
2. Open Tom and grant microphone permission when requested.
3. Select বাংলা and download the Bengali offline model once; wait for the app to report that the model is ready.
4. Tap **Tap to speak**, speak a supported command, and wait for Tom's response.
5. Select English only after installing a compatible English offline speech pack.
6. If desired, enable Tom Phone Control manually in Accessibility settings. Review the permission dashboard in the app.

## Example supported commands

| Category | Example |
|---|---|
| Basic | “হ্যালো”, “তুমি কে”, “সাহায্য” |
| Time and date | “এখন সময় কত”, “আজকের তারিখ” |
| Battery | “ব্যাটারি কত” |
| Clock | “৫ মিনিটের টাইমার দাও”, “সকাল ৭ টায় অ্যালার্ম দাও” |
| Navigation (Accessibility enabled) | “হোমে যাও”, “পিছনে যাও”, “সাম্প্রতিক অ্যাপ” |
| Volume (Accessibility enabled) | “ভলিউম বাড়াও”, “ভলিউম কমাও”, “শব্দ বন্ধ কর” |
| Settings (Accessibility enabled) | “Wi-Fi settings”, “ব্রাইটনেস সেটিংস” |
| Apps (Accessibility enabled) | “Open calculator”, “ক্যালেন্ডার খোলো” |

Exact recognition depends on the device, microphone, pronunciation and installed language models. Some settings commands open Android settings pages rather than changing protected system toggles directly.

## Privacy and safety

- Bengali speech recognition runs locally after the model is downloaded.
- English recognition is configured to prefer on-device recognition and is unavailable when a compatible offline recognizer / language pack is missing.
- Recent recognized commands are stored locally on the device and can be cleared from the app.
- Tom does not collect or upload call audio, SMS, OTPs, passwords or screen contents.
- Accessibility actions are optional and require the user to enable the service in Android settings.
- Tom does not store Android PINs, patterns or passwords and does not bypass the Android lock screen.

## Known limitations / future work

The following are **not implemented and must not be presented as completed**:

- Always-on “Tom” wake-word detection.
- Wake-word listening while the app is closed or the screen is off.
- Voice enrollment and offline speaker verification.
- Persistent microphone foreground service and its notification-based controls.
- Automatic start / recovery after reboot.
- Direct flashlight toggle and direct brightness-level adjustment.
- Full media transport controls (play / pause / next / previous).
- General-purpose AI conversation, online search, long-term assistant memory and multi-step automation.
- Full device compatibility and real-device QA across manufacturers.

Android background microphone restrictions, battery management and OEM-specific behavior require additional engineering and device testing. Do not assume screen-off or force-stopped wake-word operation.

## Testing and release notes

- CI build: see the latest successful **Android CI** run in the repository Actions tab.
- CI success confirms that the debug APK compiled and the artifact was uploaded; it does not prove every command works on every physical device.
- Before a final production release, test installation, microphone permission, Bengali model download, Bengali recognition, English offline availability, TTS language support, Accessibility actions and battery behavior on the target phone.
- This repository currently produces a **debug APK**, not a signed production release.

## Technology

- Kotlin, Android SDK 35, Jetpack Compose / Material 3
- Sherpa-ONNX Android runtime for Bengali streaming ASR
- Android SpeechRecognizer on-device mode for English
- Android TextToSpeech, AlarmClock intents and AccessibilityService
- GitHub Actions CI
