package uz.namozvaqti.app.protection.blocker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Fallback foreground-app detector (polled by ProtectionService ONLY while a session is running
 * and the Accessibility service is not enabled). Requires the "Usage access" special permission.
 */
class UsageStatsForegroundDetector(context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    private val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        UsageEvents.Event.ACTIVITY_RESUMED else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
    private val paused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        UsageEvents.Event.ACTIVITY_PAUSED else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_BACKGROUND

    /** Package that is currently in front, or null if it can't be determined. */
    fun currentForegroundPackage(windowMs: Long = 2 * 60_000L): String? {
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - windowMs, end) ?: return null
        val e = UsageEvents.Event()
        var current: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                resumed -> current = e.packageName
                paused -> if (e.packageName == current) current = null
            }
        }
        return current
    }
}
