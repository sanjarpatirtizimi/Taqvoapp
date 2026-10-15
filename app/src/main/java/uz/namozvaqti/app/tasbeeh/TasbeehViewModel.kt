package uz.namozvaqti.app.tasbeeh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp

/** Taps per zikr; the 33rd tap gives the long vibration and switches to the next zikr. */
const val ZIKR_CYCLE = 33

/**
 * Survives rotation (ViewModel) and process death / app switching (DataStore).
 * Writes are debounced so rapid tapping doesn't hit the disk on every tap.
 */
class TasbeehViewModel(app: Application) : AndroidViewModel(app) {
    private val appScope = (app as NamozApp).appScope
    private val store = TasbeehStore(app)
    private val haptics = TasbeehHaptics(app)

    private val _state = MutableStateFlow(TasbeehState())
    val state: StateFlow<TasbeehState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    @OptIn(FlowPreview::class)
    private val persistJob = viewModelScope.launch {
        // The UI cycles Subhanallah -> Alhamdulillah -> Allahu Akbar every 33 taps, so the round
        // length is fixed (older builds let the user pick 99/100; ignore that saved value).
        _state.value = store.read().copy(target = ZIKR_CYCLE)
        loaded = true
        // drop(1): skip the value we just loaded; persist every later change after 150 ms of quiet.
        _state.drop(1).debounce(150).collect { store.save(it) }
    }

    fun onTap() {
        if (!loaded) return // ignore taps for the few ms before the saved count is read
        var milestone = false
        var vibrate = false
        _state.update { s ->
            val next = s.count + 1
            milestone = next % s.target == 0
            vibrate = s.hapticsEnabled
            s.copy(count = next)
        }
        if (vibrate) if (milestone) haptics.milestone() else haptics.tick()
    }

    fun undo() = _state.update { it.copy(count = (it.count - 1).coerceAtLeast(0)) }
    fun reset() = _state.update { it.copy(count = 0) }
    fun setTarget(target: Int) = _state.update { it.copy(target = target.coerceAtLeast(1)) }
    fun setHaptics(enabled: Boolean) = _state.update { it.copy(hapticsEnabled = enabled) }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so flush the latest value on the app scope.
        val latest = _state.value
        if (loaded) appScope.launch { store.save(latest) }
        super.onCleared()
    }
}
