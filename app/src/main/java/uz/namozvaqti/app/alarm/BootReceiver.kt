package uz.namozvaqti.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.work.PrayerRefreshWorker

/**
 * AlarmManager alarms are wiped on reboot. This (and the other system events in the manifest)
 * rebuilds the alarm chain from the Room cache - no network needed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val app = context.applicationContext as NamozApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.scheduler.scheduleNext()
                // Also top up the cache in the background if the network is available.
                PrayerRefreshWorker.enqueueOneTime(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
