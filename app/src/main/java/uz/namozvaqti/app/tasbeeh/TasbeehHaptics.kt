package uz.namozvaqti.app.tasbeeh

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Requires <uses-permission android:name="android.permission.VIBRATE"/> (already in the manifest). */
class TasbeehHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val canVibrate get() = vibrator?.hasVibrator() == true
    private val hasAmplitudeControl get() = vibrator?.hasAmplitudeControl() == true

    /** Short, light tick on every tap. */
    fun tick() {
        if (!canVibrate) return
        val amplitude = if (hasAmplitudeControl) 70 else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator?.vibrate(VibrationEffect.createOneShot(TICK_MS, amplitude))
    }

    /** Distinct, long double-pulse when a target multiple (33, 66, 99, ...) is reached. */
    fun milestone() {
        if (!canVibrate) return
        val timings = longArrayOf(0, 250, 120, 500)
        val effect = if (hasAmplitudeControl) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255), NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(timings, NO_REPEAT) // on/off pattern only
        }
        vibrator?.vibrate(effect)
    }

    private companion object {
        const val TICK_MS = 14L
        const val NO_REPEAT = -1
    }
}
