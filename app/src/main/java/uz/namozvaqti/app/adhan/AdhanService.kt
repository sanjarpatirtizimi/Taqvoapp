package uz.namozvaqti.app.adhan

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.namozvaqti.app.R
import uz.namozvaqti.app.domain.Prayer
import uz.namozvaqti.app.locale.LocaleHelper
import uz.namozvaqti.app.ui.MainActivity

/**
 * Plays the adhan as a mediaPlayback foreground service, so playback survives Doze and the user
 * can stop it from the notification.
 *
 * Audio files (optional, lowercase names, put in app/src/main/res/raw/):
 *   adhan.mp3        - used for every prayer
 *   adhan_fajr.mp3   - optional, used for Fajr instead
 * If none exist, the device's default alarm sound is used so the build never breaks.
 */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAdhan()
            return START_NOT_STICKY
        }
        val prayer = Prayer.fromName(intent?.getStringExtra(EXTRA_PRAYER)) ?: Prayer.DHUHR

        // Must be called within ~5 s of startForegroundService().
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(prayer),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        play(prayer)
        return START_NOT_STICKY
    }

    private fun play(prayer: Prayer) {
        releasePlayer()

        val attrs = AudioAttributes.Builder()
            // USAGE_ALARM follows the ALARM volume and still sounds when the ringer is on silent/vibrate.
            // Switch to USAGE_NOTIFICATION_EVENT / USAGE_MEDIA if you want the adhan to respect silent mode.
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioManager = getSystemService(AudioManager::class.java).also { am ->
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .build()
                .also { am.requestAudioFocus(it) }
        }

        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setWakeMode(this@AdhanService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stopAdhan() }
            setOnErrorListener { _, _, _ -> stopAdhan(); true }
            try {
                setDataSource(this@AdhanService, resolveAudioUri(prayer))
                prepareAsync()
            } catch (e: Exception) {
                stopAdhan()
            }
        }
    }

    private fun resolveAudioUri(prayer: Prayer): Uri {
        val candidates = if (prayer == Prayer.FAJR) listOf("adhan_fajr", "adhan") else listOf("adhan")
        for (name in candidates) {
            // getIdentifier keeps compilation working even before you add the mp3 files.
            val id = resources.getIdentifier(name, "raw", packageName)
            if (id != 0) return Uri.parse("android.resource://$packageName/$id")
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun stopAdhan() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.runCatching { if (isPlaying) stop(); release() }
        player = null
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun buildNotification(prayer: Prayer): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = LocaleHelper.forApp(this) // the language chosen in Settings
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_namoz)
            .setContentTitle(text.getString(R.string.notif_adhan_title, text.getString(prayer.nameRes)))
            .setContentText(text.getString(R.string.notif_adhan_text))
            .setContentIntent(open)
            .addAction(0, text.getString(R.string.notif_adhan_stop), stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "adhan"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "uz.namozvaqti.app.action.STOP_ADHAN"
        private const val EXTRA_PRAYER = "prayer"

        fun start(context: Context, prayer: Prayer) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AdhanService::class.java).putExtra(EXTRA_PRAYER, prayer.name)
            )
        }
    }
}
