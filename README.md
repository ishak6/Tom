# Tom — Offline-first Android voice assistant

Tom is a native Android voice-assistant prototype built with Kotlin and Jetpack Compose.

## Current capabilities

- Bengali streaming speech recognition with the Sherpa-ONNX Bengali model after a one-time model download.
- English recognition through Android's **on-device** speech recognizer only. Tom does not fall back to a network-backed recognizer.
- Local command handling for greetings, time, Android Settings, app launching, navigation, volume, notifications, and other explicitly supported device actions.
- Optional phone navigation through an Accessibility Service that the device owner must enable manually.
- Bengali/English text-to-speech through the Android TTS engine. Offline voice availability depends on installed Android voice data.

## Not implemented yet

These are planned items, not working features:

- Wake word detection ("Tom").
- Voice enrollment and speaker verification.
- A user-started foreground microphone service for background listening.
- Reliable screen-off operation and POCO/MIUI autostart/battery-policy compatibility.
- Device-tested offline English language-pack availability and Bengali TTS voice availability.

The current microphone flow is started from the open app. Do not assume that listening continues after the app is closed or the screen is locked.

## Privacy and security

- Speech recognition is designed to run locally after the required model/language data is installed.
- The Bengali model download requires internet once. Downloaded model data is stored in the app's private files directory.
- Model downloads and archive extraction have size/file-count limits and reject archive path traversal.
- Accessibility is opt-in and must be enabled by the user in Android Settings. The service does not retrieve window contents, read screen text, capture credentials, or perform gestures.
- Tom does not store Android PINs, passwords, or patterns and does not bypass Android Keyguard.
- Microphone permission is requested at runtime. There is no hidden microphone or camera operation.

## Build

The GitHub Actions workflow builds a debug APK with JDK 17, Android SDK 35, and Gradle 8.9. It runs Android unit-test and lint tasks before assembling the APK.

Download the `Tom-debug-apk` artifact from a successful workflow run.

## Device validation still required

CI compilation does not prove runtime compatibility. Validate on the target POCO M3 Pro 5G / MIUI device:

1. Install the APK and grant microphone permission.
2. Download the Bengali model over a stable connection, then enable airplane mode and test Bengali recognition.
3. Install the English offline language pack in Android speech settings and test English with internet disabled.
4. Test TTS voice data for both languages.
5. Enable Tom Phone Control manually and verify only the documented local actions.
6. Test app backgrounding, screen-off behavior, battery saver, reboot, and permission revocation. Background wake-word operation is not available until its explicit foreground-service implementation is added.

## Development status

The project is an offline-first prototype. A successful CI build is not a claim that unimplemented features or device-specific behavior have been validated.
