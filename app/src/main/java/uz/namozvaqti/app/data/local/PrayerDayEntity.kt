package uz.namozvaqti.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import uz.namozvaqti.app.domain.Prayer

/**
 * One row per calendar day. Times are stored as absolute epoch millis (already converted using
 * the timezone Aladhan returned), so the scheduler never has to deal with timezones/parsing.
 */
@Entity(tableName = "prayer_days")
data class PrayerDayEntity(
    @PrimaryKey val date: String, // ISO yyyy-MM-dd (sorts lexicographically)
    val fajr: Long,
    val sunrise: Long,
    val dhuhr: Long,
    val asr: Long,
    val maghrib: Long,
    val isha: Long,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Long
) {
    fun timeOf(prayer: Prayer): Long = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}
