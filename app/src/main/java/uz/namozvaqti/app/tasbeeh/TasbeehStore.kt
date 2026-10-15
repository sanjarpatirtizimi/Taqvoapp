package uz.namozvaqti.app.tasbeeh

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.tasbeehDataStore by preferencesDataStore(name = "tasbeeh")

/**
 * [count] is the running total (it does NOT reset at the target), so with target = 33 the
 * milestones are 33, 66, 99, ... Progress ring and "rounds" are derived from count and target.
 */
data class TasbeehState(
    val count: Int = 0,
    val target: Int = 33,
    val hapticsEnabled: Boolean = true
) {
    val rounds: Int get() = count / target
    val countInRound: Int get() = if (count > 0 && count % target == 0) target else count % target
}

class TasbeehStore(private val context: Context) {
    private val keyCount = intPreferencesKey("count")
    private val keyTarget = intPreferencesKey("target")
    private val keyHaptics = booleanPreferencesKey("haptics")

    suspend fun read(): TasbeehState {
        val prefs = context.tasbeehDataStore.data.first()
        return TasbeehState(
            count = prefs[keyCount] ?: 0,
            target = (prefs[keyTarget] ?: 33).coerceAtLeast(1),
            hapticsEnabled = prefs[keyHaptics] ?: true
        )
    }

    suspend fun save(state: TasbeehState) {
        context.tasbeehDataStore.edit {
            it[keyCount] = state.count
            it[keyTarget] = state.target
            it[keyHaptics] = state.hapticsEnabled
        }
    }
}
