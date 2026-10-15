package uz.namozvaqti.app.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.R
import uz.namozvaqti.app.apps.AppCategory
import uz.namozvaqti.app.apps.InstalledApp
import uz.namozvaqti.app.apps.InstalledAppsRepository

enum class AppFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    SUGGESTED(R.string.filter_suggested),
    SOCIAL(R.string.filter_social),
    GAMES(R.string.filter_games),
    SELECTED(R.string.filter_selected)
}

data class AppPickerUiState(
    val isLoading: Boolean = true,
    val items: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val suggestedNotSelected: Int = 0
)

class AppPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as NamozApp).preferences
    private val repository = InstalledAppsRepository(app)

    private val allApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AppFilter.ALL)

    val uiState: StateFlow<AppPickerUiState> =
        combine(allApps, query, filter, prefs.blacklist) { apps, q, f, selected ->
            if (apps == null) return@combine AppPickerUiState(isLoading = true, selected = selected)

            val visible = apps.asSequence()
                .filter { app ->
                    when (f) {
                        AppFilter.ALL -> true
                        AppFilter.SUGGESTED -> app.suggested
                        AppFilter.SOCIAL -> app.category == AppCategory.SOCIAL || app.category == AppCategory.VIDEO
                        AppFilter.GAMES -> app.category == AppCategory.GAME
                        AppFilter.SELECTED -> app.packageName in selected
                    }
                }
                .filter {
                    q.isBlank() ||
                        it.label.contains(q.trim(), ignoreCase = true) ||
                        it.packageName.contains(q.trim(), ignoreCase = true)
                }
                .toList()

            AppPickerUiState(
                isLoading = false,
                items = visible,
                selected = selected,
                query = q,
                filter = f,
                suggestedNotSelected = apps.count { it.suggested && it.packageName !in selected }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    /** Called each time the screen opens, so newly installed apps show up. */
    fun reload() {
        viewModelScope.launch { allApps.value = repository.loadLaunchableApps() }
    }

    fun setQuery(value: String) = query.update { value }
    fun setFilter(value: AppFilter) = filter.update { value }

    fun toggle(packageName: String, blocked: Boolean) {
        viewModelScope.launch { prefs.setBlocked(packageName, blocked) }
    }

    fun selectSuggested() {
        val suggested = allApps.value.orEmpty().filter { it.suggested }.map { it.packageName }
        viewModelScope.launch { prefs.addAll(suggested) }
    }

    fun clearAll() {
        viewModelScope.launch { prefs.clearBlacklist() }
    }
}
