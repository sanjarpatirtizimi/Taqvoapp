package uz.namozvaqti.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.namozvaqti.app.adhan.AdhanService
import uz.namozvaqti.app.alarm.PrayerAlarmScheduler
import uz.namozvaqti.app.data.AppSettings
import uz.namozvaqti.app.data.PrayerTimesRepository
import uz.namozvaqti.app.data.ProtectionPreferences
import uz.namozvaqti.app.data.local.AppDatabase
import uz.namozvaqti.app.data.location.LocationProvider
import uz.namozvaqti.app.data.remote.AladhanClient
import uz.namozvaqti.app.protection.ProtectionService
import uz.namozvaqti.app.work.PrayerRefreshWorker

class NamozApp : Application() {

    /** App-wide scope for short work started from BroadcastReceivers (inside goAsync()). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Simple manual DI. Swap for Hilt later if the graph grows.
    lateinit var preferences: ProtectionPreferences
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var locationProvider: LocationProvider
        private set
    lateinit var prayerRepository: PrayerTimesRepository
        private set
    lateinit var scheduler: PrayerAlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = ProtectionPreferences(this)
        settings = AppSettings(this)
        database = AppDatabase.create(this)
        locationProvider = LocationProvider(this, settings)
        prayerRepository = PrayerTimesRepository(AladhanClient.create(), database.prayerDao(), locationProvider)
        scheduler = PrayerAlarmScheduler(this, database.prayerDao())

        createNotificationChannels()

        PrayerRefreshWorker.enqueuePeriodic(this)
        // Cheap safety net: re-arm the next alarm on every cold start.
        appScope.launch { scheduler.scheduleNext() }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ProtectionService.CHANNEL_ID,
                getString(R.string.notif_channel_protection),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                AdhanService.CHANNEL_ID,
                getString(R.string.notif_channel_adhan),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) } // the service plays the audio itself
        )
    }
}
