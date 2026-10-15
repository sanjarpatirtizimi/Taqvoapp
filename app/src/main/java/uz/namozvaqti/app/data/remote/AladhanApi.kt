package uz.namozvaqti.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AladhanApi {

    /** One whole month of timings in a single call: https://aladhan.com/prayer-times-api#get-/calendar/-year-/-month- */
    @GET("v1/calendar/{year}/{month}")
    suspend fun getMonth(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("school") school: Int,
        @Query("tune") tune: String? = null,
        @Query("timezonestring") timezone: String? = null
    ): AladhanCalendarResponse
}

@Serializable
data class AladhanCalendarResponse(
    val code: Int = 0,
    val status: String = "",
    val data: List<AladhanDay> = emptyList()
)

@Serializable
data class AladhanDay(
    val timings: AladhanTimings,
    val date: AladhanDate,
    val meta: AladhanMeta
)

/** Values look like "05:12 (+05)" - the mapper keeps only HH:mm. */
@Serializable
data class AladhanTimings(
    @SerialName("Fajr") val fajr: String,
    @SerialName("Sunrise") val sunrise: String,
    @SerialName("Dhuhr") val dhuhr: String,
    @SerialName("Asr") val asr: String,
    @SerialName("Maghrib") val maghrib: String,
    @SerialName("Isha") val isha: String
)

@Serializable
data class AladhanDate(val gregorian: AladhanGregorian)

/** "dd-MM-yyyy" */
@Serializable
data class AladhanGregorian(val date: String)

@Serializable
data class AladhanMeta(val timezone: String)
