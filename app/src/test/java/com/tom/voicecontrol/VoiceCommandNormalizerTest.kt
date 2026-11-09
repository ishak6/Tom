package com.tom.voicecontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandNormalizerTest {
    @Test
    fun convertsBengaliDigitsToAscii() {
        assertEquals("5 মিনিটের টাইমার দাও", VoiceCommandNormalizer.normalize("৫ মিনিটের টাইমার দাও"))
    }

    @Test
    fun removesPunctuationAndCollapsesWhitespace() {
        assertEquals("এখন সময় কত", VoiceCommandNormalizer.normalize("এখন,   সময় কত?"))
    }

    @Test
    fun removesEnglishWakePrefix() {
        assertEquals("open settings", VoiceCommandNormalizer.normalize("Tom, open settings!"))
    }

    @Test
    fun removesBengaliNamePrefix() {
        assertEquals("সময় কত", VoiceCommandNormalizer.normalize("টম, সময় কত?"))
    }

    @Test
    fun preservesUnknownUtteranceForHelpfulFallback() {
        assertEquals("আমাকে গান শোনাও", VoiceCommandNormalizer.normalize("আমাকে গান শোনাও"))
    }
}
