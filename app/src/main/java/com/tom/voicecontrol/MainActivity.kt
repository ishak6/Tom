package com.tom.voicecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_MISSING_DATA
import android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
import android.speech.tts.UtteranceProgressListener
import android.provider.Settings
import android.provider.AlarmClock
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
    private var recentCommands by mutableStateOf(listOf<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recentCommands = getSharedPreferences("tom_local_history", MODE_PRIVATE).getString("commands", "").orEmpty().split("\n").filter { it.isNotBlank() }.take(10)
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
                    // A final offline-ASR utterance must close the microphone before TTS.
                    // Otherwise Tom can hear its own spoken answer and execute it again.
                    offlineBengaliEngine?.stopListening()
                    isListening = false
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
                    recentCommands = recentCommands,
                    onDownloadModel = { offlineBengaliEngine?.downloadModel() },
                    onListen = {
                        if (hasMicPermission) startVoiceInput()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { stopVoiceInput() },
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
        } + if (onDeviceAvailable) " • Android on-device service available for fallback" else ""
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            isListening = false
            status = "English offline speech service is unavailable. Install an offline English speech pack in Android voice settings."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "এই ডিভাইসে speech recognition service নেই"
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
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                        status = "English offline language pack unavailable. Install the language pack in Android voice settings, then retry."
                    } else {
                        status = "Speech error: ${speechErrorText(error)}"
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
                // English recognition must remain on-device; never silently fall back to network.
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
        recentCommands = (listOf(raw.trim()) + recentCommands).filter { it.isNotBlank() }.distinct().take(10)
        getSharedPreferences("tom_local_history", MODE_PRIVATE).edit().putString("commands", recentCommands.joinToString("\n")).apply()
        // Select response language from the recognized utterance, not only the UI preference.
        val containsBanglaScript = raw.any { it in '\u0980'..'\u09FF' }
        val isBangla = containsBanglaScript || selectedLanguage.startsWith("bn")
        val command = raw.lowercase(Locale.ROOT)
            .replace('০', '0').replace('১', '1').replace('২', '2').replace('৩', '3')
            .replace('৪', '4').replace('৫', '5').replace('৬', '6').replace('৭', '7')
            .replace('৮', '8').replace('৯', '9')
            .replace(Regex("[\\p{Punct}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("tom ")
            .removePrefix("টম ")
            .trim()

        // Handle the general Android Settings intent before generic app-launch routing.
        // "open settings" and "সেটিংস খোলো" also match the app-launch patterns.
        val settingsCommands = setOf("settings", "open settings", "সেটিংস", "সেটিংস খোলো", "ফোনের সেটিংস খোলো")
        if (command in settingsCommands) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            val answer = if (isBangla) "ফোনের সেটিংস খুলছি।" else "Opening Android settings."
            responseText = answer
            status = "Local command processed"
            speak(answer)
            return
        }

        // Phone-control routing is deliberately checked only after ASR text is normalized.
        if (TomAccessibilityService.matchesCommand(command)) {
            val phoneControlEnabled = TomAccessibilityService.isEnabled(this)
            val phoneControlAnswer = if (!phoneControlEnabled) {
                if (isBangla) {
                    "ফোন নিয়ন্ত্রণ ব্যবহার করতে Settings → Accessibility → Tom Phone Control নিজে চালু করুন।"
                } else {
                    "To use phone control, enable Tom Phone Control in Android Accessibility settings."
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

        // Delegate alarm and timer setup to Android Clock with visible confirmation UI.
        val normalizedDigits = command
            .replace('০', '0').replace('১', '1').replace('২', '2').replace('৩', '3')
            .replace('৪', '4').replace('৫', '5').replace('৬', '6').replace('৭', '7')
            .replace('৮', '8').replace('৯', '9')
        val timerRequest = normalizedDigits.contains("timer") || normalizedDigits.contains("টাইমার")
        val alarmRequest = normalizedDigits.contains("alarm") || normalizedDigits.contains("অ্যালার্ম") || normalizedDigits.contains("এলার্ম")
        if (timerRequest) {
            val duration = Regex("(\\d{1,3})\\s*(seconds?|secs?|সেকেন্ড|minutes?|mins?|মিনিট|ঘণ্টা|ঘন্টা|hours?)").find(normalizedDigits)
            if (duration != null) {
                val amount = duration.groupValues[1].toIntOrNull() ?: 0
                val unit = duration.groupValues[2]
                val seconds = when {
                    unit.startsWith("সেকেন্ড") || unit.startsWith("second") || unit.startsWith("sec") -> amount
                    unit.startsWith("ঘ") || unit.startsWith("hour") -> amount * 3600
                    else -> amount * 60
                }
                if (seconds in 1..86400) {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Tom timer")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        val answer = if (isBangla) "$amount $unit-এর টাইমার সেট করার Clock খুলছি।" else "Opening Clock to set a $amount $unit timer."
                        responseText = answer; status = "Timer request sent to Clock"; speak(answer)
                    } else { status = if (isBangla) "Clock অ্যাপ পাওয়া যায়নি।" else "No Clock app is available." }
                    return
                }
            }
            val answer = if (isBangla) "সময় উল্লেখ করে বলুন, যেমন: ৫ মিনিটের টাইমার দাও।" else "Specify a duration, for example: set a timer for 5 minutes."
            responseText = answer; status = "Timer duration needed"; speak(answer); return
        }
        if (alarmRequest) {
            val match = Regex("(\\d{1,2})(?:\\s+(\\d{2}))?").find(normalizedDigits)
            if (match != null) {
                val rawHour = match.groupValues[1].toIntOrNull() ?: -1
                val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: -1
                val pm = normalizedDigits.contains("pm") || normalizedDigits.contains("বিকাল") || normalizedDigits.contains("সন্ধ্যা") || normalizedDigits.contains("রাত")
                val am = normalizedDigits.contains("am") || normalizedDigits.contains("সকাল") || normalizedDigits.contains("ভোর")
                val hour = if (pm && rawHour in 1..11) rawHour + 12 else if (am && rawHour == 12) 0 else rawHour
                if (hour in 0..23 && minute in 0..59) {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour); putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Tom alarm"); putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        val answer = if (isBangla) "Clock-এ অ্যালার্ম সেট করার স্ক্রিন খুলছি।" else "Opening Clock to set your alarm."
                        responseText = answer; status = "Alarm request sent to Clock"; speak(answer)
                    } else { status = if (isBangla) "Clock অ্যাপ পাওয়া যায়নি।" else "No Clock app is available." }
                    return
                }
            }
            val answer = if (isBangla) "সময় উল্লেখ করে বলুন, যেমন: সকাল ৭:৩০-এ অ্যালার্ম দাও।" else "Specify a time, for example: set an alarm for 7:30 AM."
            responseText = answer; status = "Alarm time needed"; speak(answer); return
        }

        val greetingCommands = setOf("hello", "hi", "hey", "হ্যালো", "হাই", "সালাম", "আসসালামু আলাইকুম")
        val timeCommands = setOf(
            "time", "what time is it", "what is the time", "tell me the time", "current time", "time please",
            "সময়", "সময়", "সময় কত", "সময় কত", "এখন সময় কত", "এখন সময় কত",
            "এখন কয়টা বাজে", "এখন কয়টা বাজে", "কয়টা বাজে", "কয়টা বাজে", "কটা বাজে",
            "সময় বলো", "সময় বলো", "বর্তমান সময় বলো", "বর্তমান সময় বলো"
        )
        val dateCommands = setOf(
            "date", "today date", "what is the date", "what is today's date",
            "আজকের তারিখ", "আজ কত তারিখ", "আজকের তারিখ কত", "তারিখ বলো"
        )
        val batteryCommands = setOf(
            "battery", "battery level", "how much battery", "what is my battery level",
            "ব্যাটারি", "ব্যাটারি কত", "চার্জ কত", "ফোনে কত চার্জ আছে", "ব্যাটারির অবস্থা বলো"
        )
        val helpCommands = setOf("help", "what can you do", "কী করতে পারো", "কি করতে পারো", "তুমি কী করতে পারো", "তুমি কি করতে পারো", "সাহায্য")
        val identityCommands = setOf("who are you", "what is your name", "তোমার নাম কী", "তোমার নাম কি", "তুমি কে")
        val stopCommands = setOf("stop", "বন্ধ কর", "থামো", "চুপ কর", "থেমে যাও")

        val answer = when {
            command in greetingCommands ->
                if (isBangla) "হ্যালো! আমি টম। কীভাবে সাহায্য করতে পারি?" else "Hello! I'm Tom. How can I help?"
            command in timeCommands -> {
                val now = DateFormat.getTimeInstance(
                    DateFormat.SHORT,
                    if (isBangla) Locale("bn", "BD") else Locale.US
                ).format(Date())
                if (isBangla) "এখন সময় $now" else "The time is $now"
            }
            command in dateCommands -> {
                val today = DateFormat.getDateInstance(
                    DateFormat.LONG,
                    if (isBangla) Locale("bn", "BD") else Locale.US
                ).format(Date())
                if (isBangla) "আজ $today" else "Today is $today"
            }
            command in batteryCommands -> {
                val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
                val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level in 0..100) {
                    if (isBangla) "তোমার ফোনের ব্যাটারি $level শতাংশ।" else "Your phone battery is at $level percent."
                } else {
                    if (isBangla) "ব্যাটারির তথ্য পাওয়া যাচ্ছে না।" else "Battery information is unavailable."
                }
            }
            command in helpCommands ->
                if (isBangla) {
                    "আমি সময়, তারিখ, ব্যাটারি, অ্যালার্ম, টাইমার, ফোনের সেটিংস, অ্যাপ খোলা এবং ফোনের নেভিগেশন কমান্ডে সাহায্য করতে পারি। অন্য প্রশ্নের জন্য ইন্টারনেট বা AI সংযোগ প্রয়োজন।"
                } else {
                    "I can help with time, date, battery, alarms, timers, settings, opening apps, and phone navigation. General questions need an internet or AI connection."
                }
            command in identityCommands ->
                if (isBangla) "আমি টম, তোমার ফোনের ভয়েস অ্যাসিস্ট্যান্ট।" else "I'm Tom, your phone voice assistant."
            command in settingsCommands -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                if (isBangla) "ফোনের সেটিংস খুলছি।" else "Opening Android settings."
            }
            command in stopCommands -> {
                textToSpeech?.stop()
                if (isBangla) "ঠিক আছে।" else "Okay."
            }
            command.isBlank() ->
                if (isBangla) "কথা পরিষ্কারভাবে শনাক্ত হয়নি। আরেকবার বলো।" else "I couldn't recognize that clearly. Please try again."
            else -> if (isBangla) {
                "আমি শুনেছি: $raw। এই কাজটি এখনো যোগ করা হয়নি। 'সাহায্য' বললে কী কী করতে পারি জানাব।"
            } else {
                "I heard: $raw. That action is not implemented yet. Say 'help' to hear what I can do."
            }
        }
        responseText = answer
        status = "কমান্ড যাচাই সম্পন্ন • Local command processed"
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
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language unavailable (install the English speech language pack or use system fallback)"
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
    recentCommands: List<String>,
    onDownloadModel: () -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit,
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
            Spacer(Modifier.height(18.dp))
            InfoCard("YOU SAID", recognizedText)
            Spacer(Modifier.height(10.dp))
            InfoCard("TOM REPLIED", responseText)
            Spacer(Modifier.height(10.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TomSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("RECENT COMMANDS • LOCAL ONLY", color = TomAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    if (recentCommands.isEmpty()) Text("এখনো কোনো কমান্ড নেই।", color = Color.LightGray, fontSize = 12.sp)
                    else recentCommands.forEachIndexed { index, item -> Text("${index + 1}. $item", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
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
                    Text("বাংলা model একবার download করলে speech recognition ফোনেই চলে; পরে internet লাগে না। English এখনও Android speech service ব্যবহার করে।", color = Color.Gray, fontSize = 11.sp)
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
