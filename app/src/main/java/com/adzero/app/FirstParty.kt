package com.adzero.app

/**
 * Apps whose ads no DNS blocker can remove, including this one.
 *
 * Everything AdZero does rests on ads arriving from servers that only serve
 * ads. A game asks applvn.com for a video; silencing that name kills the video
 * and breaks nothing else.
 *
 * These apps are built the other way round. The ad and the content come from
 * the same server, in the same response: a YouTube ad streams from
 * googlevideo.com, which is where the video you wanted also comes from, and a
 * sponsored post is a field in the same feed as the real posts. There is no
 * name to silence — only one name for both, and blocking it stops the app
 * working at all. That is why googlevideo.com has been on the list of domains
 * AdZero refuses to touch since the first day.
 *
 * It is not an oversight by them: when you are both the publisher and the ad
 * network, you have no reason to split into two addresses.
 *
 * So this exists to make the app honest rather than capable. Offering somebody
 * a list of servers to block after a YouTube ad lets them conclude AdZero is
 * broken, when the truth is that this particular ad was never reachable.
 */
object FirstParty {

    /**
     * VIDEO and FEED cannot be touched at all. MIXED is the honest middle:
     * Google's search results carry their ads on the same server as the
     * results, so those are out of reach — but the sites you open from there
     * carry ordinary third-party ads, and those are removed like anywhere
     * else. Saying "Google cannot be blocked" was too absolute.
     */
    enum class Kind { VIDEO, FEED, MIXED }

    private val known = mapOf(
        "com.google.android.youtube" to Kind.VIDEO,
        "com.google.android.apps.youtube.music" to Kind.VIDEO,
        "com.google.android.apps.youtube.kids" to Kind.VIDEO,
        "tv.twitch.android.app" to Kind.VIDEO,
        "com.spotify.music" to Kind.VIDEO,
        "com.netflix.mediaclient" to Kind.VIDEO,

        "com.instagram.android" to Kind.FEED,
        "com.facebook.katana" to Kind.FEED,
        "com.facebook.lite" to Kind.FEED,
        "com.pinterest" to Kind.FEED,
        "com.zhiliaoapp.musically" to Kind.FEED,      // TikTok
        "com.ss.android.ugc.trill" to Kind.FEED,      // TikTok, other regions
        "com.snapchat.android" to Kind.FEED,
        "com.twitter.android" to Kind.FEED,
        "com.reddit.frontpage" to Kind.FEED,
        "com.linkedin.android" to Kind.FEED,
        "com.amazon.mShop.android.shopping" to Kind.FEED,
        "com.google.android.googlequicksearchbox" to Kind.MIXED,
    )

    fun kindOf(pkg: String): Kind? = known[pkg]

    /** True when nothing at all can be done about this app's own ads. */
    fun hopeless(pkg: String): Boolean = known[pkg].let {
        it == Kind.VIDEO || it == Kind.FEED
    }
}
