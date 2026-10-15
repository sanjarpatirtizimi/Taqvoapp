package uz.namozvaqti.app.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = (app as NamozApp).settings

    val oem: Oem = OemSettings.detect()

    val autostartConfirmed: StateFlow<Boolean> =
        settings.autostartConfirmed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutostartConfirmed(value: Boolean) {
        viewModelScope.launch { settings.setAutostartConfirmed(value) }
    }
}
