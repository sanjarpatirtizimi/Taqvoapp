package uz.namozvaqti.app.apps

enum class AppCategory { SOCIAL, GAME, VIDEO, OTHER }

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    /** True if the category is social/game/video OR the package is in [SuggestedApps]. */
    val suggested: Boolean
)
