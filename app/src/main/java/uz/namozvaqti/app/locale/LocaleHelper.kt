package uz.namozvaqti.app.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import uz.namozvaqti.app.NamozApp

/**
 * Switches language WITHOUT restarting the app.
 *
 * - Compose UI: [ProvideAppLocale] swaps LocalContext/LocalConfiguration, so every stringResource()
 *   below it recomposes instantly when the language changes.
 * - Services / receivers / notifications: use [forApp] to get a Context that resolves strings in
 *   the language the user chose (NamozApp keeps it in memory, DataStore is the source of truth).
 */
object LocaleHelper {

    fun localizedContext(base: Context, language: AppLanguage): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(language.locale)
        return base.createConfigurationContext(config)
    }

    /** For non-Compose code: `LocaleHelper.forApp(context).getString(R.string.x)` */
    fun forApp(context: Context): Context {
        val app = context.applicationContext as NamozApp
        return localizedContext(app, app.currentLanguage)
    }
}

/**
 * Delegates everything to the real Context (so startActivity, system services, etc. keep working)
 * and only replaces Resources with ones configured for the chosen locale.
 */
private class LocalizedContext(base: Context, config: Configuration) : ContextWrapper(base) {
    private val localizedResources: Resources = base.createConfigurationContext(config).resources
    override fun getResources(): Resources = localizedResources
}

@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current

    val localized = remember(base, baseConfig, language) {
        val config = Configuration(baseConfig).apply { setLocale(language.locale) }
        LocalizedContext(base, config)
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration
    ) {
        content()
    }
}
