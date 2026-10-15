package uz.namozvaqti.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.namozvaqti.app.NamozApp
import java.util.concurrent.TimeUnit

/** Downloads fresh timings into Room and re-arms the alarm chain. */
class PrayerRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NamozApp
        val refreshed = app.prayerRepository.refresh()
        // Always re-arm: the cache may already hold valid times even if the network failed.
        app.scheduler.scheduleNext()

        return when {
            refreshed -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure() // the periodic request still runs again at its next interval
        }
    }

    companion object {
        private const val PERIODIC_NAME = "prayer_refresh_periodic"
        private const val ONE_TIME_NAME = "prayer_refresh_once"
        private const val MAX_ATTEMPTS = 5

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Once a day. KEEP => calling this on every app start does not reset the schedule. */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrayerRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Immediate refresh (first launch, after reboot, location change). */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<PrayerRefreshWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
