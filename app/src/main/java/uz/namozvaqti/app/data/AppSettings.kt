package uz.namozvaqti.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uz.namozvaqti.app.locale.AppLanguage
import uz.namozvaqti.app.ui.theme.ThemeMode

private val Context.appSettingsStore by preferencesDataStore(name = "app_settings")

/** General settings: appearance, language, adhan toggle, onboarding flags, last known location. */
class AppSettings(private val context: Context) {

    private val keyAdhan = booleanPreferencesKey("adhan_enabled")
    private val keyLat = doublePreferencesKey("saved_lat")
    private val keyLon = doublePreferencesKey("saved_lon")
    private val keyOnboardingDone = booleanPreferencesKey("onboarding_done")
    private val keyAutostart = booleanPreferencesKey("autostart_confirmed")
    private val keyLocationPrompted = booleanPreferencesKey("location_prompted")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyLanguage = stringPreferencesKey("language")

    // ---------- Appearance & language ----------

    val themeMode: Flow<ThemeMode> =
        context.appSettingsStore.data.map { ThemeMode.fromName(it[keyTheme]) }

    /** Until the user chooses, follows the phone's language (ru / uz / uz-Cyrl) or falls back to English. */
    val language: Flow<AppLanguage> = context.appSettingsStore.data.map {
        AppLanguage.fromName(it[keyLanguage]) ?: AppLanguage.systemDefault()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsStore.edit { it[keyTheme] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.appSettingsStore.edit { it[keyLanguage] = language.name }
    }

    // ---------- Adhan ----------

    val adhanEnabled: Flow<Boolean> = context.appSettingsStore.data.map { it[keyAdhan] ?: true }

    suspend fun setAdhanEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { it[keyAdhan] = enabled }
    }

    // ---------- Location (used by background workers) ----------

    /** Last location obtained while the app was in the foreground, or null. */
    val savedLocation: Flow<Pair<Double, Double>?> = context.appSettingsStore.data.map {
        val lat = it[keyLat]
        val lon = it[keyLon]
        if (lat != null && lon != null) lat to lon else null
    }

    suspend fun saveLocation(lat: Double, lon: Double) {
        context.appSettingsStore.edit {
            it[keyLat] = lat
            it[keyLon] = lon
        }
    }

    // ---------- Onboarding ----------

    /** First-run battery/autostart onboarding finished (or skipped). */
    val onboardingDone: Flow<Boolean> = context.appSettingsStore.data.map { it[keyOnboardingDone] ?: false }

    /** The user ticked "I have enabled it" for the OEM autostart screen (cannot be detected by code). */
    val autostartConfirmed: Flow<Boolean> = context.appSettingsStore.data.map { it[keyAutostart] ?: false }

    /** Home asks for location once (first launch); later it lives in Settings. */
    val locationPrompted: Flow<Boolean> = context.appSettingsStore.data.map { it[keyLocationPrompted] ?: false }

    suspend fun setLocationPrompted() {
        context.appSettingsStore.edit { it[keyLocationPrompted] = true }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.appSettingsStore.edit { it[keyOnboardingDone] = done }
    }

    suspend fun setAutostartConfirmed(confirmed: Boolean) {
        context.appSettingsStore.edit { it[keyAutostart] = confirmed }
    }
}
