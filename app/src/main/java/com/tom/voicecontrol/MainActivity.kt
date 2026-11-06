package com.tom.voicecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val TomBackground = Color(0xFF0B1020)
private val TomSurface = Color(0xFF151D32)
private val TomAccent = Color(0xFF78E0C3)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var offlineBengaliEngine: OfflineBengaliSpeechEngine? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var selectedLanguage by mutableStateOf("bn-BD")
    private var status by mutableStateOf("প্রস্তুত • Ready")
    private var recognizedText by mutableStateOf("এখানে আপনার কথা দেখা যাবে")
    private var responseText by mutableStateOf("মাইক্রোফোন চাপ দিয়ে কথা বলুন।")
    private var isListening by mutableStateOf(false)
    private var offlineModeLabel by mutableStateOf("Offline speech availability checking…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        offlineBengaliEngine = OfflineBengaliSpeechEngine(
            context = applicationContext,
            onStatus = { message ->
                runOnUiThread {
                    status = message
                    isListening = message.contains("শুনছি")
                    updateSpeechAvailability()
                }
            },
            onText = { text ->
                runOnUiThread {
                    recognizedText = text
                    handleLocalCommand(text)
                }
            },
            onError = { message ->
                runOnUiThread {
                    isListening = false
                    status = message
                }
            }
        )
        updateSpeechAvailability()

        setContent {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) startVoiceInput()
                else status = "মাইক্রোফোন অনুমতি দেওয়া হয়নি • Permission denied"
            }
            val backgroundNotificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) startBackgroundVoiceService()
                else status = "দৃশ্যমান listening notification ছাড়া background microphone চালু করা হয়নি"
            }
            val backgroundPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    status = "Background listening-এর জন্য microphone permission প্রয়োজন"
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    backgroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startBackgroundVoiceService()
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = TomAccent,
                background = TomBackground,
                surface = TomSurface,
                onPrimary = TomBackground,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                TomHome(
                    status = status,
                    recognizedText = recognizedText,
                    responseText = responseText,
                    isListening = isListening,
                    language = selectedLanguage,
                    offlineLabel = offlineModeLabel,
                    modelInstalled = offlineBengaliEngine?.isModelInstalled() == true,
                    onDownloadModel = { offlineBengaliEngine?.downloadModel() },
                    onListen = {
                        if (hasMicPermission) startVoiceInput()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { stopVoiceInput() },
                    onBackgroundStart = {
                        if (selectedLanguage != "bn-BD") {
                            status = "Background local listening এখন বাংলা মডেলেই সীমাবদ্ধ"
                        } else if (offlineBengaliEngine?.isModelInstalled() != true) {
                            status = "আগে বাংলা Offline Model Download করুন"
                        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            backgroundPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            backgroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startBackgroundVoiceService()
                        }
                    },
                    onBackgroundStop = { stopBackgroundVoiceService() },
                    onLanguage = { selectedLanguage = it; updateSpeechAvailability() },
                    onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                    onAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) {
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) {
                    runOnUiThread { status = "Voice output failed • ভয়েস চালু হয়নি" }
                }
            })
        }
    }

    private fun updateSpeechAvailability() {
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val bengaliReady = offlineBengaliEngine?.isModelInstalled() == true
        offlineModeLabel = if (bengaliReady) {
            "বাংলা Offline Model installed • Bengali recognition runs locally"
        } else {
            "বাংলা Offline Model not installed • Download once (about 88 MB)"
        } + if (onDeviceAvailable) " • English offline recognition available when its language pack is installed" else " • English offline recognition unavailable on this Android build"
    }

    private fun startVoiceInput() {
        if (selectedLanguage.startsWith("bn")) {
            val engine = offlineBengaliEngine
            if (engine == null || !engine.isModelInstalled()) {
                isListening = false
                status = "বাংলা Offline Model আগে Download করুন"
                return
            }
            isListening = true
            engine.startListening()
            return
        }

        // Never fall back to createSpeechRecognizer(): providers may send audio to a
        // network service. English mode is strictly on-device or unavailable.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            isListening = false
            status = "এই Android-এ English offline speech service নেই • অনলাইন fallback বন্ধ রাখা হয়েছে"
            return
        }
        if (isListening) stopVoiceInput()

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    status = "শুনছি… বলুন"
                }
                override fun onBeginningOfSpeech() { status = "কথা শনাক্ত করছি…" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isListening = false
                    status = "প্রসেস করছি…"
                }
                override fun onError(error: Int) {
                    isListening = false
                    status = if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        "English offline language pack ইনস্টল নেই • Settings-এ offline speech language যোগ করুন"
                    } else {
                        "Offline speech error: ${speechErrorText(error)}"
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isBlank()) {
                        status = "কথা বোঝা যায়নি • আবার চেষ্টা করুন"
                    } else {
                        recognizedText = text
                        handleLocalCommand(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) recognizedText = partial
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            status = "মাইক্রোফোন চালু হচ্ছে…"
            speechRecognizer?.startListening(intent)
        } catch (error: Exception) {
            isListening = false
            status = "শুরু করা যায়নি: ${error.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun handleLocalCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT)
        val isBangla = selectedLanguage.startsWith("bn")

        if (TomAccessibilityService.matchesCommand(command)) {
            val phoneControlEnabled = TomAccessibilityService.isEnabled(this)
            val phoneControlAnswer = if (!phoneControlEnabled) {
                if (isBangla) {
                    "ফোন নিয়ন্ত্রণ ব্যবহার করতে Settings → Accessibility → Tom Phone Control চালু করুন। এটি আপনার নিজের অনুমতি ছাড়া চালু হবে না।"
                } else {
                    "To use phone control, open Settings → Accessibility → Tom Phone Control and enable it yourself."
                }
            } else {
                TomAccessibilityService.executeCommand(command, isBangla) ?: if (isBangla) {
                    "এই ফোন নিয়ন্ত্রণ কমান্ডটি সম্পন্ন করা যায়নি।"
                } else {
                    "I could not complete that phone-control command."
                }
            }
            responseText = phoneControlAnswer
            status = if (phoneControlEnabled) "Phone control • processed" else "Phone control • permission needed"
            speak(phoneControlAnswer)
            return
        }

        val answer = when {
            listOf("hello", "হ্যালো", "হাই", "hi", "সালাম", "আসসালামু").any(command::contains) ->
                if (isBangla) "হ্যালো! আমি টম। কীভাবে সাহায্য করতে পারি?" else "Hello! I'm Tom. How can I help?"
            listOf("time", "সময়", "সময়", "কয়টা", "কটা বাজে").any(command::contains) -> {
                val now = DateFormat.getTimeInstance(DateFormat.SHORT, if (isBangla) Locale("bn", "BD") else Locale.US).format(Date())
                if (isBangla) "এখন সময় $now" else "The time is $now"
            }
            listOf("settings", "সেটিংস", "সেটিং").any(command::contains) -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                if (isBangla) "ফোনের সেটিংস খুলছি।" else "Opening Android settings."
            }
            listOf("stop", "বন্ধ কর", "থামো").any(command::contains) ->
                if (isBangla) "ঠিক আছে।" else "Okay."
            else -> if (isBangla) {
                "এই কমান্ডটি এখনো যোগ করা হয়নি। আমি আপাতত সময়, হ্যালো এবং সেটিংস কমান্ড বুঝি।"
            } else {
                "That command is not supported yet. I currently understand hello, time, and settings."
            }
        }
        responseText = answer
        status = "কমান্ড সম্পন্ন • Local command processed"
        speak(answer)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            status = "Text-to-speech engine is not ready"
            return
        }
        val locale = if (selectedLanguage.startsWith("bn")) Locale("bn", "BD") else Locale.US
        val languageResult = textToSpeech?.setLanguage(locale)
        if (languageResult == LANG_MISSING_DATA || languageResult == LANG_NOT_SUPPORTED) {
            status = "এই ভাষার voice data নেই; Android TTS settings থেকে voice data ইনস্টল করুন"
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tom-response")
    }

    private fun startBackgroundVoiceService() {
        stopVoiceInput()
        try {
            val intent = Intent(this, TomBackgroundVoiceService::class.java).apply {
                action = TomBackgroundVoiceService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
            status = "Background local listening requested • Notification থেকে Stop করতে পারবেন"
        } catch (error: Exception) {
            status = "Background service শুরু হয়নি: ${error.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun stopBackgroundVoiceService() {
        stopService(Intent(this, TomBackgroundVoiceService::class.java))
        status = "Background listening বন্ধ করার অনুরোধ পাঠানো হয়েছে"
    }

    private fun stopVoiceInput() {
        offlineBengaliEngine?.stopListening()
        speechRecognizer?.cancel()
        isListening = false
        status = "শোনা বন্ধ • Listening stopped"
    }

    private fun speechErrorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language unavailable (install the English offline language pack)"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported by this recognition service"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network/service"
        SpeechRecognizer.ERROR_NO_MATCH -> "no speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        else -> "code $error"
    }

    override fun onDestroy() {
        offlineBengaliEngine?.release()
        offlineBengaliEngine = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun TomHome(
    status: String,
    recognizedText: String,
    responseText: String,
    isListening: Boolean,
    language: String,
    offlineLabel: String,
    modelInstalled: Boolean,
    onDownloadModel: () -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onBackgroundStart: () -> Unit,
    onBackgroundStop: () -> Unit,
    onLanguage: (String) -> Unit,
    onSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = TomBackground) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOM", color = TomAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Your voice assistant", color = Color.LightGray, fontSize = 12.sp)
                }
                androidx.compose.material3.IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Android settings", tint = Color.LightGray)
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier.background(TomSurface, CircleShape).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.background(
                        TomAccent.copy(alpha = if (isListening) 0.28f else 0.14f), CircleShape
                    ).padding(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Microphone", tint = TomAccent)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Hello, I'm Tom.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(status, color = if (isListening) TomAccent else Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onLanguage("bn-BD") }) {
                    Text(if (language == "bn-BD") "✓ বাংলা" else "বাংলা")
                }
                OutlinedButton(onClick = { onLanguage("en-US") }) {
                    Text(if (language == "en-US") "✓ English" else "English")
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (isListening) onStop else onListen,
                colors = ButtonDefaults.buttonColors(containerColor = TomAccent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = TomBackground)
                Text(
                    if (isListening) "  Stop listening" else "  Tap to speak",
                    color = TomBackground,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBackgroundStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = language == "bn-BD" && modelInstalled
            ) {
                Text("Start background listening • Tom prefix required")
            }
            OutlinedButton(
                onClick = onBackgroundStop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop background listening")
            }
            Text(
                "এটি দৃশ্যমান notification সহ চালু হবে। ব্যাকগ্রাউন্ডে শুধু বাংলা local ASR চলে; কমান্ডের আগে “Tom …” বলতে হবে এবং Phone Control আলাদা করে Enable থাকতে হবে।",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(18.dp))
            InfoCard("YOU SAID", recognizedText)
            Spacer(Modifier.height(10.dp))
            InfoCard("TOM REPLIED", responseText)
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TomSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("PHONE CONTROL", color = TomAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "VOICE COMMAND GUIDE • Offline phone controls\n\nNAVIGATION: “Back / পিছনে যাও” = previous screen; “Home / হোমে যাও” = Home screen; “Recent apps” = app switcher; “Notifications” = notification shade; “Quick settings” = Wi-Fi, Bluetooth and other quick tiles; “Power menu” = Android power menu; “Lock screen” = lock phone; “Screenshot” = capture screen (Android 9+).\n\nVOLUME: “Volume up / ভলিউম বাড়াও”; “Volume down / ভলিউম কমাও”; “Mute / শব্দ বন্ধ কর”; “Unmute / শব্দ চালু কর”. These adjust media volume.\n\nSETTINGS SHORTCUTS: “Wi-Fi settings” = connect/manage Wi-Fi; “Bluetooth settings” = pair devices; “Display settings” = brightness, screen timeout and display; “Sound settings” = ringtone and sound; “Battery settings” = battery saver; “App settings” = installed apps and permissions; “Location settings” = location access; “Security settings” = device security; “Storage settings” = storage use; “Date and time settings” = clock/time zone; “Language settings” = device language; “Notification settings” = notification controls; “Hotspot settings” = hotspot/tethering; “Airplane mode settings” = airplane mode page.\n\nAPPS: “Open [app name]” = launch an installed app; “Open camera / ক্যামেরা খোলো”, “Open calculator”, “Open clock”, “Open calendar”, “Open contacts”, “Open phone” = launch the matching app if installed.\n\nIMPORTANT: Android/MIUI may require you to confirm restricted changes inside Settings. Tom opens the relevant settings page; it does not secretly toggle protected Wi-Fi, Bluetooth, mobile data, GPS or Airplane mode. Calls/messages, PIN/OTP, payments and security changes are not automated. Commands work only while Tom Phone Control is enabled by you in Accessibility settings.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Tom Phone Control")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TomSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("VOICE ENGINE", color = TomAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(offlineLabel, color = Color.LightGray, fontSize = 12.sp)
                    Text("বাংলা model install হলে recognition সম্পূর্ণ ফোনে চলে। English শুধু Android on-device recognizer ব্যবহার করে; offline language pack না থাকলে কাজ করবে না। Background listening, wake word এবং voice verification এখনো যোগ হয়নি।", color = Color.Gray, fontSize = 11.sp)
                }
            }
            if (language.startsWith("bn")) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDownloadModel,
                    enabled = !modelInstalled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (modelInstalled) "বাংলা Offline Model Ready" else "Download Bengali Offline Model • ~88 MB")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Tom • Native Android • No WebView", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@androidx.compose.runtime.Composable
private fun InfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TomSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = TomAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Color.White, fontSize = 14.sp)
        }
    }
}
