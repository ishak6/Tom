package com.tom.voicecontrol

import java.util.Locale

/**
 * Deterministic command harness.
 *
 * Recognition output is never treated as an instruction by substring matching.
 * A command must normalize to a registered alias or an explicit app-launch phrase.
 */
object TomCommandHarness {
    enum class Intent {
        BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_MENU, LOCK_SCREEN,
        VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE, WIFI_SETTINGS, BLUETOOTH_SETTINGS,
        DISPLAY_SETTINGS, SOUND_SETTINGS, BATTERY_SETTINGS, APP_SETTINGS,
        LOCATION_SETTINGS, SECURITY_SETTINGS, STORAGE_SETTINGS, DATE_TIME_SETTINGS,
        LANGUAGE_SETTINGS, NOTIFICATION_SETTINGS, HOTSPOT_SETTINGS,
        AIRPLANE_SETTINGS, CAMERA, CALCULATOR, CLOCK, CALENDAR, CONTACTS, PHONE,
        SCREENSHOT, GREETING, TIME, GENERAL_SETTINGS, STOP, OPEN_APP
    }

    data class ParsedCommand(
        val intent: Intent,
        val argument: String? = null
    )

    private val aliases: Map<Intent, Set<String>> = mapOf(
        Intent.BACK to setOf("back", "go back", "পিছনে যাও", "পেছনে যাও", "ব্যাক"),
        Intent.HOME to setOf("home", "go home", "home screen", "হোম", "হোমে যাও"),
        Intent.RECENTS to setOf("recent apps", "show recent", "recents", "সাম্প্রতিক অ্যাপ", "রিসেন্ট অ্যাপ"),
        Intent.NOTIFICATIONS to setOf("notifications", "open notifications", "নোটিফিকেশন", "নোটিফিকেশন দেখাও"),
        Intent.QUICK_SETTINGS to setOf("quick settings", "কুইক সেটিংস"),
        Intent.POWER_MENU to setOf("power menu", "পাওয়ার মেনু", "পাওয়ার মেনু"),
        Intent.LOCK_SCREEN to setOf("lock screen", "লক স্ক্রিন"),
        Intent.VOLUME_UP to setOf("volume up", "increase volume", "ভলিউম বাড়াও", "শব্দ বাড়াও"),
        Intent.VOLUME_DOWN to setOf("volume down", "decrease volume", "ভলিউম কমাও", "শব্দ কমাও"),
        Intent.MUTE to setOf("mute", "mute volume", "শব্দ বন্ধ কর", "মিউট"),
        Intent.UNMUTE to setOf("unmute", "শব্দ চালু কর", "আনমিউট"),
        Intent.WIFI_SETTINGS to setOf("wifi settings", "ওয়াইফাই সেটিংস", "ওয়াইফাই সেটিংস"),
        Intent.BLUETOOTH_SETTINGS to setOf("bluetooth settings", "ব্লুটুথ সেটিংস"),
        Intent.DISPLAY_SETTINGS to setOf("display settings", "screen settings", "ডিসপ্লে সেটিংস", "স্ক্রিন সেটিংস"),
        Intent.SOUND_SETTINGS to setOf("sound settings", "audio settings", "সাউন্ড সেটিংস"),
        Intent.BATTERY_SETTINGS to setOf("battery settings", "ব্যাটারি সেটিংস"),
        Intent.APP_SETTINGS to setOf("app settings", "apps settings", "অ্যাপ সেটিংস"),
        Intent.LOCATION_SETTINGS to setOf("location settings", "লোকেশন সেটিংস"),
        Intent.SECURITY_SETTINGS to setOf("security settings", "নিরাপত্তা সেটিংস"),
        Intent.STORAGE_SETTINGS to setOf("storage settings", "স্টোরেজ সেটিংস"),
        Intent.DATE_TIME_SETTINGS to setOf("date and time settings", "তারিখ সময় সেটিংস", "তারিখ ও সময় সেটিংস"),
        Intent.LANGUAGE_SETTINGS to setOf("language settings", "ভাষা সেটিংস"),
        Intent.NOTIFICATION_SETTINGS to setOf("notification settings", "নোটিফিকেশন সেটিংস"),
        Intent.HOTSPOT_SETTINGS to setOf("hotspot settings", "হটস্পট সেটিংস"),
        Intent.AIRPLANE_SETTINGS to setOf("airplane mode settings", "এয়ারপ্লেন মোড সেটিংস"),
        Intent.CAMERA to setOf("open camera", "ক্যামেরা খোলো"),
        Intent.CALCULATOR to setOf("open calculator", "ক্যালকুলেটর খোলো"),
        Intent.CLOCK to setOf("open clock", "ঘড়ি খোলো", "ঘড়ি খোলো"),
        Intent.CALENDAR to setOf("open calendar", "ক্যালেন্ডার খোলো"),
        Intent.CONTACTS to setOf("open contacts", "কন্টাক্ট খোলো"),
        Intent.PHONE to setOf("open phone", "ফোন খোলো"),
        Intent.SCREENSHOT to setOf("take screenshot", "screenshot", "স্ক্রিনশট নাও"),
        Intent.GREETING to setOf("hello", "hi", "হ্যালো", "হাই", "সালাম", "আসসালামু আলাইকুম"),
        Intent.TIME to setOf("time", "what time is it", "সময়", "সময়", "কয়টা", "কয়টা বাজে", "কটা বাজে", "এখন কয়টা বাজে", "এখন সময় কত"),
        Intent.GENERAL_SETTINGS to setOf("settings", "open settings", "সেটিংস", "সেটিং", "সেটিংস খোলো"),
        Intent.STOP to setOf("stop", "বন্ধ কর", "থামো")
    )

    private val appPrefixes = listOf("open ", "launch ", "অ্যাপ খোলো ", "অ্যাপ খুলে দাও ")
    private val appSuffixes = listOf(" খোলো", " খুলে দাও")

    fun normalize(input: String): String = input
        .lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("[\\p{Punct}।!?]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun parse(input: String): ParsedCommand? {
        val value = normalize(input)
        if (value.isBlank()) return null

        aliases.forEach { (intent, phrases) ->
            if (value in phrases.map(::normalize).toSet()) return ParsedCommand(intent)
        }

        val prefix = appPrefixes.firstOrNull { value.startsWith(it) }
        if (prefix != null) {
            val app = value.removePrefix(prefix).trim()
            return app.takeIf { it.isNotBlank() }?.let { ParsedCommand(Intent.OPEN_APP, it) }
        }
        val suffix = appSuffixes.firstOrNull { value.endsWith(it) }
        if (suffix != null) {
            val app = value.removeSuffix(suffix).trim()
            if (app.isNotBlank() && app !in setOf("ক্যামেরা", "ক্যালকুলেটর", "ক্যালেন্ডার", "কন্টাক্ট", "ফোন", "ঘড়ি")) {
                return ParsedCommand(Intent.OPEN_APP, app)
            }
        }
        return null
    }

    fun isPhoneCommand(input: String): Boolean {
        val parsed = parse(input) ?: return false
        return parsed.intent !in setOf(Intent.GREETING, Intent.TIME, Intent.GENERAL_SETTINGS, Intent.STOP)
    }

    fun isSupportedAssistantCommand(input: String): Boolean = parse(input) != null
}
