package uz.namozvaqti.app.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import uz.namozvaqti.app.R
import java.util.Locale

enum class Oem(
    @StringRes val brandRes: Int,
    @StringRes val guideRes: Int,
    /** Does this OEM have a separate "Autostart" screen? (Samsung does not.) */
    val hasAutostart: Boolean
) {
    XIAOMI(R.string.oem_xiaomi, R.string.onb_guide_xiaomi, true),
    SAMSUNG(R.string.oem_samsung, R.string.onb_guide_samsung, false),
    OPPO(R.string.oem_oppo, R.string.onb_guide_oppo, true),
    HUAWEI(R.string.oem_huawei, R.string.onb_guide_huawei, true),
    VIVO(R.string.oem_vivo, R.string.onb_guide_vivo, true),
    OTHER(R.string.oem_other, R.string.onb_guide_other, false)
}

/**
 * Opens vendor-specific battery / autostart screens.
 *
 * IMPORTANT: these activities are private, undocumented and change between ROM versions
 * (MIUI vs HyperOS, ColorOS vs OxygenOS, EMUI vs HarmonyOS...). So every screen has a list of
 * candidates tried in order, each inside try/catch, and callers MUST handle `false` by falling back
 * to [openAppDetails] + the written manual guide. No permission or <queries> entry is needed to
 * start an explicit component.
 */
object OemSettings {

    fun detect(): Oem {
        val id = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase(Locale.ROOT)
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in id } -> Oem.XIAOMI
            "samsung" in id -> Oem.SAMSUNG
            listOf("oppo", "realme", "oneplus").any { it in id } -> Oem.OPPO
            listOf("huawei", "honor").any { it in id } -> Oem.HUAWEI
            listOf("vivo", "iqoo").any { it in id } -> Oem.VIVO
            else -> Oem.OTHER
        }
    }

    fun hasOemBatteryScreen(oem: Oem): Boolean = oem != Oem.OTHER

    /** @return true if a vendor-specific Autostart screen was opened. */
    fun openAutostart(context: Context, oem: Oem): Boolean =
        tryStart(context, autostartIntents(context, oem))

    /** @return true if a vendor-specific battery screen was opened. */
    fun openOemBattery(context: Context, oem: Oem): Boolean =
        tryStart(context, batteryIntents(context, oem))

    /** Standard Android list of apps with battery optimization. Needs no permission. */
    fun openBatteryOptimizationList(context: Context): Boolean =
        tryStart(context, listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))

    /** Universal fallback: this app's "App info" page. */
    fun openAppDetails(context: Context): Boolean =
        tryStart(
            context,
            listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        )

    /** Standard Android check (no permission needed). OEM-specific restrictions are NOT visible here. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    // ------------------------------------------------------------------------------------------

    private fun tryStart(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // ActivityNotFoundException (screen doesn't exist on this ROM) or
                // SecurityException (screen exists but isn't exported) -> try the next candidate.
            }
        }
        return false
    }

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    private fun autostartIntents(context: Context, oem: Oem): List<Intent> = when (oem) {
        Oem.XIAOMI -> listOf(
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        Oem.OPPO -> listOf(
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            component("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity")
        )
        Oem.HUAWEI -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )
        Oem.VIVO -> listOf(
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        )
        Oem.SAMSUNG, Oem.OTHER -> emptyList()
    }

    private fun batteryIntents(context: Context, oem: Oem): List<Intent> = when (oem) {
        Oem.XIAOMI -> {
            val label = context.applicationInfo.loadLabel(context.packageManager).toString()
            listOf(
                component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    .putExtra("package_name", context.packageName)
                    .putExtra("package_label", label),
                component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
            )
        }
        Oem.SAMSUNG -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            component("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        Oem.OPPO -> listOf(
            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
            component("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity")
        )
        Oem.HUAWEI -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )
        Oem.VIVO -> listOf(
            component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
            component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity")
        )
        Oem.OTHER -> emptyList()
    }
}
