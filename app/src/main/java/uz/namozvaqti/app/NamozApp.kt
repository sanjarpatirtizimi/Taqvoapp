package uz.namozvaqti.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uz.namozvaqti.app.adhan.AdhanService
import uz.namozvaqti.app.alarm.PrayerAlarmScheduler
import uz.namozvaqti.app.data.AppSettings
import uz.namozvaqti.app.data.PrayerTimesRepository
import uz.namozvaqti.app.data.ProtectionPreferences
import uz.namozvaqti.app.data.local.AppDatabase
import uz.namozvaqti.app.data.location.LocationProvider
import uz.namozvaqti.app.data.remote.AladhanClient
import uz.namozvaqti.app.locale.AppLanguage
import uz.namozvaqti.app.locale.LocaleHelper
import uz.namozvaqti.app.protection.ProtectionService
import uz.namozvaqti.app.work.PrayerRefreshWorker

class NamozApp : Application() {

    /** App-wide scope for short work started from BroadcastReceivers (inside goAsync()). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The language chosen in Settings, kept in memory so services, receivers and notifications
     * (which have no Compose) can read it synchronously. DataStore stays the source of truth.
     */
    @Volatile
    var currentLanguage: AppLanguage = AppLanguage.systemDefault()
        private set

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

        // One tiny DataStore read so the very first notification already uses the right language.
        currentLanguage = runBlocking { settings.language.first() }
        createNotificationChannels()

        // Keep the in-memory language fresh and re-label the notification channels on change.
        appScope.launch {
            settings.language.collect { language ->
                if (language != currentLanguage) {
                    currentLanguage = language
                    createNotificationChannels()
                }
            }
        }

        PrayerRefreshWorker.enqueuePeriodic(this)
        // Cheap safety net: re-arm the next alarm on every cold start.
        appScope.launch { scheduler.scheduleNext() }
    }

    /** Safe to call repeatedly: re-creating a channel with the same id only updates its name. */
    private fun createNotificationChannels() {
        val text = LocaleHelper.forApp(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ProtectionService.CHANNEL_ID,
                text.getString(R.string.notif_channel_protection),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                AdhanService.CHANNEL_ID,
                text.getString(R.string.notif_channel_adhan),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null) } // the service plays the audio itself
        )
    }
}
