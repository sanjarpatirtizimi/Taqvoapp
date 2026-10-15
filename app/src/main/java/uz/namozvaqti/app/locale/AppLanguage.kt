package uz.namozvaqti.app.locale

import java.util.Locale

/**
 * The 4 languages the user can pick inside the app.
 * Resource folders: values (en), values-ru, values-uz (Latin), values-b+uz+Cyrl (Cyrillic).
 */
enum class AppLanguage(val tag: String) {
    EN("en"),
    RU("ru"),
    UZ("uz"),
    UZ_CYRL("uz-Cyrl");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromName(name: String?): AppLanguage? = entries.firstOrNull { it.name == name }

        /** Used until the user picks a language: follow the phone, fall back to English. */
        fun systemDefault(): AppLanguage {
            val system = Locale.getDefault()
            return when (system.language) {
                "ru" -> RU
                "uz" -> if (system.script.equals("Cyrl", ignoreCase = true)) UZ_CYRL else UZ
                else -> EN
            }
        }
    }
}
