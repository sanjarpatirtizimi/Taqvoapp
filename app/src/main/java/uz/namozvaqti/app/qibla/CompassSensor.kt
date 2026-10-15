package uz.namozvaqti.app.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import androidx.core.hardware.display.DisplayManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** [azimuthDegrees] is relative to MAGNETIC north, in [0, 360). */
data class CompassReading(val azimuthDegrees: Float, val accuracy: Int)

/**
 * Fully offline compass built on TYPE_ROTATION_VECTOR (sensor fusion of magnetometer +
 * accelerometer + gyroscope, much steadier than reading the raw magnetometer yourself).
 * The sensor is registered only while the flow is being collected, so it costs no battery
 * when the screen is not visible.
 */
class CompassSensor(private val context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = rotationSensor != null

    fun readings(): Flow<CompassReading> = callbackFlow {
        val sensor = rotationSensor
        val manager = sensorManager
        if (sensor == null || manager == null) {
            close()
            return@callbackFlow
        }

        val rotationVector = FloatArray(4)
        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Some devices report 5 values, which crashes getRotationMatrixFromVector on old builds.
                System.arraycopy(event.values, 0, rotationVector, 0, minOf(4, event.values.size))
                SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

                // Keep "azimuth" meaning "where the top of the screen points" in every orientation.
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)

                val azimuth = QiblaMath.normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
                trySend(CompassReading(azimuth, accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate() // if the UI is slower than the sensor, drop old values instead of queueing

    private fun displayRotation(): Int =
        DisplayManagerCompat.getInstance(context).getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
}
