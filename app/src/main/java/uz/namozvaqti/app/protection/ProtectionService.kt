package uz.namozvaqti.app.protection

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.R
import uz.namozvaqti.app.protection.blocker.FocusBlocker
import uz.namozvaqti.app.protection.blocker.UsageStatsForegroundDetector
import uz.namozvaqti.app.protection.overlay.PrayerOverlayController
import uz.namozvaqti.app.ui.MainActivity

/**
 * Owns one prayer "focus session":
 *  - Layer 1: overlay window (until timer ends or the 15s emergency exit completes)
 *  - Layer 2 fallback: UsageStats polling if the Accessibility service is not enabled
 *  - Session timer: ends everything automatically, so the app can never lock the user in.
 */
class ProtectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var detectorJob: Job? = null
    private lateinit var overlay: PrayerOverlayController

    override fun onCreate() {
        super.onCreate()
        overlay = PrayerOverlayController(this) { onEmergencyExit() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            endSession()
            return START_NOT_STICKY
        }
        val prayer = intent?.getStringExtra(EXTRA_PRAYER) ?: getString(R.string.prayer_generic)
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, DEFAULT_DURATION_MS) ?: DEFAULT_DURATION_MS

        // Must be called within ~5s of startForegroundService().
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(prayer),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        beginSession(prayer, durationMs)
        return START_NOT_STICKY // never resurrect a session after process death
    }

    private fun beginSession(prayer: String, durationMs: Long) {
        timerJob?.cancel(); detectorJob?.cancel(); overlay.hide()

        timerJob = scope.launch {
            val prefs = (application as NamozApp).preferences
            if (!prefs.protectionEnabled.first()) { // user opt-in master switch
                endSession(); return@launch
            }
            val now = System.currentTimeMillis()
            val session = ProtectionSession(prayer, now, now + durationMs)
            ProtectionStateStore.start(session)

            overlay.show(session) // returns false if SYSTEM_ALERT_WINDOW wasn't granted; Layer 2 still runs
            startUsageStatsFallback()

            delay(durationMs)
            endSession()
        }
    }

    /** Layer 2 fallback, used only when AccessibilityService isn't enabled but Usage access is. */
    private fun startUsageStatsFallback() {
        val prefs = (application as NamozApp).preferences
        val blacklist = prefs.blacklist.stateIn(scope, SharingStarted.Eagerly, emptySet())
        val detector = UsageStatsForegroundDetector(this)

        detectorJob = scope.launch {
            while (isActive) {
                val a11yOn = ProtectionPermissions.isAccessibilityEnabled(this@ProtectionService)
                if (!a11yOn && ProtectionPermissions.hasUsageAccess(this@ProtectionService)) {
                    val pkg = detector.currentForegroundPackage()
                    if (pkg != null && FocusBlocker.shouldBlock(pkg, packageName, blacklist.value)) {
                        FocusBlocker.block(this@ProtectionService) { goHome() }
                    }
                }
                delay(1_000)
            }
        }
    }

    private fun onEmergencyExit() {
        overlay.hide()
        ProtectionStateStore.markOverlayDismissed() // Layer 2 stays active until the session ends
    }

    private fun endSession() {
        timerJob?.cancel(); detectorJob?.cancel()
        overlay.hide()
        ProtectionStateStore.end()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun buildNotification(prayer: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProtectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_namoz)
            .setContentTitle(getString(R.string.notif_title_session, prayer))
            .setContentText(getString(R.string.notif_text_session))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_end_session), stop) // second, always-available escape hatch
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        overlay.hide()
        ProtectionStateStore.end()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "protection_session"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "uz.namozvaqti.app.action.STOP_SESSION"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_DURATION_MS = "duration_ms"
        const val DEFAULT_DURATION_MS = 20 * 60_000L

        fun start(context: Context, prayerName: String, durationMs: Long = DEFAULT_DURATION_MS) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProtectionService::class.java)
                    .putExtra(EXTRA_PRAYER, prayerName)
                    .putExtra(EXTRA_DURATION_MS, durationMs)
            )
        }
    }
}
