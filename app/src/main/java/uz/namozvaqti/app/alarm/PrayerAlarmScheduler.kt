package uz.namozvaqti.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import uz.namozvaqti.app.data.local.PrayerDao
import uz.namozvaqti.app.domain.Prayer
import java.time.LocalDate

/**
 * "Alarm chain" strategy: exactly ONE alarm is pending at any time - the next upcoming prayer.
 * When it fires, PrayerAlarmReceiver schedules the following one. Benefits:
 *  - no stale alarms after the timetable changes (same PendingIntent is simply replaced)
 *  - trivial to rebuild after reboot / permission change / timezone change: call scheduleNext()
 */
class PrayerAlarmScheduler(
    private val context: Context,
    private val dao: PrayerDao
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** @return the scheduled (prayer, triggerAtMillis), or null if the cache has no future times. */
    suspend fun scheduleNext(): Pair<Prayer, Long>? {
        val now = System.currentTimeMillis()
        // Start from yesterday to be safe around midnight / timezone differences.
        val days = dao.getFrom(LocalDate.now().minusDays(1).toString(), 4)
        val next = days
            .flatMap { day -> Prayer.entries.map { it to day.timeOf(it) } }
            .filter { (_, time) -> time > now }
            .minByOrNull { (_, time) -> time }

        if (next == null) {
            Log.w(TAG, "No future prayer times in cache; waiting for refresh")
            return null
        }
        setAlarm(next.first, next.second)
        return next
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun setAlarm(prayer: Prayer, triggerAt: Long) {
        val pi = pendingIntent(context, prayer, triggerAt)
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            }
        }
        // Without "Alarms & reminders" the OS may delay this by several minutes.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    companion object {
        private const val TAG = "PrayerAlarmScheduler"
        private const val REQUEST_CODE = 2001
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TRIGGER_AT = "trigger_at"

        fun pendingIntent(context: Context, prayer: Prayer, triggerAt: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, PrayerAlarmReceiver::class.java)
                    .putExtra(EXTRA_PRAYER, prayer.name)
                    .putExtra(EXTRA_TRIGGER_AT, triggerAt),
                // Same request code + UPDATE_CURRENT => the previous alarm is replaced, never duplicated.
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
