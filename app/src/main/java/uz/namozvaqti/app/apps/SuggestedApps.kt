package uz.namozvaqti.app.apps

/**
 * Popular distracting apps (with emphasis on those common in Uzbekistan), used for the
 * "Select suggested" shortcut. Android's own app-category metadata is often missing, so a
 * hand-maintained list catches what it misses. Nothing is blocked unless the user taps Select/Save.
 */
object SuggestedApps {
    val PACKAGES: Set<String> = setOf(
        // Social / messaging
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.instagram.android", "com.facebook.katana", "com.facebook.lite",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", // TikTok
        "com.snapchat.android", "com.twitter.android", "com.whatsapp",
        "com.vkontakte.android", "ru.ok.android", "video.like", "com.reddit.frontpage",
        // Video / streaming
        "com.google.android.youtube", "com.netflix.mediaclient",
        // Games
        "com.tencent.ig", "com.pubg.imobile", "com.mobile.legends", "com.dts.freefireth",
        "com.roblox.client", "com.supercell.clashofclans", "com.supercell.brawlstars",
        "com.supercell.clashroyale", "com.mojang.minecraftpe",
        "com.activision.callofduty.shooter", "com.king.candycrushsaga"
    )
}
