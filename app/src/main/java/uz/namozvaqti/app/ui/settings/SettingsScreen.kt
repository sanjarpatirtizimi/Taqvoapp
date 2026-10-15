package uz.namozvaqti.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.R
import uz.namozvaqti.app.adhan.AdhanService
import uz.namozvaqti.app.domain.Prayer
import uz.namozvaqti.app.locale.AppLanguage
import uz.namozvaqti.app.onboarding.OemSettings
import uz.namozvaqti.app.protection.ProtectionPermissions
import uz.namozvaqti.app.protection.ProtectionService
import uz.namozvaqti.app.ui.AppPrefs
import uz.namozvaqti.app.ui.MainViewModel
import uz.namozvaqti.app.ui.components.ChecklistItem
import uz.namozvaqti.app.ui.components.ChoiceDialog
import uz.namozvaqti.app.ui.components.GroupDivider
import uz.namozvaqti.app.ui.components.SettingsGroup
import uz.namozvaqti.app.ui.components.SettingsItem
import uz.namozvaqti.app.ui.components.SettingsValueItem
import uz.namozvaqti.app.ui.theme.ThemeMode

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.EN -> R.string.lang_en
    AppLanguage.RU -> R.string.lang_ru
    AppLanguage.UZ -> R.string.lang_uz
    AppLanguage.UZ_CYRL -> R.string.lang_uz_cyrl
}

private fun notificationsGranted(ctx: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun locationGranted(ctx: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Control hub. Groups: Appearance, Focus mode & permissions, Alarms & battery, Adhan.
 * Permission states are re-read on every ON_RESUME because the user changes them in system screens.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    prefs: AppPrefs,
    onOpenPicker: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NamozApp

    val focusEnabled by vm.protectionEnabled.collectAsStateWithLifecycle()
    val adhanEnabled by vm.adhanEnabled.collectAsStateWithLifecycle()
    val blacklist by vm.blacklist.collectAsStateWithLifecycle()

    var overlayOk by remember { mutableStateOf(ProtectionPermissions.canDrawOverlays(ctx)) }
    var a11yOk by remember { mutableStateOf(ProtectionPermissions.isAccessibilityEnabled(ctx)) }
    var usageOk by remember { mutableStateOf(ProtectionPermissions.hasUsageAccess(ctx)) }
    var exactOk by remember { mutableStateOf(app.scheduler.canScheduleExact()) }
    var notifOk by remember { mutableStateOf(notificationsGranted(ctx)) }
    var batteryOk by remember { mutableStateOf(OemSettings.isIgnoringBatteryOptimizations(ctx)) }
    var locationOk by remember { mutableStateOf(locationGranted(ctx)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayOk = ProtectionPermissions.canDrawOverlays(ctx)
        a11yOk = ProtectionPermissions.isAccessibilityEnabled(ctx)
        usageOk = ProtectionPermissions.hasUsageAccess(ctx)
        exactOk = app.scheduler.canScheduleExact()
        notifOk = notificationsGranted(ctx)
        batteryOk = OemSettings.isIgnoringBatteryOptimizations(ctx)
        locationOk = locationGranted(ctx)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = notificationsGranted(ctx)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationOk = locationGranted(ctx)
        if (locationOk) vm.refreshPrayerTimes()
    }

    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val testSessionName = stringResource(R.string.test_session_name)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        // ---------------- Appearance ----------------
        SettingsGroup(stringResource(R.string.section_appearance)) {
            SettingsValueItem(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.setting_theme),
                value = stringResource(prefs.themeMode.labelRes()),
                onClick = { showThemeDialog = true }
            )
            GroupDivider()
            SettingsValueItem(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.setting_language),
                value = stringResource(prefs.language.labelRes()),
                onClick = { showLanguageDialog = true }
            )
        }

        // ---------------- Focus mode & permissions ----------------
        SettingsGroup(stringResource(R.string.section_focus)) {
            SettingsItem(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.focus_toggle_title),
                subtitle = stringResource(R.string.focus_toggle_desc),
                trailing = { Switch(checked = focusEnabled, onCheckedChange = vm::setEnabled) }
            )
            GroupDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.focus_checklist_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.focus_checklist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ChecklistItem(
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                granted = overlayOk,
                onAllow = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            )
            ChecklistItem(
                title = stringResource(R.string.perm_a11y_title),
                description = stringResource(R.string.perm_a11y_desc),
                granted = a11yOk,
                onAllow = { showDisclosure = true }
            )
            ChecklistItem(
                title = stringResource(R.string.perm_usage_title),
                description = stringResource(R.string.perm_usage_desc),
                granted = usageOk,
                onAllow = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Filled.Apps,
                title = stringResource(R.string.focus_blocked_apps_title),
                subtitle = stringResource(R.string.focus_blocked_apps_desc, blacklist.size),
                onClick = onOpenPicker,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.focus_test_title),
                subtitle = stringResource(R.string.focus_test_desc),
                trailing = {
                    FilledTonalButton(
                        onClick = { ProtectionService.start(ctx, testSessionName, 60_000L) },
                        enabled = focusEnabled
                    ) { Text(stringResource(R.string.action_start)) }
                }
            )
        }

        // ---------------- Alarms & battery ----------------
        SettingsGroup(stringResource(R.string.section_alarms)) {
            ChecklistItem(
                title = stringResource(R.string.alarms_exact_title),
                description = stringResource(R.string.alarms_exact_desc),
                granted = exactOk,
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }
            )
            ChecklistItem(
                title = stringResource(R.string.alarms_notifications_title),
                description = stringResource(R.string.alarms_notifications_desc),
                granted = notifOk,
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            ChecklistItem(
                title = stringResource(R.string.alarms_battery_title),
                description = stringResource(
                    if (batteryOk) R.string.alarms_battery_ok else R.string.alarms_battery_bad
                ),
                granted = batteryOk,
                onAllow = {
                    if (!OemSettings.openBatteryOptimizationList(ctx)) OemSettings.openAppDetails(ctx)
                }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Filled.BatteryChargingFull,
                title = stringResource(R.string.alarms_guide_title),
                subtitle = stringResource(R.string.alarms_guide_desc),
                onClick = onOpenOnboarding,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ---------------- Adhan ----------------
        SettingsGroup(stringResource(R.string.section_adhan)) {
            SettingsItem(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.adhan_play_title),
                subtitle = stringResource(R.string.adhan_play_desc),
                trailing = { Switch(checked = adhanEnabled, onCheckedChange = vm::setAdhanEnabled) }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.adhan_test_title),
                subtitle = stringResource(R.string.adhan_test_desc),
                trailing = {
                    FilledTonalButton(onClick = { AdhanService.start(ctx, Prayer.DHUHR) }) {
                        Text(stringResource(R.string.action_play))
                    }
                }
            )
            GroupDivider()
            SettingsItem(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.adhan_location_title),
                subtitle = stringResource(R.string.adhan_location_desc),
                trailing = {
                    if (locationOk) {
                        Text(
                            stringResource(R.string.status_granted),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        ) { Text(stringResource(R.string.action_allow)) }
                    }
                }
            )
        }
    }

    // ---------------- Dialogs ----------------
    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.setting_theme_dialog_title),
            options = ThemeMode.entries.map { it to stringResource(it.labelRes()) },
            selected = prefs.themeMode,
            onSelect = {
                vm.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.setting_language_dialog_title),
            options = AppLanguage.entries.map { it to stringResource(it.labelRes()) },
            selected = prefs.language,
            onSelect = {
                vm.setLanguage(it) // takes effect instantly: no restart needed
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    if (showDisclosure) {
        // Google Play "prominent disclosure" before sending the user to Accessibility settings.
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
