package uz.namozvaqti.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.data.local.PrayerDayEntity
import java.time.LocalDate

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as NamozApp

    /**
     * Yesterday + the next 3 days. Yesterday keeps the screen correct for the first moments after
     * midnight; the extra days let the hero roll over to tomorrow's Fajr after Isha.
     * null = Room has not answered yet.
     */
    val days: StateFlow<List<PrayerDayEntity>?> =
        container.prayerRepository.observeFrom(LocalDate.now().minusDays(1), 4)
            .map<List<PrayerDayEntity>, List<PrayerDayEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ticks once per second, only while the screen is visible (WhileSubscribed). */
    val nowMs: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** null while DataStore is loading. */
    val locationPrompted: StateFlow<Boolean?> =
        container.settings.locationPrompted.map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        refresh()
    }

    fun markLocationPrompted() = viewModelScope.launch { container.settings.setLocationPrompted() }

    /** Fresh location (if permitted) -> download -> re-arm the alarm chain. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                container.locationProvider.requestFreshLocation()
                container.prayerRepository.refresh()
                container.scheduler.scheduleNext()
            } finally {
                _refreshing.value = false
            }
        }
    }
}
