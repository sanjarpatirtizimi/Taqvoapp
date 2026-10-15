package uz.namozvaqti.app.protection.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp

/**
 * Layer 2. Reads ONLY the package name of the window that just came to the front.
 * (canRetrieveWindowContent=false in the config, so we cannot read screen content anyway.)
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var blacklist: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = (application as NamozApp).preferences
        scope.launch { prefs.blacklist.collect { blacklist = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Cheap checks first - this callback runs on every window change.
        if (!FocusBlocker.shouldBlock(pkg, packageName, blacklist)) return

        FocusBlocker.block(this) { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
