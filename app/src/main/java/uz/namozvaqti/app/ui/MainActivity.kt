package uz.namozvaqti.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.R
import uz.namozvaqti.app.adhan.AdhanService
import uz.namozvaqti.app.data.local.PrayerDayEntity
import uz.namozvaqti.app.domain.Prayer
import uz.namozvaqti.app.onboarding.OnboardingScreen
import uz.namozvaqti.app.protection.ProtectionPermissions
import uz.namozvaqti.app.protection.ProtectionService
import uz.namozvaqti.app.qibla.QiblaScreen
import uz.namozvaqti.app.tasbeeh.TasbeehScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppRoot(vm) } }
    }
}

private enum class Tab(@StringRes val labelRes: Int, val emoji: String) {
    HOME(R.string.tab_home, "🏠"),
    QIBLA(R.string.tab_qibla, "🧭"),
    TASBEEH(R.string.tab_tasbeeh, "📿")
}

/**
 * Gate: first launch shows the reliability onboarding. After that: 3 bottom-bar tabs, plus the app
 * picker and the onboarding (re-openable from Home) as full-screen sub-pages.
 * Tab content leaves composition when you switch away, so the compass sensor stops automatically.
 */
@Composable
private fun AppRoot(vm: MainViewModel) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }

    when {
        onboardingDone == null -> { // still reading DataStore
            Box(Modifier.fillMaxSize())
            return
        }
        onboardingDone == false || showOnboarding -> {
            BackHandler(enabled = showOnboarding) { showOnboarding = false }
            OnboardingScreen(onFinish = {
                vm.completeOnboarding()
                showOnboarding = false
            })
            return
        }
    }

    if (showPicker) {
        BackHandler { showPicker = false }
        AppPickerScreen(onBack = { showPicker = false })
        return
    }

    // Back from Qibla/Tasbeeh goes to Home instead of closing the app.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji) },
                        label = { Text(stringResource(t.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> SetupScreen(
                vm,
                onOpenPicker = { showPicker = true },
                onOpenOnboarding = { showOnboarding = true },
                modifier = content
            )
            Tab.QIBLA -> QiblaScreen(modifier = content)
            Tab.TASBEEH -> TasbeehScreen(modifier = content)
        }
    }
}

@Composable
private fun SetupScreen(
    vm: MainViewModel,
    onOpenPicker: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val enabled by vm.protectionEnabled.collectAsStateWithLifecycle()
    val adhanEnabled by vm.adhanEnabled.collectAsStateWithLifecycle()
    val blacklist by vm.blacklist.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    var overlayOk by remember { mutableStateOf(ProtectionPermissions.canDrawOverlays(ctx)) }
    var a11yOk by remember { mutableStateOf(ProtectionPermissions.isAccessibilityEnabled(ctx)) }
    var usageOk by remember { mutableStateOf(ProtectionPermissions.hasUsageAccess(ctx)) }
    var exactOk by remember { mutableStateOf((ctx.applicationContext as NamozApp).scheduler.canScheduleExact()) }
    var showDisclosure by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayOk = ProtectionPermissions.canDrawOverlays(ctx)
        a11yOk = ProtectionPermissions.isAccessibilityEnabled(ctx)
        usageOk = ProtectionPermissions.hasUsageAccess(ctx)
        exactOk = (ctx.applicationContext as NamozApp).scheduler.canScheduleExact()
    }

    // First launch / every return to the app: refresh timings (works with Tashkent fallback too).
    LaunchedEffect(Unit) { vm.refreshPrayerTimes() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPrayerTimes() // re-run now that a real location may be available
    }
    val testSessionName = stringResource(R.string.test_session_name)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), // insets come from Scaffold
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        // ---------- Prayer times ----------
        PrayerTimesCard(today)
        OutlinedButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
            Text(stringResource(R.string.btn_use_location))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_play_adhan), Modifier.weight(1f))
            Switch(checked = adhanEnabled, onCheckedChange = vm::setAdhanEnabled)
        }
        PermissionRow(stringResource(R.string.perm_exact_alarms), exactOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.btn_allow_notifications))
            }
        }
        OutlinedButton(onClick = { AdhanService.start(ctx, Prayer.DHUHR) }) {
            Text(stringResource(R.string.btn_test_adhan))
        }
        // Battery / autostart onboarding (critical on Xiaomi, Samsung, Oppo, Huawei...)
        Button(onClick = onOpenOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_reliability))
        }

        // ---------- Focus ----------
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.focus_enable), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = vm::setEnabled)
        }
        PermissionRow(stringResource(R.string.perm_overlay), overlayOk) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        PermissionRow(stringResource(R.string.perm_accessibility), a11yOk) { showDisclosure = true }
        PermissionRow(stringResource(R.string.perm_usage), usageOk) {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_choose_apps, blacklist.size))
        }

        Button(
            onClick = { ProtectionService.start(ctx, testSessionName, 60_000L) },
            enabled = enabled
        ) { Text(stringResource(R.string.btn_test_session)) }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.a11y_disclosure_title)) },
            text = { Text(stringResource(R.string.a11y_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(stringResource(R.string.a11y_disclosure_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.a11y_disclosure_decline))
                }
            }
        )
    }
}

@Composable
private fun PrayerTimesCard(day: PrayerDayEntity?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (day == null) {
                Text(stringResource(R.string.prayer_loading))
                return@Column
            }
            val zone = ZoneId.of(day.timezone)
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            fun fmtTime(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(fmt)

            Text(day.date, style = MaterialTheme.typography.titleMedium)
            listOf(
                stringResource(R.string.prayer_fajr) to day.fajr,
                stringResource(R.string.prayer_sunrise) to day.sunrise,
                stringResource(R.string.prayer_dhuhr) to day.dhuhr,
                stringResource(R.string.prayer_asr) to day.asr,
                stringResource(R.string.prayer_maghrib) to day.maghrib,
                stringResource(R.string.prayer_isha) to day.isha
            ).forEach { (name, time) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(name, Modifier.weight(1f))
                    Text(fmtTime(time))
                }
            }
        }
    }
}

/** Shared with the onboarding screen. */
@Composable
internal fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (granted) Text(stringResource(R.string.status_on))
        else OutlinedButton(onClick = onClick) { Text(stringResource(R.string.action_enable)) }
    }
}
