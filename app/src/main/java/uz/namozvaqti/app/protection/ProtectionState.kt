package uz.namozvaqti.app.protection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProtectionSession(
    val prayerName: String,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    /** True once the user used the 15s emergency exit. Layer 2 keeps running after this. */
    val overlayDismissed: Boolean = false
)

/**
 * Single source of truth shared by ProtectionService (Layer 1) and the AccessibilityService
 * (Layer 2). Both live in the same process, so an in-process StateFlow is enough.
 */
object ProtectionStateStore {
    private val _session = MutableStateFlow<ProtectionSession?>(null)
    val session: StateFlow<ProtectionSession?> = _session.asStateFlow()

    fun start(session: ProtectionSession) {
        _session.value = session
    }

    fun markOverlayDismissed() {
        _session.update { it?.copy(overlayDismissed = true) }
    }

    fun end() {
        _session.value = null
    }

    /** Layer 2 is active for the whole session, even after the overlay was dismissed. */
    fun isBlockingActive(now: Long = System.currentTimeMillis()): Boolean =
        _session.value?.let { now < it.endsAtMillis } ?: false
}
