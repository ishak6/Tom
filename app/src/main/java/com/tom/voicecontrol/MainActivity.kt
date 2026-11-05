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
                    onListen = {
                        if (hasMicPermission) startVoiceInput()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { stopVoiceInput() },
                    onLanguage = { selectedLanguage = it; updateSpeechAvailability() },
                    onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) }
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
        offlineModeLabel = when {
            onDeviceAvailable -> "On-device speech recognition available"
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                "Offline mode requested; Android speech service may use network"
            else -> "On-device model unavailable; install a supported offline speech service"
        }
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "এই ডিভাইসে speech recognition service নেই"
            return
        }
        if (isListening) stopVoiceInput()

        try {
            speechRecognizer?.destroy()
            speechRecognizer = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

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
                    status = "Speech error: ${speechErrorText(error)}"
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

    private fun stopVoiceInput() {
        speechRecognizer?.cancel()
        isListening = false
        status = "শোনা বন্ধ • Listening stopped"
    }

    private fun speechErrorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network/service"
        SpeechRecognizer.ERROR_NO_MATCH -> "no speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        else -> "code $error"
    }

    override fun onDestroy() {
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
    onListen: () -> Unit,
    onStop: () -> Unit,
    onLanguage: (String) -> Unit,
    onSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = TomBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp),
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TomSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("VOICE ENGINE", color = TomAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(offlineLabel, color = Color.LightGray, fontSize = 12.sp)
                    Text("Offline support depends on the Android speech service and installed language models. TTS voice data may need a one-time download.", color = Color.Gray, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.weight(1f))
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
