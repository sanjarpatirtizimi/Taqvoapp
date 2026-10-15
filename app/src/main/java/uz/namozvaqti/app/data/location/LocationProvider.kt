package uz.namozvaqti.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import uz.namozvaqti.app.data.AppSettings
import uz.namozvaqti.app.data.PrayerConfig
import kotlin.coroutines.resume

data class ResolvedLocation(val latitude: Double, val longitude: Double, val isFallback: Boolean)

/**
 * Uses the platform LocationManager (no Google Play Services needed - important for Huawei and
 * other GMS-less devices).
 *
 * Resolution order: last known device location -> last location saved from the foreground
 * -> Tashkent. Background workers cannot actively request location without the background
 * location permission, so they rely on the saved value.
 */
class LocationProvider(
    private val context: Context,
    private val settings: AppSettings
) {

    suspend fun resolve(): ResolvedLocation {
        lastKnownLocation()?.let {
            settings.saveLocation(it.latitude, it.longitude)
            return ResolvedLocation(it.latitude, it.longitude, isFallback = false)
        }
        settings.savedLocation.first()?.let { (lat, lon) ->
            return ResolvedLocation(lat, lon, isFallback = false)
        }
        return ResolvedLocation(PrayerConfig.TASHKENT_LAT, PrayerConfig.TASHKENT_LON, isFallback = true)
    }

    /** Foreground only: actively asks the OS for a fresh fix (10 s timeout) and saves it. */
    @SuppressLint("MissingPermission")
    suspend fun requestFreshLocation(timeoutMs: Long = 10_000): Boolean {
        if (!hasLocationPermission()) return false
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return false

        val location = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        lm, provider, signal, ContextCompat.getMainExecutor(context)
                    ) { cont.resume(it) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
        } ?: return false

        settings.saveLocation(location.latitude, location.longitude)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
