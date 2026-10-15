package uz.namozvaqti.app.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists launchable apps WITHOUT QUERY_ALL_PACKAGES.
 * Package visibility (Android 11+) comes from the <queries> block in AndroidManifest.xml:
 * every app that has a MAIN/LAUNCHER activity is visible to queryIntentActivities().
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val excluded = neverBlockablePackages(pm)

        pm.queryActivities(launcher)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName !in excluded }
            .distinctBy { it.packageName }
            .map { info ->
                val category = categoryOf(info.applicationInfo)
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    category = category,
                    suggested = category != AppCategory.OTHER || info.packageName in SuggestedApps.PACKAGES
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Safety net: apps the user must never be able to block, otherwise they could lock themselves
     * out of Settings (to disable our accessibility service), the phone dialer (emergencies) or
     * the launcher. We also exclude ourselves.
     */
    private fun neverBlockablePackages(pm: PackageManager): Set<String> {
        val result = HARDCODED_SAFE.toMutableSet()
        result += context.packageName
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), // every installed launcher
            Intent(Intent.ACTION_DIAL),                                   // dialers
            Intent(Settings.ACTION_SETTINGS)                              // Settings
        ).forEach { intent -> pm.queryActivities(intent).forEach { result += it.activityInfo.packageName } }
        return result
    }

    @Suppress("DEPRECATION")
    private fun categoryOf(info: ApplicationInfo?): AppCategory {
        if (info == null) return AppCategory.OTHER
        return when {
            info.category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            info.category == ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
            info.category == ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            info.flags and ApplicationInfo.FLAG_IS_GAME != 0 -> AppCategory.GAME // legacy marker
            else -> AppCategory.OTHER
        }
    }

    private fun PackageManager.queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, 0)
        }

    private companion object {
        val HARDCODED_SAFE = setOf(
            "com.android.settings", "com.android.systemui", "com.android.phone", "com.android.emergency",
            "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
            "com.samsung.android.incallui", "com.miui.securitycenter"
        )
    }
}
