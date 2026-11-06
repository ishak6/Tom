package com.tom.voicecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TomResourceManagerTest {
    @Test
    fun lowRamDeviceAlwaysUsesLowMemoryMode() {
        assertTrue(TomResourcePolicy.isLowMemory(true, 2L * 1024 * 1024 * 1024, 384L * 1024 * 1024))
    }

    @Test
    fun lowAvailableMemoryEnablesLowMemoryMode() {
        assertTrue(TomResourcePolicy.isLowMemory(false, 200L * 1024 * 1024, 384L * 1024 * 1024))
    }

    @Test
    fun healthyMemoryKeepsNormalMode() {
        assertFalse(TomResourcePolicy.isLowMemory(false, 1024L * 1024 * 1024, 384L * 1024 * 1024))
    }

    @Test
    fun lowMemoryUsesOneAsrThreadAndNormalModeUsesTwo() {
        assertEquals(1, TomResourcePolicy.recommendedAsrThreads(true))
        assertEquals(2, TomResourcePolicy.recommendedAsrThreads(false))
    }

    @Test
    fun modelInstallRequiresMinimumFreeStorage() {
        val minimum = 350L * 1024 * 1024
        assertTrue(TomResourcePolicy.hasEnoughModelInstallSpace(minimum, minimum))
        assertFalse(TomResourcePolicy.hasEnoughModelInstallSpace(minimum - 1, minimum))
    }
}
