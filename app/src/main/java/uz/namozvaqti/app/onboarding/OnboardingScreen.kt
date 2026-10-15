package uz.namozvaqti.app.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.R
import uz.namozvaqti.app.ui.components.PermissionRow

/**
 * Reliability onboarding: unrestricted battery, OEM autostart, exact alarms, notifications,
 * lock in Recents, and the "Restricted settings" tip for sideloaded APKs.
 * Shown on first launch and again from Home → "Keep alarms reliable".
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    val ctx = LocalContext.current
    val oem = vm.oem
    val autostartConfirmed by vm.autostartConfirmed.collectAsStateWithLifecycle()

    var batteryOk by remember { mutableStateOf(OemSettings.isIgnoringBatteryOptimizations(ctx)) }
    var exactOk by remember { mutableStateOf((ctx.applicationContext as NamozApp).scheduler.canScheduleExact()) }
    var openFailed by remember { mutableStateOf(false) }

    // The user changes these in system screens; re-read when they come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryOk = OemSettings.isIgnoringBatteryOptimizations(ctx)
        exactOk = (ctx.applicationContext as NamozApp).scheduler.canScheduleExact()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Runs an OEM-specific opener; if the ROM doesn't have it, open App info and tell the user. */
    fun openOrFallback(opener: () -> Boolean) {
        openFailed = false
        if (!opener()) {
            openFailed = true
            OemSettings.openAppDetails(ctx)
        }
    }

    val brand = stringResource(oem.brandRes)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onb_intro))
        Text(
            stringResource(R.string.onb_device_detected, "$brand (${Build.MANUFACTURER} ${Build.MODEL})"),
            style = MaterialTheme.typography.labelLarge
        )

        if (openFailed) {
            Text(
                stringResource(R.string.onb_open_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // ---------- Step 1: battery ----------
        StepCard(
            title = stringResource(R.string.onb_step_battery_title),
            description = stringResource(R.string.onb_step_battery_desc)
        ) {
            Text(
                stringResource(if (batteryOk) R.string.onb_battery_status_ok else R.string.onb_battery_status_bad),
                color = if (batteryOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = { openOrFallback { OemSettings.openBatteryOptimizationList(ctx) } }) {
                Text(stringResource(R.string.onb_btn_battery_list))
            }
            if (OemSettings.hasOemBatteryScreen(oem)) {
                Button(onClick = { openOrFallback { OemSettings.openOemBattery(ctx, oem) } }) {
                    Text(stringResource(R.string.onb_btn_battery_oem, brand))
                }
            }
        }

        // ---------- Step 2: autostart ----------
        StepCard(
            title = stringResource(R.string.onb_step_autostart_title),
            description = stringResource(
                if (oem.hasAutostart) R.string.onb_step_autostart_desc else R.string.onb_autostart_not_needed
            )
        ) {
            if (oem.hasAutostart) {
                Button(onClick = { openOrFallback { OemSettings.openAutostart(ctx, oem) } }) {
                    Text(stringResource(R.string.onb_btn_autostart))
                }
                // Android gives apps no way to read this OEM toggle, so we ask the user to confirm.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autostartConfirmed, onCheckedChange = vm::setAutostartConfirmed)
                    Text(stringResource(R.string.onb_autostart_confirm))
                }
            }
        }

        // ---------- Step 3: exact alarms + notifications ----------
        StepCard(
            title = stringResource(R.string.onb_step_alarm_title),
            description = stringResource(R.string.onb_step_alarm_desc)
        ) {
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
        }

        // ---------- Step 4: lock in Recents ----------
        StepCard(
            title = stringResource(R.string.onb_step_lock_title),
            description = stringResource(R.string.onb_step_lock_desc)
        ) {}

        // ---------- Tip: restricted settings (sideloaded APKs, Android 13+) ----------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StepCard(
                title = stringResource(R.string.onb_step_restricted_title),
                description = stringResource(R.string.onb_step_restricted_desc)
            ) {
                OutlinedButton(onClick = { OemSettings.openAppDetails(ctx) }) {
                    Text(stringResource(R.string.onb_btn_app_info))
                }
            }
        }

        // ---------- Written, device-specific manual steps ----------
        StepCard(
            title = stringResource(R.string.onb_oem_guide_title, brand),
            description = stringResource(oem.guideRes)
        ) {}

        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_done))
        }
        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_skip))
        }
    }
}

@Composable
private fun StepCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
