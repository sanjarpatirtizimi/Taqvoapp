package uz.namozvaqti.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.data.local.PrayerDayEntity
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as NamozApp
    private val prefs = container.preferences

    val protectionEnabled: StateFlow<Boolean> =
        prefs.protectionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val blacklist: StateFlow<Set<String>> =
        prefs.blacklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val adhanEnabled: StateFlow<Boolean> =
        container.settings.adhanEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Today's cached timings (null until the first successful download). */
    val today: StateFlow<PrayerDayEntity?> =
        container.prayerRepository.observeDay(LocalDate.now())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null while DataStore is still loading (avoids flashing the onboarding screen on every launch). */
    val onboardingDone: StateFlow<Boolean?> =
        container.settings.onboardingDone.map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(true) }

    fun setEnabled(value: Boolean) = viewModelScope.launch { prefs.setProtectionEnabled(value) }
    fun setAdhanEnabled(value: Boolean) = viewModelScope.launch { container.settings.setAdhanEnabled(value) }
    fun add(pkg: String) = viewModelScope.launch { prefs.addToBlacklist(pkg) }
    fun remove(pkg: String) = viewModelScope.launch { prefs.removeFromBlacklist(pkg) }

    /** Foreground refresh: get a fresh GPS/network fix (if permitted), download, re-arm alarms. */
    fun refreshPrayerTimes() = viewModelScope.launch {
        container.locationProvider.requestFreshLocation()
        container.prayerRepository.refresh()
        container.scheduler.scheduleNext()
    }
}
