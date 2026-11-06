package com.tom.voicecontrol

import android.content.Context

/**
 * Persists the user's explicit awake/sleep preference.
 * This state does not claim to detect a wake word while the app is asleep.
 */
class TomVoiceStateManager(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("tom_voice_state", Context.MODE_PRIVATE)

    fun isAwake(): Boolean = preferences.getBoolean(KEY_AWAKE, true)

    fun setAwake(awake: Boolean) {
        preferences.edit().putBoolean(KEY_AWAKE, awake).apply()
    }

    companion object {
        private const val KEY_AWAKE = "voice_awake"
    }
}
