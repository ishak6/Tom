package com.tom.voicecontrol

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
                "lock screen", "লক স্ক্রিন"
            )
            return actionPhrases.any { value == it || value.contains(it) } ||
                value.startsWith("open ") ||
                value.startsWith("launch ") ||
                value.startsWith("অ্যাপ খোলো ") ||
                value.startsWith("অ্যাপ খুলে দাও ") ||
                value.endsWith(" খোলো") ||
                value.endsWith(" খুলে দাও") ||
                value.endsWith(" চালু কর")
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
                listOf("go back", "back", "পিছনে যাও", "পেছনে যাও", "ব্যাক").any(value::contains) ->
                    perform(GLOBAL_ACTION_BACK, "এক ধাপ পিছনে যাচ্ছি।", "Going back.")
                listOf("go home", "home screen", "home", "হোমে যাও", "হোম").any(value::contains) ->
                    perform(GLOBAL_ACTION_HOME, "হোম স্ক্রিনে যাচ্ছি।", "Going to the Home screen.")
                listOf("recent apps", "show recent", "recents", "সাম্প্রতিক অ্যাপ", "রিসেন্ট অ্যাপ").any(value::contains) ->
                    perform(GLOBAL_ACTION_RECENTS, "Recent Apps খুলছি।", "Opening Recent Apps.")
                listOf("notifications", "open notifications", "নোটিফিকেশন", "নোটিফিকেশন দেখাও").any(value::contains) ->
                    perform(GLOBAL_ACTION_NOTIFICATIONS, "নোটিফিকেশন খুলছি।", "Opening notifications.")
                listOf("quick settings", "কুইক সেটিংস").any(value::contains) ->
                    perform(GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings খুলছি।", "Opening Quick Settings.")
                listOf("power menu", "পাওয়ার মেনু", "পাওয়ার মেনু").any(value::contains) ->
                    perform(GLOBAL_ACTION_POWER_DIALOG, "Power menu খুলছি।", "Opening the power menu.")
                listOf("lock screen", "লক স্ক্রিন").any(value::contains) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        perform(GLOBAL_ACTION_LOCK_SCREEN, "স্ক্রিন লক করছি।", "Locking the screen.")
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
