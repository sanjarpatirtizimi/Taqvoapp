package uz.namozvaqti.app.protection.blocker

import android.content.Context
import android.widget.Toast
import uz.namozvaqti.app.R
import uz.namozvaqti.app.locale.LocaleHelper
import uz.namozvaqti.app.protection.ProtectionStateStore

/** Decision + feedback shared by the Accessibility path and the UsageStats fallback path. */
object FocusBlocker {
    private const val DEBOUNCE_MS = 1_500L
    private var lastBlockAt = 0L

    fun shouldBlock(foregroundPkg: String, ownPkg: String, blacklist: Set<String>): Boolean =
        foregroundPkg != ownPkg &&
            foregroundPkg in blacklist &&
            ProtectionStateStore.isBlockingActive()

    /** Must be called on the main thread. [goHome] is supplied by the caller's mechanism. */
    fun block(context: Context, goHome: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < DEBOUNCE_MS) return
        lastBlockAt = now
        goHome()
        val message = LocaleHelper.forApp(context).getString(R.string.toast_focus_on_prayer)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
