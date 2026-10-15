package uz.namozvaqti.app.protection.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.locale.ProvideAppLocale
import uz.namozvaqti.app.protection.ProtectionPermissions
import uz.namozvaqti.app.protection.ProtectionSession

/** Layer 1: adds / removes the full-screen TYPE_APPLICATION_OVERLAY window. Main thread only. */
class PrayerOverlayController(
    private val context: Context,
    private val onExitConfirmed: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: OverlayRoot? = null
    private var owner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = root != null

    fun show(session: ProtectionSession): Boolean {
        if (root != null) return true
        if (!ProtectionPermissions.canDrawOverlays(context)) return false

        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val container = OverlayRoot(context)
        lifecycleOwner.attachTo(container)

        container.addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    // The overlay lives in a Service, so apply the in-app language explicitly.
                    ProvideAppLocale((context.applicationContext as NamozApp).currentLanguage) {
                        PrayerOverlayScreen(session = session, onExitConfirmed = onExitConfirmed)
                    }
                }
            }
        )

        try {
            windowManager.addView(container, layoutParams())
        } catch (t: Throwable) { // BadTokenException if permission revoked mid-flight
            lifecycleOwner.destroy()
            return false
        }
        root = container
        owner = lifecycleOwner
        return true
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        owner?.destroy()
        root = null
        owner = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // No FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCHABLE => the overlay receives all touches and key events.
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    /** Swallows BACK so it cannot be used to peek behind the overlay. */
    private class OverlayRoot(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }
}
