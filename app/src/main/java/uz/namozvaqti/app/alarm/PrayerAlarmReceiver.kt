package uz.namozvaqti.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.adhan.AdhanService
import uz.namozvaqti.app.domain.Prayer
import uz.namozvaqti.app.protection.ProtectionService

/**
 * Fired by AlarmManager at prayer time. Starting foreground services from an exact-alarm broadcast
 * is exempt from the Android 12+ background-start restrictions.
 *
 * 1. schedule the NEXT prayer first (so a crash below can't break the chain)
 * 2. play the adhan (if enabled)
 * 3. start the focus session / overlay (if enabled)
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayer = Prayer.fromName(intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER)) ?: return
        val triggerAt = intent.getLongExtra(PrayerAlarmScheduler.EXTRA_TRIGGER_AT, 0L)
        val app = context.applicationContext as NamozApp
        val pending = goAsync()

        app.appScope.launch {
            try {
                app.scheduler.scheduleNext()

                // If the alarm was delayed a lot (e.g. phone was off), don't blast the adhan late.
                val lateBy = System.currentTimeMillis() - triggerAt
                if (lateBy in 0..MAX_LATE_MS) {
                    if (app.settings.adhanEnabled.first()) {
                        AdhanService.start(app, prayer)
                    }
                    if (app.preferences.protectionEnabled.first()) {
                        ProtectionService.start(
                            app, app.getString(prayer.nameRes), ProtectionService.DEFAULT_DURATION_MS
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val MAX_LATE_MS = 5 * 60_000L
    }
}
