package com.tom.voicecontrol

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Explicit, user-started foreground microphone service.
 * It listens only with the installed local Bengali ASR model. Commands are
 * ignored unless the recognized phrase begins with the spoken prefix "Tom".
 */
class TomBackgroundVoiceService : Service() {
    companion object {
        const val ACTION_START = "com.tom.voicecontrol.action.START_BACKGROUND_VOICE"
        const val ACTION_STOP = "com.tom.voicecontrol.action.STOP_BACKGROUND_VOICE"
        private const val CHANNEL_ID = "tom_background_voice"
        private const val NOTIFICATION_ID = 4107
        private const val PREFIX_EN = "tom "
        private const val PREFIX_BN = "টম "
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: OfflineBengaliSpeechEngine? = null
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListeningAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startListening()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (engine != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val localEngine = OfflineBengaliSpeechEngine(
            context = applicationContext,
            onStatus = { message ->
                updateNotification(if (message.contains("শুনছি")) {
                    "Microphone active • Bengali speech processed on this device"
                } else {
                    message
                })
            },
            onText = { text -> handleRecognizedPhrase(text) },
            onError = { message ->
                updateNotification("Listening stopped: $message")
                mainHandler.postDelayed({ stopListeningAndSelf() }, 1200L)
            }
        )

        if (!localEngine.isModelInstalled()) {
            stopSelf()
            return
        }

        engine = localEngine
        stopping = false
        val notification = buildNotification("Starting local Bengali listening…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            localEngine.startListening()
        } catch (_: SecurityException) {
            stopListeningAndSelf()
        } catch (_: Exception) {
            stopListeningAndSelf()
        }
    }

    private fun handleRecognizedPhrase(raw: String) {
        val phrase = raw.trim()
        val lower = phrase.lowercase()
        val command = when {
            lower.startsWith(PREFIX_EN) -> phrase.substring(PREFIX_EN.length).trim()
            phrase.startsWith(PREFIX_BN) -> phrase.removePrefix(PREFIX_BN).trim()
            else -> return
        }
        if (command.isBlank() || !TomAccessibilityService.isEnabled(this)) return
        if (!TomAccessibilityService.matchesCommand(command)) return

        mainHandler.post {
            if (stopping) return@post
            TomAccessibilityService.executeCommand(command, isBangla = true)
            updateNotification("Tom command received • Local phone control")
        }
    }

    private fun stopListeningAndSelf() {
        if (stopping) return
        stopping = true
        engine?.stopListening()
        engine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tom background voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible status for user-started local microphone listening"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, TomBackgroundVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            4108,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                this,
                4109,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Tom is listening")
            .setContentText(message)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopPendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        if (stopping) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        stopping = true
        engine?.release()
        engine = null
        super.onDestroy()
    }
}
