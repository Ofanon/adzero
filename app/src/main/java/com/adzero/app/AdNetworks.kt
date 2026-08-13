package com.adzero.app

import android.content.Context
import java.io.File

/**
 * The domains we leave unanswered.
 *
 * This list comes from measurement, not theory: these are the hosts actually
 * contacted by real games while loading a rewarded ad.
 */
object AdNetworks {

    private val markers = listOf(
        // mediators — they drive the waterfall
        "applovin", "ironsrc", "ironsource", "supersonicads",
        // Unity ships its ad network and its game engine services under the
        // same domain. Blocking unity3d.com wholesale took out cloud config
        // and content delivery that games legitimately need, so only the ad
        // subdomains are listed.
        "unityads.unity3d.com", "iads.unity3d.com", "mediation.unity3d.com",
        "auction.unity3d.com", "adserver.unity3d.com",
        "admob", "googleads", "doubleclick", "googlesyndication",
        "fyber", "mopub", "appodeal",

        // ad networks
        "vungle", "adcolony", "chartboost", "inmobi", "inmobicdn", "tapjoy",
        "pangle", "bytedance", "mintegral", "mtgglobals", "startapp", "smaato",
        "bigo", "moloco", "adsmoloco", "liftoff", "liftoff-creatives", "nefta",
        "digitalturbine", "maticooads", "an.facebook.com",

        // real-time bidding
        "bidmachine", "3lift.com", "inner-active", "amazon-adsystem",
        "optimusbid", "lazybumblebee", "pubmatic", "rubiconproject",
        "adnxs", "adsrvr", "casalemedia", "openx", "smartadserver",
        "magnite", "sharethrough", "gumgum", "yieldmo", "teads",
        "adform", "pubnative", "onetag", "sovrn", "triplelift", "bidswitch",

        // measurement and attribution
        "appsflyer", "afafb.com", "adjust.com", "app-measurement.com",
        "gameanalytics", "kochava", "singular.net", "branch.io",
        "fundingchoicesmessages",

        // Caught in real play sessions, on top of the bench findings.
        "applvn.com", "axon.ai", "safedk", "maxesads", "maxmdb",
        "everestop", "yandexadexchange", "voodoo-adn", "voodoo-tech",
        "vervegroupinc", "adjoe-programmatic", "push-sdk", "uidsync",
        "doubleverify", "geoedge", "privacy-mgmt", "adjust.io", "tk0x1",

        // Throwaway domains on cheap TLDs, seen serving the "your phone has a
        // virus" kind of ad. Short-lived by design, which is exactly why the
        // learning below matters more than this list.
        "queencarlotta", "firwinds.site", "bytegle.site", "news-cdn.site",
        "ilyvo.com", "ammnlth.net", "youngle.tech",
    )

    /*
     * Deliberately NOT blocked, though they showed up next to ads:
     *   facebook.com   breaks Messenger and Instagram outright
     *   aliyuncs.com   Alibaba Cloud — also hosts the games' own assets
     *   googlevideo.com  YouTube playback
     *   revenuecat.com   in-app purchases; blocking it breaks paying users
     *   amanotes.*, balancy.dev, fastly-edge.com   the games' own backends
     * Blocking infrastructure to catch an ad is how a blocker earns a
     * reputation for breaking things.
     */

    /** Domains confirmed by the user from the suggestions. */
    private val custom = mutableSetOf<String>()
    private var file: File? = null

    /**
     * Domains the user has taken back off the blocklist.
     *
     * Until now "unblock" only worked on the user's own additions, which is
     * fine right up to the moment a game stops working because of one of the
     * built-in markers. Without an override the only remedy was to stop
     * protecting the whole app. This is checked before everything else, so a
     * person can always overrule the list shipped with the app.
     */
    private val allowed = mutableSetOf<String>()
    private var allowFile: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "custom_networks.txt")
        file = f
        val a = File(ctx.applicationContext.filesDir, "allowed_networks.txt")
        allowFile = a
        if (a.exists()) try {
            a.forEachLine { line ->
                line.trim().lowercase().takeIf { it.isNotEmpty() }?.let { allowed.add(it) }
            }
        } catch (_: Exception) {
        }
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                line.trim().lowercase().takeIf { it.isNotEmpty() }?.let { custom.add(it) }
            }
        } catch (_: Exception) {
        }
    }

    /** Takes a domain off the blocklist, whichever list put it there. */
    fun allow(domain: String) {
        val d = domain.trim().lowercase()
        if (d.isEmpty()) return
        // Also drop it from the user's own additions, otherwise the two lists
        // would disagree and the domain would come back on the next launch.
        synchronized(custom) {
            if (custom.remove(d)) {
                try { file?.writeText(custom.joinToString("\n", postfix = "\n")) } catch (_: Exception) {}
            }
        }
        synchronized(allowed) {
            if (!allowed.add(d)) return
            try { allowFile?.appendText("$d\n") } catch (_: Exception) {}
        }
    }

    fun blockAgain(domain: String) {
        val d = domain.trim().lowercase()
        synchronized(allowed) {
            if (!allowed.remove(d)) return
            try { allowFile?.writeText(allowed.joinToString("\n", postfix = "\n")) } catch (_: Exception) {}
        }
    }

    fun allowedDomains(): List<String> = synchronized(allowed) { allowed.toList().sorted() }

    fun add(domain: String) {
        val d = domain.trim().lowercase()
        if (d.isEmpty()) return
        synchronized(custom) {
            if (!custom.add(d)) return
            try { file?.appendText("$d\n") } catch (_: Exception) {}
        }
    }

    fun remove(domain: String) {
        val d = domain.trim().lowercase()
        synchronized(custom) {
            if (!custom.remove(d)) return
            try { file?.writeText(custom.joinToString("\n", postfix = "\n")) } catch (_: Exception) {}
        }
    }

    fun customDomains(): List<String> = synchronized(custom) { custom.toList().sorted() }

    /**
     * Trackers show you nothing — they report what you do. Worth counting
     * apart from ads, because it is the half nobody ever sees.
     */
    private val trackerMarkers = listOf(
        "appsflyer", "afafb.com", "adjust.com", "app-measurement.com",
        "gameanalytics", "kochava", "singular.net", "branch.io",
        "adikteev", "remerge", "jampp", "tenjin", "airbridge",
    )

    enum class Kind { NONE, AD, TRACKER }

    fun classify(host: String): Kind {
        val h = host.lowercase()
        // The user's overrides win over every list, including this app's own.
        if (synchronized(allowed) { allowed.any { it in h } }) return Kind.NONE
        if (trackerMarkers.any { it in h }) return Kind.TRACKER
        if (markers.any { it in h }) return Kind.AD
        val custom = synchronized(custom) { custom.any { it in h } }
        return if (custom) Kind.AD else Kind.NONE
    }

    fun matches(host: String): Boolean = classify(host) != Kind.NONE
}
