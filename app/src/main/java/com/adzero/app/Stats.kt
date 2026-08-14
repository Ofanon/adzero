package com.adzero.app

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/** Shared state between the service, the tile and the activity. */
object Stats {

    val silenced = AtomicInteger()
    val allowed = AtomicInteger()

    /** Trackers blocked, counted apart from ads. */
    val trackers = AtomicInteger()

    /**
     * When a temporary pause ends. During a pause the tunnel stays up and
     * everything is let through — tearing the VPN down and back up would
     * drop connections for no reason.
     */
    @Volatile var pausedUntil: Long = 0L
        private set

    fun pauseFor(minutes: Int) {
        pausedUntil = System.currentTimeMillis() + minutes * 60_000L
    }

    fun resume() {
        pausedUntil = 0L
    }

    fun isPaused(): Boolean = System.currentTimeMillis() < pausedUntil

    /** Seconds left in the pause, 0 when not paused. */
    fun pauseLeft(): Long =
        if (!isPaused()) 0 else (pausedUntil - System.currentTimeMillis()) / 1000

    @Volatile var running: Boolean = false
    @Volatile var privateDnsDetected: Boolean = false

    /** Last start-up failure, worth showing to the user. */
    @Volatile var error: String? = null

    /** The bubble subscribes to react in real time. */
    @Volatile var onSilenced: ((String) -> Unit)? = null

    private const val PREFS = "adsilence"
    private const val KEY_TOTAL = "total_silenced"
    private const val KEY_RUNNING = "running"
    private const val KEY_STARTED = "started_at"
    private const val KEY_BUBBLE = "bubble"
    private const val KEY_BANNER = "banner"
    private const val KEY_REPORT = "report"
    private const val KEY_SHIELD = "shield"
    private const val KEY_TOURED = "toured"
    private const val KEY_SHAPE = "shape"
    private const val KEY_ONBOARDED = "onboarded"

    /** Running total since install, so the number does not reset every launch. */
    @Volatile var total: Int = 0
        private set

    var bubbleWanted: Boolean = false
        private set

    /** On by default: the announcement is the point of the feature. */
    var bannerWanted: Boolean = true
        private set

    /**
     * Whether the ongoing notification offers to report an ad that got through.
     * On by default, because the people who need it are exactly the ones who
     * would never go looking for it in the settings.
     */
    /** Le rapport de fin de partie. Actif par defaut : c'est la nouveaute. */
    /**
     * Les nouvelles deja lues, une par ligne.
     *
     * Une banniere qui revient apres avoir ete lue devient un decor : on cesse
     * de la voir, y compris le jour ou elle dit quelque chose d'important.
     */
    /**
     * Combien de serveurs distants cette personne a deja vus annonces.
     *
     * -1 tant qu'elle n'en a jamais vu : la premiere liste chargee devient
     * alors la reference en silence, parce qu'une premiere installation n'a
     * rien appris — la liste n'a pas grandi pour elle, elle etait deja la.
     */
    var listSeen: Int
        get() = prefs?.getInt("list_seen", -1) ?: -1
        set(value) {
            prefs?.edit()?.putInt("list_seen", value)?.apply()
        }

    fun newsSeen(key: String) {
        val seen = prefs?.getStringSet("news_seen", emptySet())?.toMutableSet()
            ?: return
        if (seen.add(key)) prefs?.edit()?.putStringSet("news_seen", seen)?.apply()
    }

    fun newsWasSeen(key: String): Boolean =
        prefs?.getStringSet("news_seen", emptySet())?.contains(key) == true

    /**
     * La mise a jour de la liste depuis GitHub. Allumee par defaut : une liste
     * gelee vieillit des le jour de sa sortie.
     */
    var remoteList: Boolean = true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("remote_list", value)?.apply()
        }

    var sessionReports: Boolean = true
        set(value) {
            field = value
            prefs?.edit()?.putBoolean("session_reports", value)?.apply()
        }

    var reportWanted: Boolean = true
        private set

    /**
      * Whether unknown servers are silenced during an ad burst. On by default:
      * it is the only thing that catches a throwaway domain the first time it
      * is ever seen, which is the case no list can cover.
      */
    var shieldWanted: Boolean = true
        private set

    /**
     * Whether the shield may also act on the shape of a burst, with no known
     * name in it. Separate from [shieldWanted] on purpose: it is the newer and
     * riskier of the two rules, and losing it should not cost the other.
     */
    var shapeWanted: Boolean = true
        private set

    fun rememberShape(value: Boolean) {
        shapeWanted = value
        prefs?.edit()?.putBoolean(KEY_SHAPE, value)?.apply()
    }

    /** False until the guided tour has run once. */
    var toured: Boolean = false
        private set

    fun rememberToured() {
        toured = true
        prefs?.edit()?.putBoolean(KEY_TOURED, true)?.apply()
    }

    /** False until the welcome flow has been seen once. */
    var onboarded: Boolean = false
        private set

    fun markOnboarded() {
        onboarded = true
        prefs?.edit()?.putBoolean(KEY_ONBOARDED, true)?.apply()
    }

    fun rememberReport(value: Boolean) {
        reportWanted = value
        prefs?.edit()?.putBoolean(KEY_REPORT, value)?.apply()
    }

    fun rememberShield(value: Boolean) {
        shieldWanted = value
        prefs?.edit()?.putBoolean(KEY_SHIELD, value)?.apply()
    }

    fun rememberBanner(value: Boolean) {
        bannerWanted = value
        prefs?.edit()?.putBoolean(KEY_BANNER, value)?.apply()
    }

    private var prefs: android.content.SharedPreferences? = null

    /** How many times each domain was left unanswered, this session. */
    private val perDomain = LinkedHashMap<String, Int>()

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        total = prefs?.getInt(KEY_TOTAL, 0) ?: 0
        // The process may have been killed while the service was up: without
        // this the UI would wrongly claim filtering is off.
        running = prefs?.getBoolean(KEY_RUNNING, false) ?: false
        startedAt = prefs?.getLong(KEY_STARTED, 0L) ?: 0L
        bubbleWanted = prefs?.getBoolean(KEY_BUBBLE, false) ?: false
        bannerWanted = prefs?.getBoolean(KEY_BANNER, true) ?: true
        reportWanted = prefs?.getBoolean(KEY_REPORT, true) ?: true
        sessionReports = prefs!!.getBoolean("session_reports", true)
        remoteList = prefs!!.getBoolean("remote_list", true)
        shieldWanted = prefs?.getBoolean(KEY_SHIELD, true) ?: true
        onboarded = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false
        toured = prefs?.getBoolean(KEY_TOURED, false) ?: false
        shapeWanted = prefs?.getBoolean(KEY_SHAPE, true) ?: true
    }

    /** When protection was switched on, for the session timer. */
    @Volatile var startedAt: Long = 0L
        private set

    fun markRunning(value: Boolean) {
        running = value
        startedAt = if (value) System.currentTimeMillis() else 0L
        prefs?.edit()
            ?.putBoolean(KEY_RUNNING, value)
            ?.putLong(KEY_STARTED, startedAt)
            ?.apply()
    }

    /** Seconds since protection was switched on, 0 when off. */
    fun uptimeSeconds(): Long =
        if (!running || startedAt == 0L) 0
        else (System.currentTimeMillis() - startedAt) / 1000

    fun rememberBubble(value: Boolean) {
        bubbleWanted = value
        prefs?.edit()?.putBoolean(KEY_BUBBLE, value)?.apply()
    }

    fun record(host: String, silencedHost: Boolean) {
        if (!silencedHost) {
            allowed.incrementAndGet()
            return
        }
        silenced.incrementAndGet()
        total++
        // Batched: no need to touch the disk on every single query.
        if (total % 25 == 0) prefs?.edit()?.putInt(KEY_TOTAL, total)?.apply()

        val root = rootOf(host)
        synchronized(perDomain) {
            perDomain[root] = (perDomain[root] ?: 0) + 1
        }
        onSilenced?.invoke(root)
    }

    /** foo.cdn.applovin.com -> applovin.com, so the list stays readable. */
    fun rootOf(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    /** Most-silenced domains first. */
    fun ranking(max: Int = 25): List<Pair<String, Int>> =
        synchronized(perDomain) {
            perDomain.entries.sortedByDescending { it.value }.take(max).map { it.key to it.value }
        }

    fun save() {
        prefs?.edit()?.putInt(KEY_TOTAL, total)?.apply()
    }

    fun reset() {
        silenced.set(0)
        allowed.set(0)
        synchronized(perDomain) { perDomain.clear() }
    }
}
