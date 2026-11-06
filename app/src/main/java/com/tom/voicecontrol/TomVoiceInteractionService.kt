package com.tom.voicecontrol

import android.service.voice.VoiceInteractionService

/**
 * Entry point registered with Android's assistant role.
 * This establishes assistant eligibility; it does not claim to implement wake-word
 * detection or continuous microphone capture.
 */
class TomVoiceInteractionService : VoiceInteractionService()
