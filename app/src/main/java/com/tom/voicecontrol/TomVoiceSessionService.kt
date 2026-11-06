package com.tom.voicecontrol

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Minimal native Android voice-interaction session.
 * Conversation UI and speech capture are connected in a later integration step.
 */
class TomVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return VoiceInteractionSession(this)
    }
}
