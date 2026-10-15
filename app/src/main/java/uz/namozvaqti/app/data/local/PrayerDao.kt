package uz.namozvaqti.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {

    @Upsert
    suspend fun upsertAll(days: List<PrayerDayEntity>)

    @Query("SELECT * FROM prayer_days WHERE date = :date")
    fun observeDay(date: String): Flow<PrayerDayEntity?>

    @Query("SELECT * FROM prayer_days WHERE date >= :from ORDER BY date ASC LIMIT :limit")
    fun observeFrom(from: String, limit: Int): Flow<List<PrayerDayEntity>>

    @Query("SELECT * FROM prayer_days WHERE date >= :from ORDER BY date ASC LIMIT :limit")
    suspend fun getFrom(from: String, limit: Int): List<PrayerDayEntity>

    @Query("DELETE FROM prayer_days WHERE date < :before")
    suspend fun deleteBefore(before: String)
}
