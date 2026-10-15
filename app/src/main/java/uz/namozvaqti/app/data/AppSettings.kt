package uz.namozvaqti.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsStore by preferencesDataStore(name = "app_settings")

/** General settings: adhan toggle + last known location (used by background workers). */
class AppSettings(private val context: Context) {

    private val keyAdhan = booleanPreferencesKey("adhan_enabled")
    private val keyLat = doublePreferencesKey("saved_lat")
    private val keyLon = doublePreferencesKey("saved_lon")
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyAutostart = booleanPreferencesKey("autostart_confirmed")

    val adhanEnabled: Flow<Boolean> = context.appSettingsStore.data.map { it[keyAdhan] ?: true }

    /** Last location obtained while the app was in the foreground, or null. */
    val savedLocation: Flow<Pair<Double, Double>?> = context.appSettingsStore.data.map {
        val lat = it[keyLat]
        val lon = it[keyLon]
        if (lat != null && lon != null) lat to lon else null
    }

    /** First-run battery/autostart onboarding finished (or skipped). */
    val onboardingDone: Flow<Boolean> = context.appSettingsStore.data.map { it[keyOnboardingDone] ?: false }

    /** The user ticked "I have enabled it" for the OEM autostart screen (cannot be detected by code). */
    val autostartConfirmed: Flow<Boolean> = context.appSettingsStore.data.map { it[keyAutostart] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.appSettingsStore.edit { it[keyOnboardingDone] = done }
    }

    suspend fun setAutostartConfirmed(confirmed: Boolean) {
        context.appSettingsStore.edit { it[keyAutostart] = confirmed }
    }

    suspend fun setAdhanEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { it[keyAdhan] = enabled }
    }

    suspend fun saveLocation(lat: Double, lon: Double) {
        context.appSettingsStore.edit {
            it[keyLat] = lat
            it[keyLon] = lon
        }
    }
}
