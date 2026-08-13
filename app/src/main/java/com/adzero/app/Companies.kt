package com.adzero.app

/**
 * Who actually owns the servers, on paper.
 *
 * The cards already said what a domain does. What they could not say is who is
 * behind it, and that is what makes the answer sound like a fact rather than an
 * opinion: a legal name, a head office, a stock ticker where there is one.
 *
 * It also shows something the individual cards hide. Half of these domains
 * belong to four companies. Unity bought ironSource and Tapjoy; AppLovin bought
 * MoPub, Adjust and SafeDK; Digital Turbine bought Fyber and AdColony. Somebody
 * who blocks eight "different" ad networks is refusing four companies.
 *
 * Ownership in this industry changes hands often, so [parent] is the field most
 * likely to age. Everything here is stated as of writing and nowhere claims to
 * be live.
 */
object Companies {

    class Entry(
        val legal: String,
        val home: String,
        /** Stock exchange and ticker, for the ones that are listed. */
        val listing: String? = null,
        /** The group it now belongs to, when it is no longer independent. */
        val parent: String? = null,
    )

    /**
     * Keyed by the same markers the domain matching uses, longest first so a
     * specific subsidiary wins over the group it belongs to.
     */
    private val byMarker: List<Pair<String, Entry>> = listOf(
        // --- the four groups most of this list rolls up into
        "applovin" to Entry("AppLovin Corporation", "Palo Alto, California", "Nasdaq: APP"),
        "applvn" to Entry("AppLovin Corporation", "Palo Alto, California", "Nasdaq: APP"),
        "maxesads" to Entry("AppLovin Corporation", "Palo Alto, California", "Nasdaq: APP"),
        "maxmdb" to Entry("AppLovin Corporation", "Palo Alto, California", "Nasdaq: APP"),
        "axon.ai" to Entry("AppLovin Corporation", "Palo Alto, California", "Nasdaq: APP"),
        "safedk" to Entry("SafeDK Mobile Ltd.", "Tel-Aviv, Israel", parent = "AppLovin"),
        "mopub" to Entry("MoPub", "San Francisco, California", parent = "AppLovin (closed 2022)"),
        "adjust" to Entry("Adjust GmbH", "Berlin, Germany", parent = "AppLovin"),

        "unityads" to Entry("Unity Technologies", "San Francisco, California", "NYSE: U"),
        "iads.unity3d" to Entry("Unity Technologies", "San Francisco, California", "NYSE: U"),
        "mediation.unity3d" to Entry("Unity Technologies", "San Francisco, California", "NYSE: U"),
        "unity3d" to Entry("Unity Technologies", "San Francisco, California", "NYSE: U"),
        "ironsrc" to Entry("ironSource Ltd.", "Tel-Aviv, Israel", parent = "Unity (merged 2022)"),
        "ironsource" to Entry("ironSource Ltd.", "Tel-Aviv, Israel", parent = "Unity (merged 2022)"),
        "supersonicads" to Entry("ironSource Ltd.", "Tel-Aviv, Israel", parent = "Unity"),
        "tapjoy" to Entry("Tapjoy, Inc.", "San Francisco, California", parent = "Unity"),

        "digitalturbine" to Entry("Digital Turbine, Inc.", "Austin, Texas", "Nasdaq: APPS"),
        "fyber" to Entry("Fyber N.V.", "Berlin, Germany", parent = "Digital Turbine"),
        "adcolony" to Entry("AdColony", "Los Angeles, California", parent = "Digital Turbine"),

        "admob" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),
        "googleads" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),
        "doubleclick" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),
        "googlesyndication" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),
        "app-measurement" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),
        "fundingchoices" to Entry("Google LLC", "Mountain View, California", "Nasdaq: GOOGL"),

        // --- the rest
        "vungle" to Entry("Liftoff Mobile, Inc.", "Redwood City, California", parent = "Liftoff (formerly Vungle)"),
        "liftoff" to Entry("Liftoff Mobile, Inc.", "Redwood City, California"),
        "chartboost" to Entry("Chartboost, Inc.", "San Francisco, California", parent = "Zynga / Take-Two"),
        "inmobi" to Entry("InMobi", "Bangalore, India"),
        "pangle" to Entry("ByteDance Ltd.", "Beijing, China"),
        "bytedance" to Entry("ByteDance Ltd.", "Beijing, China"),
        "mintegral" to Entry("Mobvista", "Guangzhou, China", "Hong Kong Stock Exchange"),
        "mtgglobals" to Entry("Mobvista", "Guangzhou, China", "Hong Kong Stock Exchange"),
        "startapp" to Entry("Start.io", "Tel-Aviv, Israel"),
        "smaato" to Entry("Smaato, Inc.", "San Francisco, California", parent = "Verve Group"),
        "vervegroup" to Entry("Verve Group", "Berlin, Germany"),
        "bigo" to Entry("BIGO Technology", "Singapore", parent = "JOYY"),
        "moloco" to Entry("Moloco, Inc.", "Redwood City, California"),
        "an.facebook" to Entry("Meta Platforms, Inc.", "Menlo Park, California", "Nasdaq: META"),
        "yandexadexchange" to Entry("Yandex", "Moscow, Russia"),
        "voodoo" to Entry("Voodoo SAS", "Paris, France"),
        "adjoe" to Entry("adjoe GmbH", "Hamburg, Germany"),
        "appodeal" to Entry("Appodeal, Inc.", "San Francisco, California"),
        "bidmachine" to Entry("BidMachine", "San Francisco, California", parent = "Appodeal"),

        "amazon-adsystem" to Entry("Amazon.com, Inc.", "Seattle, Washington", "Nasdaq: AMZN"),
        "adsrvr" to Entry("The Trade Desk, Inc.", "Ventura, California", "Nasdaq: TTD"),
        "adnxs" to Entry("Xandr", "New York", parent = "Microsoft"),
        "magnite" to Entry("Magnite, Inc.", "New York", "Nasdaq: MGNI"),
        "rubiconproject" to Entry("Magnite, Inc.", "New York", "Nasdaq: MGNI"),
        "pubmatic" to Entry("PubMatic, Inc.", "Redwood City, California", "Nasdaq: PUBM"),
        "casalemedia" to Entry("Index Exchange", "Toronto, Canada"),
        "openx" to Entry("OpenX Technologies", "Pasadena, California"),
        "smartadserver" to Entry("Equativ", "Paris, France"),
        "teads" to Entry("Teads", "Luxembourg"),
        "3lift" to Entry("TripleLift, Inc.", "New York"),
        "triplelift" to Entry("TripleLift, Inc.", "New York"),
        "sharethrough" to Entry("Sharethrough", "Montreal, Canada"),
        "gumgum" to Entry("GumGum, Inc.", "Santa Monica, California"),
        "yieldmo" to Entry("Yieldmo, Inc.", "New York"),
        "adform" to Entry("Adform", "Copenhagen, Denmark"),
        "sovrn" to Entry("Sovrn Holdings", "Boulder, Colorado"),
        "onetag" to Entry("OneTag Ltd.", "London, United Kingdom"),
        "bidswitch" to Entry("BidSwitch", "New York", parent = "Criteo"),
        "doubleverify" to Entry("DoubleVerify Holdings", "New York", "NYSE: DV"),
        "privacy-mgmt" to Entry("Sourcepoint", "New York"),

        "appsflyer" to Entry("AppsFlyer Ltd.", "Herzliya, Israel"),
        "afafb" to Entry("AppsFlyer Ltd.", "Herzliya, Israel"),
        "kochava" to Entry("Kochava, Inc.", "Sandpoint, Idaho"),
        "singular" to Entry("Singular Labs, Inc.", "San Francisco, California"),
        "branch.io" to Entry("Branch Metrics, Inc.", "Palo Alto, California"),
        "gameanalytics" to Entry("GameAnalytics", "Copenhagen, Denmark"),
        "tenjin" to Entry("Tenjin, Inc.", "San Francisco, California"),
        "airbridge" to Entry("AB180, Inc.", "Seoul, South Korea"),
    ).sortedByDescending { it.first.length }

    fun forHost(host: String): Entry? {
        val h = host.lowercase()
        return byMarker.firstOrNull { it.first in h }?.second
    }
}
