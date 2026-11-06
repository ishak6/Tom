# Tom — Offline-first Android voice assistant

Tom is a native Android voice-assistant prototype built with Kotlin and Jetpack Compose.

## Current capabilities

- Bengali streaming speech recognition with the Sherpa-ONNX Bengali model after a one-time model download.
- English recognition through Android's **on-device** speech recognizer only. Tom does not fall back to a network-backed recognizer.
- Local command handling for greetings, time, Android Settings, app launching, navigation, volume, notifications, and other explicitly supported device actions.
- Optional phone navigation through an Accessibility Service that the device owner must enable manually.
- A deterministic command harness normalizes Bengali/English phrases, matches registered aliases exactly, rejects substring false positives, and requires an explicit "open/launch" phrase for app launching.
- JVM unit tests cover registered aliases, unsupported/ambiguous phrases, app-launch parsing, blank input, and automatic resource-policy decisions.
- Bengali/English text-to-speech through the Android TTS engine. Offline voice availability depends on installed Android voice data.
- Explicitly user-started foreground microphone service for background Bengali ASR. It displays an ongoing notification with a Stop action, requires microphone permission, and on Android 13+ asks for notification permission.
- Background service accepts a spoken command only when the recognized phrase starts with the text prefix "Tom " or "টম ". This is phrase-prefix gating, **not** a dedicated acoustic wake-word detector. The command must also be supported and Tom Phone Control must be manually enabled.
- Automatic resource policy checks Android's available-memory and app-storage telemetry, selects one ASR thread in low-memory conditions (two otherwise), and refuses model installation when free app storage is below 350 MB.
- Resource handling is conservative: Android remains responsible for system RAM reclamation. Tom only removes its own incomplete temporary model archive; it never automatically deletes user files, installed model data, chat history, or other apps' data.

## Not implemented yet

These are planned items, not working features:

- Dedicated wake-word model for reliable hands-free "Tom" detection.
- Voice enrollment and speaker verification.
- Background English recognition.
- Guaranteed screen-off operation, reboot auto-start, and POCO/MIUI autostart/battery-policy compatibility.
- Device-tested offline English language-pack availability and Bengali TTS voice availability.

The background service is opt-in and can be stopped from its notification, the app, or the spoken "Tom stop" / "টম থামো" command. Android and MIUI may stop microphone work under battery restrictions; screen-off reliability must be tested on the target device. No service starts automatically after reboot.

## Command behavior

- Tom uses exact registered command aliases after case/spacing/punctuation normalization. It does not execute a command just because a word appears inside a longer sentence.
- App launching requires an explicit phrase such as "open YouTube", "launch Chrome", or "YouTube খোলো". A bare app name is not treated as an instruction.
- Unrecognized phrases are reported as unsupported and ignored; they are not marked as successful.
- The command harness is deliberately conservative. Speech recognition can still mishear words, so verify the recognized phrase before relying on device actions.
- Run JVM tests with `./gradlew :app:testDebugUnitTest`; CI runs them before APK assembly.

## Privacy and security

- Speech recognition is designed to run locally after the required model/language data is installed.
- The Bengali model download requires internet once. Downloaded model data is stored in the app's private files directory.
- Model downloads and archive extraction have size/file-count limits and reject archive path traversal. The resource manager checks available internal storage before installation and adjusts ASR thread count based on Android memory signals.
- The foreground service uses the microphone only after an explicit in-app start action and shows a persistent notification. It does not upload or save microphone audio.
- Accessibility is opt-in and must be enabled by the user in Android Settings. The service does not retrieve window contents, read screen text, capture credentials, or perform gestures.
- Tom does not store Android PINs, passwords, or patterns and does not bypass Android Keyguard.
- Microphone and notification permissions are requested at runtime. There is no hidden microphone or camera operation.

## Build

The GitHub Actions workflow builds a debug APK with JDK 17, Android SDK 35, and Gradle 8.9. It runs Android unit-test and lint tasks before assembling the APK.

Download the `Tom-debug-apk` artifact from a successful workflow run.

## Device validation still required

CI compilation does not prove runtime compatibility. Validate on the target POCO M3 Pro 5G / MIUI device:

1. Install the APK and grant microphone permission.
2. Download the Bengali model over a stable connection, then enable airplane mode and test Bengali recognition.
3. Enable Background listening in Tom; grant notification permission; verify the persistent notification and Stop action.
4. Speak a complete command beginning with "Tom" or "টম" and verify only supported commands run.
5. Lock the screen and test background listening; repeat with battery saver enabled and disabled.
6. Install the English offline language pack in Android speech settings and test English with internet disabled.
7. Test TTS voice data for both languages.
8. Enable Tom Phone Control manually and verify only the documented local actions.
9. Test permission revocation and force-stop behavior.

## Development status

The project is an offline-first prototype. A successful CI build is not a claim that unimplemented features or device-specific behavior have been validated.
