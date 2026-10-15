package uz.namozvaqti.app.domain

import androidx.annotation.StringRes
import uz.namozvaqti.app.R

/** The five obligatory prayers. Sunrise is shown in the UI but never gets an alarm. */
enum class Prayer(@StringRes val nameRes: Int) {
    FAJR(R.string.prayer_fajr),
    DHUHR(R.string.prayer_dhuhr),
    ASR(R.string.prayer_asr),
    MAGHRIB(R.string.prayer_maghrib),
    ISHA(R.string.prayer_isha);

    companion object {
        fun fromName(name: String?): Prayer? = entries.firstOrNull { it.name == name }
    }
}
