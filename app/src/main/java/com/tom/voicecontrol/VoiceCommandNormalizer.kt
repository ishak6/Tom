package com.tom.voicecontrol

import java.util.Locale

/**
 * Pure, deterministic normalization shared by Bengali and English local commands.
 * Keeps letters and digits, converts Bengali numerals to ASCII, and removes
 * punctuation so speech-recognition variants match the same command phrase.
 */
object VoiceCommandNormalizer {
    private val bengaliDigits = mapOf(
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
    )

    fun normalize(raw: String): String {
        val digitsNormalized = buildString(raw.length) {
            raw.forEach { append(bengaliDigits[it] ?: it) }
        }
        return digitsNormalized.lowercase(Locale.ROOT)
            .replace(Regex("[\\p{P}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("tom ")
            .removePrefix("টম ")
            .trim()
    }
}
