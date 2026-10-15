package uz.namozvaqti.app.data

/** Calculation settings sent to Aladhan. Tweak here to match the official Uzbekistan timetable. */
object PrayerConfig {
    /** 1 = Hanafi (affects Asr only). Required by the project spec. */
    const val SCHOOL_HANAFI = 1

    /**
     * Aladhan calculation method. 3 = Muslim World League (Fajr 18°, Isha 17°).
     * TODO: compare a week of output with the Muftiyat of Uzbekistan timetable; if it differs, either
     * pick another method id or use TUNE below (per-prayer minute offsets).
     */
    const val METHOD = 3

    /**
     * Minute offsets in Aladhan's order: Imsak,Fajr,Sunrise,Dhuhr,Asr,Sunset,Maghrib,Isha,Midnight.
     * null = no adjustment. Example: "0,0,0,2,0,0,2,0,0".
     */
    val TUNE: String? = null

    // Fallback: Tashkent
    const val TASHKENT_LAT = 41.2995
    const val TASHKENT_LON = 69.2401
    const val TASHKENT_TZ = "Asia/Tashkent"
}
