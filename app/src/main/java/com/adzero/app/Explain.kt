package com.adzero.app

import android.content.Context

/**
 * Says in plain words what a domain is.
 *
 * A list of hostnames tells a person nothing. Worse, it invites the wrong
 * conclusion: someone who sees unity3d.com next to a big red counter assumes
 * their game engine is spying on them, and someone who sees a hundred lines
 * scroll past assumes the app is the tracker. Both were real reactions from
 * the first person outside the project to use it.
 *
 * So every domain the app displays can be tapped, and answers three questions:
 * who owns it, what it does, and why it is or is not silenced.
 */
object Explain {

    enum class Kind { MEDIATION, NETWORK, BIDDING, TRACKER, SHADY, ENGINE, UNKNOWN }

    /**
     * How much this server actually costs the person holding the phone.
     *
     * The categories above are accurate but they are still industry words, and
     * knowing that something is "real-time bidding" helps nobody decide
     * anything. Three levels do: it leaves you alone, it shows you ads, or it
     * is working against you. Colour first, words second, detail last.
     */
    enum class Level { NONE, ADS, BAD }

    fun levelOf(kind: Kind): Level = when (kind) {
        // Not advertising at all. The game needs these to run.
        Kind.ENGINE, Kind.UNKNOWN -> Level.NONE
        // Advertising: annoying, and it costs you time and data, nothing worse.
        Kind.MEDIATION, Kind.NETWORK, Kind.BIDDING -> Level.ADS
        // Follows you, or lies to you. A tracker shows you nothing at all,
        // which is precisely what makes it worth ranking above a banner.
        Kind.TRACKER, Kind.SHADY -> Level.BAD
    }

    fun levelColour(level: Level): Int = when (level) {
        Level.NONE -> Ui.LIME_A
        Level.ADS -> android.graphics.Color.parseColor("#F2B43D")
        Level.BAD -> Ui.RED_A
    }

    fun levelLabel(ctx: Context, level: Level): String = ctx.getString(
        when (level) {
            Level.NONE -> R.string.level_none
            Level.ADS -> R.string.level_ads
            Level.BAD -> R.string.level_bad
        }
    )

    fun levelText(ctx: Context, level: Level): String = ctx.getString(
        when (level) {
            Level.NONE -> R.string.level_none_text
            Level.ADS -> R.string.level_ads_text
            Level.BAD -> R.string.level_bad_text
        }
    )

    class Card(val owner: String, val kind: Kind, val blocked: Boolean)

    /**
     * Marker to owner. The order matters: the first match wins, so anything
     * specific has to come before the domain it lives under — the Unity ad
     * subdomains before Unity's engine services, for one.
     */
    private val known: List<Triple<String, String, Kind>> = listOf(
        // Mediation — the layer that runs the auction between the networks.
        Triple("applovin", "AppLovin MAX", Kind.MEDIATION),
        Triple("applvn", "AppLovin", Kind.MEDIATION),
        Triple("ironsrc", "ironSource LevelPlay", Kind.MEDIATION),
        Triple("ironsource", "ironSource LevelPlay", Kind.MEDIATION),
        Triple("supersonicads", "ironSource", Kind.MEDIATION),
        Triple("fyber", "Fyber (Digital Turbine)", Kind.MEDIATION),
        Triple("mopub", "MoPub", Kind.MEDIATION),
        Triple("appodeal", "Appodeal", Kind.MEDIATION),
        Triple("safedk", "SafeDK (AppLovin)", Kind.MEDIATION),
        Triple("maxesads", "AppLovin MAX", Kind.MEDIATION),
        Triple("maxmdb", "AppLovin MAX", Kind.MEDIATION),

        // Unity: ads first, engine second — see the note above.
        Triple("unityads", "Unity Ads", Kind.NETWORK),
        Triple("iads.unity3d", "Unity Ads", Kind.NETWORK),
        Triple("mediation.unity3d", "Unity Ads", Kind.MEDIATION),
        Triple("auction.unity3d", "Unity Ads", Kind.BIDDING),
        Triple("adserver.unity3d", "Unity Ads", Kind.NETWORK),
        Triple("unity3d", "Unity", Kind.ENGINE),
        Triple("unity.com", "Unity", Kind.ENGINE),

        // Ad networks — the ones that actually hold the video to play.
        Triple("admob", "Google AdMob", Kind.NETWORK),
        Triple("googleads", "Google Ads", Kind.NETWORK),
        Triple("doubleclick", "Google (DoubleClick)", Kind.NETWORK),
        Triple("googlesyndication", "Google AdSense", Kind.NETWORK),
        Triple("vungle", "Vungle (Liftoff)", Kind.NETWORK),
        Triple("adcolony", "AdColony", Kind.NETWORK),
        Triple("chartboost", "Chartboost", Kind.NETWORK),
        Triple("inmobi", "InMobi", Kind.NETWORK),
        Triple("tapjoy", "Tapjoy", Kind.NETWORK),
        Triple("pangle", "Pangle (TikTok)", Kind.NETWORK),
        Triple("bytedance", "ByteDance (TikTok)", Kind.NETWORK),
        Triple("mintegral", "Mintegral", Kind.NETWORK),
        Triple("mtgglobals", "Mintegral", Kind.NETWORK),
        Triple("startapp", "StartApp", Kind.NETWORK),
        Triple("smaato", "Smaato", Kind.NETWORK),
        Triple("bigo", "BIGO Ads", Kind.NETWORK),
        Triple("moloco", "Moloco", Kind.NETWORK),
        Triple("liftoff", "Liftoff", Kind.NETWORK),
        Triple("nefta", "Nefta", Kind.NETWORK),
        Triple("digitalturbine", "Digital Turbine", Kind.NETWORK),
        Triple("maticooads", "Maticoo", Kind.NETWORK),
        Triple("an.facebook", "Meta Audience Network", Kind.NETWORK),
        Triple("yandexadexchange", "Yandex Ads", Kind.NETWORK),
        Triple("voodoo-adn", "Voodoo", Kind.NETWORK),
        Triple("adjoe", "adjoe", Kind.NETWORK),
        Triple("vervegroup", "Verve Group", Kind.NETWORK),
        Triple("everestop", "Everestop", Kind.NETWORK),
        Triple("axon.ai", "Axon (AppLovin)", Kind.NETWORK),

        // Real-time bidding — the auction house behind the scenes.
        Triple("bidmachine", "BidMachine", Kind.BIDDING),
        Triple("3lift", "TripleLift", Kind.BIDDING),
        Triple("triplelift", "TripleLift", Kind.BIDDING),
        Triple("inner-active", "InnerActive", Kind.BIDDING),
        Triple("amazon-adsystem", "Amazon Ads", Kind.BIDDING),
        Triple("pubmatic", "PubMatic", Kind.BIDDING),
        Triple("rubiconproject", "Magnite", Kind.BIDDING),
        Triple("magnite", "Magnite", Kind.BIDDING),
        Triple("adnxs", "Xandr (Microsoft)", Kind.BIDDING),
        Triple("adsrvr", "The Trade Desk", Kind.BIDDING),
        Triple("casalemedia", "Index Exchange", Kind.BIDDING),
        Triple("openx", "OpenX", Kind.BIDDING),
        Triple("smartadserver", "Equativ", Kind.BIDDING),
        Triple("sharethrough", "Sharethrough", Kind.BIDDING),
        Triple("gumgum", "GumGum", Kind.BIDDING),
        Triple("yieldmo", "Yieldmo", Kind.BIDDING),
        Triple("teads", "Teads", Kind.BIDDING),
        Triple("adform", "Adform", Kind.BIDDING),
        Triple("pubnative", "PubNative", Kind.BIDDING),
        Triple("onetag", "OneTag", Kind.BIDDING),
        Triple("sovrn", "Sovrn", Kind.BIDDING),
        Triple("bidswitch", "BidSwitch", Kind.BIDDING),
        Triple("optimusbid", "Optimus Bid", Kind.BIDDING),
        Triple("lazybumblebee", "Lazy Bumblebee", Kind.BIDDING),
        Triple("uidsync", "BidSwitch", Kind.BIDDING),

        // Measurement and attribution — nothing to show, everything to report.
        Triple("appsflyer", "AppsFlyer", Kind.TRACKER),
        Triple("afafb", "AppsFlyer", Kind.TRACKER),
        Triple("adjust", "Adjust", Kind.TRACKER),
        Triple("app-measurement", "Google Analytics", Kind.TRACKER),
        Triple("gameanalytics", "GameAnalytics", Kind.TRACKER),
        Triple("kochava", "Kochava", Kind.TRACKER),
        Triple("singular", "Singular", Kind.TRACKER),
        Triple("branch.io", "Branch", Kind.TRACKER),
        Triple("adikteev", "Adikteev", Kind.TRACKER),
        Triple("remerge", "Remerge", Kind.TRACKER),
        Triple("jampp", "Jampp", Kind.TRACKER),
        Triple("tenjin", "Tenjin", Kind.TRACKER),
        Triple("airbridge", "Airbridge", Kind.TRACKER),
        Triple("doubleverify", "DoubleVerify", Kind.TRACKER),
        Triple("geoedge", "GeoEdge", Kind.TRACKER),
        Triple("privacy-mgmt", "Sourcepoint", Kind.TRACKER),
        Triple("fundingchoices", "Google Funding Choices", Kind.TRACKER),
        Triple("push-sdk", "Push SDK", Kind.TRACKER),

        // Throwaway domains: registered cheap, used for a few weeks, dropped.
        Triple("queencarlotta", "", Kind.SHADY),
        Triple("firwinds", "", Kind.SHADY),
        Triple("bytegle", "", Kind.SHADY),
        Triple("news-cdn", "", Kind.SHADY),
        Triple("ilyvo", "", Kind.SHADY),
        Triple("ammnlth", "", Kind.SHADY),
        Triple("youngle", "", Kind.SHADY),
        Triple("tk0x1", "", Kind.SHADY),
    )

    fun cardFor(host: String): Card {
        val h = host.lowercase()
        val hit = known.firstOrNull { it.first in h }
        val blocked = AdNetworks.matches(h)
        if (hit == null) {
            // Unknown but silenced means the learning caught it: it showed up
            // in several apps at the moment an ad was loading.
            return Card("", if (blocked) Kind.SHADY else Kind.UNKNOWN, blocked)
        }
        return Card(hit.second, hit.third, blocked)
    }

    fun kindLabel(ctx: Context, k: Kind): String = ctx.getString(
        when (k) {
            Kind.MEDIATION -> R.string.kind_mediation
            Kind.NETWORK -> R.string.kind_network
            Kind.BIDDING -> R.string.kind_bidding
            Kind.TRACKER -> R.string.kind_tracker
            Kind.SHADY -> R.string.kind_shady
            Kind.ENGINE -> R.string.kind_engine
            Kind.UNKNOWN -> R.string.kind_unknown
        }
    )

    fun kindText(ctx: Context, k: Kind): String = ctx.getString(
        when (k) {
            Kind.MEDIATION -> R.string.explain_mediation
            Kind.NETWORK -> R.string.explain_network
            Kind.BIDDING -> R.string.explain_bidding
            Kind.TRACKER -> R.string.explain_tracker
            Kind.SHADY -> R.string.explain_shady
            Kind.ENGINE -> R.string.explain_engine
            Kind.UNKNOWN -> R.string.explain_unknown
        }
    )
}
