package uz.namozvaqti.app.qibla

import android.app.Application
import android.hardware.GeomagneticField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.namozvaqti.app.NamozApp
import uz.namozvaqti.app.data.location.ResolvedLocation

data class QiblaUiState(
    val sensorAvailable: Boolean = true,
    /** Smoothed, unwrapped heading relative to TRUE north (declination applied). */
    val heading: Float = 0f,
    val hasHeading: Boolean = false,
    /** SensorManager.SENSOR_STATUS_* */
    val accuracy: Int = 3,
    /** Degrees clockwise from true north to the Kaaba, or null until a location is known. */
    val qiblaBearing: Float? = null,
    val distanceKm: Double? = null,
    val usingFallbackLocation: Boolean = false
)

class QiblaViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as NamozApp
    private val compass = CompassSensor(app)

    private val location = MutableStateFlow<ResolvedLocation?>(null)

    private data class Place(
        val bearing: Float,
        val distanceKm: Double,
        val declination: Float,
        val isFallback: Boolean
    )

    private data class Heading(val unwrapped: Float, val accuracy: Int)

    /** Computed once per location change (GeomagneticField is too heavy to rebuild per sensor event). */
    private val place: Flow<Place?> = location.map { loc ->
        loc?.let {
            Place(
                bearing = QiblaMath.bearingToKaaba(it.latitude, it.longitude).toFloat(),
                distanceKm = QiblaMath.distanceToKaabaKm(it.latitude, it.longitude),
                // Rotation vector gives MAGNETIC north; the Qibla bearing is relative to TRUE north.
                declination = GeomagneticField(
                    it.latitude.toFloat(), it.longitude.toFloat(), 0f, System.currentTimeMillis()
                ).declination,
                isFallback = it.isFallback
            )
        }
    }

    /** A fresh smoother per subscription, so reopening the screen never starts from a stale angle. */
    private val headings: Flow<Heading?> = flow<Heading?> {
        val smoother = HeadingSmoother(alpha = 0.15f)
        compass.readings().collect { emit(Heading(smoother.next(it.azimuthDegrees), it.accuracy)) }
    }.onStart { emit(null) }

    val uiState: StateFlow<QiblaUiState> = combine(place, headings) { place, heading ->
        QiblaUiState(
            sensorAvailable = compass.isAvailable,
            heading = (heading?.unwrapped ?: 0f) + (place?.declination ?: 0f),
            hasHeading = heading != null,
            accuracy = heading?.accuracy ?: 3,
            qiblaBearing = place?.bearing,
            distanceKm = place?.distanceKm,
            usingFallbackLocation = place?.isFallback ?: false
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(2_000),
        QiblaUiState(sensorAvailable = compass.isAvailable)
    )

    init {
        refreshLocation()
    }

    /** Fast path first (saved/last-known/Tashkent), then try for a fresh fix and update. */
    fun refreshLocation() {
        viewModelScope.launch {
            location.value = container.locationProvider.resolve()
            if (container.locationProvider.requestFreshLocation()) {
                location.value = container.locationProvider.resolve()
            }
        }
    }
}
