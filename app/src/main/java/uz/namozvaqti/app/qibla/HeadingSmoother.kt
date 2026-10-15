package uz.namozvaqti.app.qibla

/**
 * Exponential low-pass filter for compass angles.
 *
 * Plain smoothing breaks at the 359° -> 1° wrap-around (it would swing the long way round).
 * This filter works on the SHORTEST angular difference and returns an UNWRAPPED, continuous angle
 * (it may go below 0 or above 360). That is exactly what you want for a rotation animation:
 * the dial turns smoothly through north instead of spinning backwards.
 *
 * smaller alpha = smoother but laggier. ~0.12-0.2 feels right at SENSOR_DELAY_UI (~16 Hz).
 * Not thread-safe: use from a single coroutine.
 */
class HeadingSmoother(private val alpha: Float = 0.15f) {
    private var current: Float? = null

    fun next(rawDegrees: Float): Float {
        val previous = current
        if (previous == null) {
            current = rawDegrees
            return rawDegrees
        }
        val delta = QiblaMath.shortestDelta(rawDegrees - QiblaMath.normalize(previous))
        val smoothed = previous + alpha * delta
        current = smoothed
        return smoothed
    }

    fun reset() {
        current = null
    }
}
