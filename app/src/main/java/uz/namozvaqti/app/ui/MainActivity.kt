package uz.namozvaqti.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.namozvaqti.app.R
import uz.namozvaqti.app.locale.ProvideAppLocale
import uz.namozvaqti.app.onboarding.OnboardingScreen
import uz.namozvaqti.app.qibla.QiblaScreen
import uz.namozvaqti.app.tasbeeh.TasbeehScreen
import uz.namozvaqti.app.ui.home.HomeScreen
import uz.namozvaqti.app.ui.settings.SettingsScreen
import uz.namozvaqti.app.ui.theme.NamozTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot(vm) }
    }
}

private enum class Tab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    QIBLA(R.string.tab_qibla, Icons.Filled.Explore),
    TASBEEH(R.string.tab_tasbeeh, Icons.Filled.TouchApp),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings)
}

/**
 * Root: waits for DataStore (theme + language), then wraps everything in the chosen locale and theme.
 * Changing either in Settings recomposes this tree instantly - no Activity restart.
 */
@Composable
private fun AppRoot(vm: MainViewModel) {
    val prefs by vm.appPrefs.collectAsStateWithLifecycle()
    val p = prefs
    if (p == null) { // still reading DataStore (a few ms)
        Box(Modifier.fillMaxSize())
        return
    }

    ProvideAppLocale(p.language) {
        NamozTheme(p.themeMode) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppContent(vm, p)
            }
        }
    }
}

/**
 * Gate: first launch shows the reliability onboarding. After that: 4 bottom-bar tabs, plus the
 * app picker and the onboarding (re-openable from Settings) as full-screen sub-pages.
 * Tab content leaves composition when you switch away, so the compass sensor stops automatically.
 */
@Composable
private fun AppContent(vm: MainViewModel, prefs: AppPrefs) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }

    if (!prefs.onboardingDone || showOnboarding) {
        BackHandler(enabled = showOnboarding) { showOnboarding = false }
        OnboardingScreen(onFinish = {
            vm.completeOnboarding()
            showOnboarding = false
        })
        return
    }

    if (showPicker) {
        BackHandler { showPicker = false }
        AppPickerScreen(onBack = { showPicker = false })
        return
    }

    // Back from any other tab goes to Home instead of closing the app.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        val content = Modifier.padding(padding) // status-bar + nav-bar insets come from here
        when (tab) {
            Tab.HOME -> HomeScreen(modifier = content)
            Tab.QIBLA -> QiblaScreen(modifier = content)
            Tab.TASBEEH -> TasbeehScreen(modifier = content)
            Tab.SETTINGS -> SettingsScreen(
                vm = vm,
                prefs = prefs,
                onOpenPicker = { showPicker = true },
                onOpenOnboarding = { showOnboarding = true },
                modifier = content
            )
        }
    }
}
