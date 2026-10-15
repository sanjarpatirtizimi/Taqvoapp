package uz.namozvaqti.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PrayerDayEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "namoz.db")
                // This is a pure cache that is re-downloadable, so wiping on schema change is acceptable.
                .fallbackToDestructiveMigration()
                .build()
    }
}
