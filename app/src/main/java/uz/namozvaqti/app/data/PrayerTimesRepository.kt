package uz.namozvaqti.app.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import uz.namozvaqti.app.data.local.PrayerDao
import uz.namozvaqti.app.data.local.PrayerDayEntity
import uz.namozvaqti.app.data.location.LocationProvider
import uz.namozvaqti.app.data.location.ResolvedLocation
import uz.namozvaqti.app.data.remote.AladhanApi
import uz.namozvaqti.app.data.remote.AladhanDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrayerTimesRepository(
    private val api: AladhanApi,
    private val dao: PrayerDao,
    private val locationProvider: LocationProvider
) {
    fun observeDay(date: LocalDate): Flow<PrayerDayEntity?> = dao.observeDay(date.toString())

    /** [limit] days starting at [date] (used by Home: today + tomorrow for the countdown). */
    fun observeFrom(date: LocalDate, limit: Int): Flow<List<PrayerDayEntity>> =
        dao.observeFrom(date.toString(), limit)

    /**
     * Downloads the current + next month and upserts them into Room (cache survives offline).
     * Returns true if at least one month was fetched. On total failure the old cache is untouched.
     */
    suspend fun refresh(): Boolean {
        val location = locationProvider.resolve()
        val today = LocalDate.now()
        var anySuccess = false

        for (month in listOf(today, today.plusMonths(1))) {
            try {
                val response = api.getMonth(
                    year = month.year,
                    month = month.monthValue,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    method = PrayerConfig.METHOD,
                    school = PrayerConfig.SCHOOL_HANAFI,
                    tune = PrayerConfig.TUNE,
                    timezone = if (location.isFallback) PrayerConfig.TASHKENT_TZ else null
                )
                val now = System.currentTimeMillis()
                val entities = response.data.mapNotNull { it.toEntity(location, now) }
                if (entities.isNotEmpty()) {
                    dao.upsertAll(entities)
                    anySuccess = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // IOException, HttpException, SerializationException...
                Log.w(TAG, "Aladhan fetch failed for $month", e)
            }
        }

        if (anySuccess) dao.deleteBefore(today.minusDays(1).toString())
        return anySuccess
    }

    private fun AladhanDay.toEntity(location: ResolvedLocation, fetchedAt: Long): PrayerDayEntity? =
        runCatching {
            val zone = ZoneId.of(meta.timezone)
            val day = LocalDate.parse(date.gregorian.date, DATE_FORMAT)
            fun epoch(raw: String): Long =
                day.atTime(LocalTime.parse(raw.trim().take(5))).atZone(zone).toInstant().toEpochMilli()

            PrayerDayEntity(
                date = day.toString(),
                fajr = epoch(timings.fajr),
                sunrise = epoch(timings.sunrise),
                dhuhr = epoch(timings.dhuhr),
                asr = epoch(timings.asr),
                maghrib = epoch(timings.maghrib),
                isha = epoch(timings.isha),
                timezone = meta.timezone,
                latitude = location.latitude,
                longitude = location.longitude,
                fetchedAt = fetchedAt
            )
        }.onFailure { Log.w(TAG, "Skipping malformed day: $date", it) }.getOrNull()

    private companion object {
        const val TAG = "PrayerTimesRepo"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}
