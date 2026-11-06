package com.tom.voicecontrol

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Read-only resource telemetry and conservative policies for Tom.
 * Android remains the owner of system memory management; Tom only tunes its own work.
 */
object TomResourceManager {
    private const val LOW_MEMORY_THRESHOLD_BYTES = 384L * 1024L * 1024L
    private const val MIN_MODEL_INSTALL_SPACE_BYTES = 350L * 1024L * 1024L

    data class Snapshot(
        val totalRamBytes: Long,
        val availableRamBytes: Long,
        val appStorageAvailableBytes: Long,
        val isLowRamDevice: Boolean,
        val lowMemoryMode: Boolean,
        val recommendedAsrThreads: Int
    )

    fun snapshot(context: Context): Snapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val lowRamDevice = activityManager.isLowRamDevice
        val lowMemory = TomResourcePolicy.isLowMemory(
            isLowRamDevice = lowRamDevice,
            availableMemoryBytes = memoryInfo.availMem,
            lowMemoryThresholdBytes = LOW_MEMORY_THRESHOLD_BYTES
        )
        val storage = StatFs(context.filesDir.absolutePath)
        return Snapshot(
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            appStorageAvailableBytes = storage.availableBytes,
            isLowRamDevice = lowRamDevice,
            lowMemoryMode = lowMemory,
            recommendedAsrThreads = TomResourcePolicy.recommendedAsrThreads(lowMemory)
        )
    }

    fun hasEnoughSpaceForModel(context: Context): Boolean {
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        return TomResourcePolicy.hasEnoughModelInstallSpace(
            availableBytes = available,
            minimumBytes = MIN_MODEL_INSTALL_SPACE_BYTES
        )
    }

    fun formatBytes(bytes: Long): String {
        val mib = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
        return when {
            mib >= 1024.0 -> String.format(java.util.Locale.US, "%.1f GB", mib / 1024.0)
            else -> String.format(java.util.Locale.US, "%.0f MB", mib)
        }
    }

    /**
     * Only removes Tom's own incomplete model-download temporary file.
     * It never clears user files, model files, history, or other apps' data.
     */
    fun removeIncompleteModelArchive(context: Context) {
        File(context.cacheDir, "tom-bn-asr.tar.bz2").delete()
    }
}

internal object TomResourcePolicy {
    fun isLowMemory(
        isLowRamDevice: Boolean,
        availableMemoryBytes: Long,
        lowMemoryThresholdBytes: Long
    ): Boolean = isLowRamDevice || availableMemoryBytes < lowMemoryThresholdBytes

    fun recommendedAsrThreads(lowMemoryMode: Boolean): Int = if (lowMemoryMode) 1 else 2

    fun hasEnoughModelInstallSpace(
        availableBytes: Long,
        minimumBytes: Long
    ): Boolean = availableBytes >= minimumBytes
}
