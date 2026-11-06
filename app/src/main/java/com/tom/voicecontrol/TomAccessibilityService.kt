package com.tom.voicecontrol

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

/**
 * User-enabled accessibility bridge for local, device-level navigation commands.
 * It does not inspect screen contents, capture text, or perform gestures.
 */
class TomAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun launchInstalledApp(requestedName: String): Boolean {
        val query = requestedName.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return false

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val launchables = packageManager.queryIntentActivities(launcherIntent, 0)

        val match = launchables.firstOrNull { info ->
            info.loadLabel(packageManager).toString().lowercase(Locale.ROOT) == query
        } ?: launchables.firstOrNull { info ->
            info.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(query)
        } ?: return false

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        private var activeService: TomAccessibilityService? = null

        fun isEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, TomAccessibilityService::class.java)
            return enabledServices.split(':').any { flattened ->
                ComponentName.unflattenFromString(flattened.trim()) == expected
            }
        }

        fun matchesCommand(command: String): Boolean {
            val value = command.lowercase(Locale.ROOT).trim()
            val actionPhrases = listOf(
                "go back", "back", "পিছনে যাও", "পেছনে যাও", "ব্যাক",
                "go home", "home screen", "home", "হোমে যাও", "হোম",
                "recent apps", "show recent", "recents", "সাম্প্রতিক অ্যাপ", "রিসেন্ট অ্যাপ",
                "notifications", "open notifications", "নোটিফিকেশন", "নোটিফিকেশন দেখাও",
                "quick settings", "কুইক সেটিংস",
                "power menu", "পাওয়ার মেনু", "পাওয়ার মেনু",
                "lock screen", "লক স্ক্রিন",
                "volume up", "increase volume", "ভলিউম বাড়াও", "শব্দ বাড়াও",
                "volume down", "decrease volume", "ভলিউম কমাও", "শব্দ কমাও",
                "mute volume", "শব্দ বন্ধ কর", "মিউট",
                "unmute", "শব্দ চালু কর", "আনমিউট",
                "wifi settings", "ওয়াইফাই সেটিংস", "ওয়াইফাই সেটিংস",
                "bluetooth settings", "ব্লুটুথ সেটিংস",
                "display settings", "screen settings", "ডিসপ্লে সেটিংস", "স্ক্রিন সেটিংস",
                "sound settings", "audio settings", "সাউন্ড সেটিংস",
                "battery settings", "ব্যাটারি সেটিংস",
                "app settings", "apps settings", "অ্যাপ সেটিংস",
                "location settings", "লোকেশন সেটিংস",
                "security settings", "নিরাপত্তা সেটিংস",
                "storage settings", "স্টোরেজ সেটিংস",
                "date and time settings", "তারিখ সময় সেটিংস",
                "language settings", "ভাষা সেটিংস",
                "notification settings", "নোটিফিকেশন সেটিংস",
                "hotspot settings", "হটস্পট সেটিংস",
                "airplane mode settings", "এয়ারপ্লেন মোড সেটিংস",
                "open camera", "ক্যামেরা খোলো", "open calculator", "ক্যালকুলেটর খোলো",
                "open clock", "ঘড়ি খোলো", "open calendar", "ক্যালেন্ডার খোলো",
                "open contacts", "কন্টাক্ট খোলো", "open phone", "ফোন খোলো",
                "take screenshot", "screenshot", "স্ক্রিনশট নাও"
            )
            return actionPhrases.any { value == it } ||
                value.startsWith("open ") ||
                value.startsWith("launch ") ||
                value.startsWith("অ্যাপ খোলো ") ||
                value.startsWith("অ্যাপ খুলে দাও ") ||
                value.endsWith(" খোলো") ||
                value.endsWith(" খুলে দাও") ||
                value.endsWith(" চালু কর")
        }

        private fun openSettings(
            service: TomAccessibilityService,
            action: String,
            bn: String,
            en: String,
            isBangla: Boolean
        ): String = try {
            service.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (isBangla) bn else en
        } catch (_: Exception) {
            if (isBangla) "এই সেটিংসটি এই ফোনে পাওয়া যায়নি।" else "That settings page is unavailable on this phone."
        }

        private fun launchByLabel(
            service: TomAccessibilityService,
            labels: List<String>,
            isBangla: Boolean
        ): String {
            val launched = labels.firstNotNullOfOrNull { label ->
                if (service.launchInstalledApp(label)) label else null
            }
            return if (launched != null) {
                if (isBangla) "$launched খুলছি।" else "Opening $launched."
            } else {
                if (isBangla) "এই অ্যাপটি ফোনে ইনস্টল নেই।" else "That app is not installed."
            }
        }

        fun executeCommand(command: String, isBangla: Boolean): String? {
            val service = activeService ?: return if (isBangla) {
                "Tom Phone Control সার্ভিসটি সক্রিয় নয়। Accessibility Settings-এ এটি চালু আছে কি না দেখুন।"
            } else {
                "Tom Phone Control is not active. Check that it is enabled in Accessibility settings."
            }
            val value = command.lowercase(Locale.ROOT).trim()

            fun done(bn: String, en: String) = if (isBangla) bn else en
            fun perform(action: Int, bn: String, en: String): String =
                if (service.performGlobalAction(action)) done(bn, en)
                else done("কমান্ডটি সম্পন্ন করা যায়নি।", "The action could not be completed.")

            return when {
                listOf("go back", "back", "পিছনে যাও", "পেছনে যাও", "ব্যাক").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_BACK, "এক ধাপ পিছনে যাচ্ছি।", "Going back.")
                listOf("go home", "home screen", "home", "হোমে যাও", "হোম").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_HOME, "হোম স্ক্রিনে যাচ্ছি।", "Going to the Home screen.")
                listOf("recent apps", "show recent", "recents", "সাম্প্রতিক অ্যাপ", "রিসেন্ট অ্যাপ").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recent Apps খুলছি।", "Opening Recent Apps.")
                listOf("notifications", "open notifications", "নোটিফিকেশন", "নোটিফিকেশন দেখাও").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "নোটিফিকেশন খুলছি।", "Opening notifications.")
                listOf("quick settings", "কুইক সেটিংস").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings খুলছি।", "Opening Quick Settings.")
                listOf("power menu", "পাওয়ার মেনু", "পাওয়ার মেনু").any { value == it } ->
                    perform(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "Power menu খুলছি।", "Opening the power menu.")
                listOf("volume up", "increase volume", "ভলিউম বাড়াও", "শব্দ বাড়াও").any { value == it } -> {
                    val audio = service.getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    done("ভলিউম বাড়াচ্ছি।", "Increasing media volume.")
                }
                listOf("volume down", "decrease volume", "ভলিউম কমাও", "শব্দ কমাও").any { value == it } -> {
                    val audio = service.getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    done("ভলিউম কমাচ্ছি।", "Decreasing media volume.")
                }
                listOf("mute volume", "শব্দ বন্ধ কর", "মিউট").any { value == it } -> {
                    val audio = service.getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    done("মিডিয়া শব্দ বন্ধ করছি।", "Muting media volume.")
                }
                listOf("unmute", "শব্দ চালু কর", "আনমিউট").any { value == it } -> {
                    val audio = service.getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    done("মিডিয়া শব্দ চালু করছি।", "Unmuting media volume.")
                }
                listOf("wifi settings", "ওয়াইফাই সেটিংস", "ওয়াইফাই সেটিংস").any { value == it } -> {
                    service.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    done("Wi-Fi সেটিংস খুলছি।", "Opening Wi-Fi settings.")
                }
                listOf("bluetooth settings", "ব্লুটুথ সেটিংস").any { value == it } -> {
                    service.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    done("Bluetooth সেটিংস খুলছি।", "Opening Bluetooth settings.")
                }
                listOf("display settings", "screen settings", "ডিসপ্লে সেটিংস", "স্ক্রিন সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_DISPLAY_SETTINGS, "ডিসপ্লে সেটিংস খুলছি।", "Opening Display settings.", isBangla)
                listOf("sound settings", "audio settings", "সাউন্ড সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_SOUND_SETTINGS, "সাউন্ড সেটিংস খুলছি।", "Opening Sound settings.", isBangla)
                listOf("battery settings", "ব্যাটারি সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_BATTERY_SAVER_SETTINGS, "ব্যাটারি সেটিংস খুলছি।", "Opening Battery settings.", isBangla)
                listOf("app settings", "apps settings", "অ্যাপ সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "অ্যাপ সেটিংস খুলছি।", "Opening App settings.", isBangla)
                listOf("location settings", "লোকেশন সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "লোকেশন সেটিংস খুলছি।", "Opening Location settings.", isBangla)
                listOf("security settings", "নিরাপত্তা সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_SECURITY_SETTINGS, "নিরাপত্তা সেটিংস খুলছি।", "Opening Security settings.", isBangla)
                listOf("storage settings", "স্টোরেজ সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "স্টোরেজ সেটিংস খুলছি।", "Opening Storage settings.", isBangla)
                listOf("date and time settings", "তারিখ সময় সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_DATE_SETTINGS, "তারিখ ও সময় সেটিংস খুলছি।", "Opening Date and time settings.", isBangla)
                listOf("language settings", "ভাষা সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_LOCALE_SETTINGS, "ভাষা সেটিংস খুলছি।", "Opening Language settings.", isBangla)
                listOf("notification settings", "নোটিফিকেশন সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_APP_NOTIFICATION_SETTINGS, "নোটিফিকেশন সেটিংস খুলছি।", "Opening Notification settings.", isBangla)
                listOf("hotspot settings", "হটস্পট সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_WIRELESS_SETTINGS, "Hotspot ও tethering সেটিংস খুলছি।", "Opening Hotspot and tethering settings.", isBangla)
                listOf("airplane mode settings", "এয়ারপ্লেন মোড সেটিংস").any { value == it } ->
                    openSettings(service, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode সেটিংস খুলছি।", "Opening Airplane mode settings.", isBangla)
                listOf("open camera", "ক্যামেরা খোলো").any { value == it } ->
                    openSettings(service, android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, "ক্যামেরা খুলছি।", "Opening Camera.", isBangla)
                listOf("open calculator", "ক্যালকুলেটর খোলো").any { value == it } ->
                    launchByLabel(service, listOf("calculator", "ক্যালকুলেটর"), isBangla)
                listOf("open clock", "ঘড়ি খোলো").any { value == it } ->
                    launchByLabel(service, listOf("clock", "ঘড়ি"), isBangla)
                listOf("open calendar", "ক্যালেন্ডার খোলো").any { value == it } ->
                    launchByLabel(service, listOf("calendar", "ক্যালেন্ডার"), isBangla)
                listOf("open contacts", "কন্টাক্ট খোলো").any { value == it } ->
                    launchByLabel(service, listOf("contacts", "কন্টাক্ট"), isBangla)
                listOf("open phone", "ফোন খোলো").any { value == it } ->
                    launchByLabel(service, listOf("phone", "dialer", "ফোন"), isBangla)
                listOf("take screenshot", "screenshot", "স্ক্রিনশট নাও").any { value == it } -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        perform(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "স্ক্রিনশট নেওয়ার চেষ্টা করছি।", "Taking a screenshot.")
                    } else done("এই Android সংস্করণে Screenshot action নেই।", "Screenshot action is unavailable.")
                }
                listOf("lock screen", "লক স্ক্রিন").any { value == it } -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "স্ক্রিন লক করছি।", "Locking the screen.")
                    } else {
                        done("এই Android সংস্করণে Lock Screen action নেই।", "Lock Screen is unavailable on this Android version.")
                    }
                }
                else -> {
                    val prefixes = listOf("open ", "launch ", "অ্যাপ খোলো ", "অ্যাপ খুলে দাও ")
                    var appName = prefixes.firstOrNull { value.startsWith(it) }
                        ?.let { value.removePrefix(it).trim() }
                    if (appName == null) {
                        appName = value
                            .removeSuffix(" খুলে দাও")
                            .removeSuffix(" খোলো")
                            .removeSuffix(" চালু কর")
                            .trim()
                    }
                    if (service.launchInstalledApp(appName)) {
                        done("$appName অ্যাপ খুলছি।", "Opening $appName.")
                    } else {
                        done("$appName নামে কোনো চালু করা যায় এমন অ্যাপ খুঁজে পাইনি।", "I couldn't find an installed app named $appName.")
                    }
                }
            }
        }
    }
}
