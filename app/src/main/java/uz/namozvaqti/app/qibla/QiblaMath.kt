package uz.namozvaqti.app.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaMath {
    // Kaaba, Masjid al-Haram, Makkah
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206
    private const val EARTH_RADIUS_KM = 6371.0

    /** Initial great-circle bearing from (lat, lon) to the Kaaba, in degrees clockwise from TRUE north [0, 360). */
    fun bearingToKaaba(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLambda = Math.toRadians(KAABA_LON - lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Haversine distance in km. */
    fun distanceToKaabaKm(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(KAABA_LON - lon)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Maps any angle into [0, 360). */
    fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /** Signed smallest difference, mapped into [-180, 180). Example: 350 -> 10 gives +20, not -340. */
    fun shortestDelta(degrees: Float): Float = ((degrees + 540f) % 360f + 360f) % 360f - 180f
}
