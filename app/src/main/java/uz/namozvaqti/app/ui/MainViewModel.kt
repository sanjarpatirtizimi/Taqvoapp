package uz.namozvaqti.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.locale.AppLanguage
import uz.namozvaqti.app.ui.theme.ThemeMode

/** Everything the root needs BEFORE it can draw anything (theme + language + first-run gate). */
data class AppPrefs(
    val themeMode: ThemeMode,
    val language: AppLanguage,
    val onboardingDone: Boolean
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as NamozApp
    private val prefs = container.preferences
    private val settings = container.settings

    /** null while DataStore is loading (avoids a theme/onboarding flash on every launch). */
    val appPrefs: StateFlow<AppPrefs?> =
        combine(settings.themeMode, settings.language, settings.onboardingDone) { theme, language, onboarding ->
            AppPrefs(theme, language, onboarding)
        }.map<AppPrefs, AppPrefs?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val protectionEnabled: StateFlow<Boolean> =
        prefs.protectionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val blacklist: StateFlow<Set<String>> =
        prefs.blacklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val adhanEnabled: StateFlow<Boolean> =
        settings.adhanEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setLanguage(language: AppLanguage) = viewModelScope.launch { settings.setLanguage(language) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }

    fun setEnabled(value: Boolean) = viewModelScope.launch { prefs.setProtectionEnabled(value) }
    fun setAdhanEnabled(value: Boolean) = viewModelScope.launch { settings.setAdhanEnabled(value) }

    /** After the user grants location in Settings: fetch a fresh fix, download, re-arm alarms. */
    fun refreshPrayerTimes() = viewModelScope.launch {
        container.locationProvider.requestFreshLocation()
        container.prayerRepository.refresh()
        container.scheduler.scheduleNext()
    }
}
