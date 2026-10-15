package uz.namozvaqti.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionDataStore by preferencesDataStore(name = "protection_prefs")

/**
 * User preferences for the Dual-Layer system.
 * Protection is OPT-IN (default = false) - important for Play policy and user trust.
 */
class ProtectionPreferences(private val context: Context) {

    private val keyEnabled = booleanPreferencesKey("protection_enabled")
    private val keyBlacklist = stringSetPreferencesKey("blacklist_packages")

    val protectionEnabled: Flow<Boolean> =
        context.protectionDataStore.data.map { it[keyEnabled] ?: false }

    val blacklist: Flow<Set<String>> =
        context.protectionDataStore.data.map { it[keyBlacklist] ?: emptySet() }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.protectionDataStore.edit { it[keyEnabled] = enabled }
    }

    suspend fun addToBlacklist(packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        context.protectionDataStore.edit { it[keyBlacklist] = (it[keyBlacklist] ?: emptySet()) + pkg }
    }

    suspend fun removeFromBlacklist(packageName: String) {
        context.protectionDataStore.edit { it[keyBlacklist] = (it[keyBlacklist] ?: emptySet()) - packageName }
    }

    /** Single atomic edit - used by the app picker checkboxes. */
    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        context.protectionDataStore.edit {
            val current = it[keyBlacklist] ?: emptySet()
            it[keyBlacklist] = if (blocked) current + packageName else current - packageName
        }
    }

    suspend fun addAll(packages: Collection<String>) {
        if (packages.isEmpty()) return
        context.protectionDataStore.edit { it[keyBlacklist] = (it[keyBlacklist] ?: emptySet()) + packages }
    }

    suspend fun clearBlacklist() {
        context.protectionDataStore.edit { it[keyBlacklist] = emptySet() }
    }
}
