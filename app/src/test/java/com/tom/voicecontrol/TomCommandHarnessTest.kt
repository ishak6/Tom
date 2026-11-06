package com.tom.voicecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TomCommandHarnessTest {
    @Test fun acceptsRegisteredEnglishAndBanglaAliases() {
        assertEquals(TomCommandHarness.Intent.BACK, TomCommandHarness.parse("Go Back")?.intent)
        assertEquals(TomCommandHarness.Intent.HOME, TomCommandHarness.parse("হোমে যাও।")?.intent)
        assertEquals(TomCommandHarness.Intent.VOLUME_UP, TomCommandHarness.parse("ভলিউম বাড়াও")?.intent)
        assertEquals(TomCommandHarness.Intent.WIFI_SETTINGS, TomCommandHarness.parse("WiFi Settings")?.intent)
    }

    @Test fun rejectsSubstringFalsePositives() {
        assertNull(TomCommandHarness.parse("sometimes"))
        assertNull(TomCommandHarness.parse("I am going back tomorrow"))
        assertNull(TomCommandHarness.parse("homework"))
        assertNull(TomCommandHarness.parse("openly"))
        assertFalse(TomCommandHarness.isPhoneCommand("please go back when you can"))
    }

    @Test fun appLaunchRequiresExplicitVerbOrSuffix() {
        val command = TomCommandHarness.parse("open youtube")
        assertNotNull(command)
        assertEquals(TomCommandHarness.Intent.OPEN_APP, command?.intent)
        assertEquals("youtube", command?.argument)
        assertNull(TomCommandHarness.parse("youtube"))
    }

    @Test fun classifiesAssistantCommandsSeparatelyFromPhoneCommands() {
        assertEquals(TomCommandHarness.Intent.TIME, TomCommandHarness.parse("কয়টা বাজে")?.intent)
        assertTrue(TomCommandHarness.isSupportedAssistantCommand("hello"))
        assertFalse(TomCommandHarness.isPhoneCommand("hello"))
    }

    @Test fun blankInputIsIgnored() {
        assertNull(TomCommandHarness.parse("   "))
    }
}
