package com.adzero.app

import android.content.Context
import java.io.File

/**
 * Spots ad networks without any hand-written list.
 *
 * The idea: a legitimate domain is only queried by the app it belongs to. An
 * ad network is queried by games that have nothing to do with each other. So
 * counting distinct apps is enough to tell them apart.
 *
 * Nothing is ever silenced automatically. A heuristic is wrong sooner or
 * later, and silencing the wrong domain breaks an app without the user ever
 * understanding why. We suggest; they decide.
 */
object Learning {

    /** Above this many distinct apps, a domain is suspicious. */
    const val THRESHOLD = 3

    /**
     * Shared infrastructure, obviously queried by dozens of apps and just as
     * obviously not an ad network. Without this exception the heuristic would
     * happily suggest silencing Google as a whole.
     */
    private val INFRASTRUCTURE = listOf(
        "googleapis.com", "gstatic.com", "google.com", "googleusercontent.com",
        "android.com", "gvt1.com", "gvt2.com", "ggpht.com", "youtube.com",
        "cloudflare.com", "cloudfront.net", "akamai.net", "akamaized.net",
        "amazonaws.com", "azureedge.net", "windows.net", "apple.com",
        "digicert.com", "letsencrypt.org", "sectigo.com", "pki.goog",
        "firebaseio.com", "crashlytics.com", "sentry.io", "bugsnag.com",
        // Media CDNs shared by many apps. ytimg.com was the heuristic's first
        // real false positive: silencing it kills YouTube thumbnails.
        "ytimg.com", "ggpht.com", "twimg.com", "fbcdn.net", "licdn.com",
        "redd.it", "giphy.com", "imgur.com",
    )

    /**
     * Suggestions already put in front of the user.
     *
     * The badge on the statistics tab used to mean "there are suggestions",
     * which is permanently true — the learning finds something most days, so
     * the dot never went out and stopped carrying information. It has to mean
     * "there is something you have not seen", which is a state that ends.
     *
     * Kept separately from [ignored]: turning a suggestion down is a decision,
     * having glanced at the list is not.
     */
    private val seen = mutableSetOf<String>()
    private var seenFile: File? = null

    /** Called when the statistics tab is opened: everything on it is now seen. */
    fun markCandidatesSeen() {
        val current = candidates().map { it.first }
        synchronized(seen) {
            if (!seen.addAll(current)) return
            try {
                seenFile?.writeText(seen.joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * True when the learning has found something the user has not looked at.
     *
     * Cached, because the tab bar asks this on every repaint — once a second
     * while the app is open — and answering means walking every domain the
     * learning has ever recorded. That was the whole interface stuttering, and
     * why the Ignore button seemed to need several presses: each press
     * triggered another full scan before the row disappeared.
     *
     * A few seconds of staleness on a badge costs nothing.
     */
    @Volatile private var unseenCache = false
    @Volatile private var unseenCheckedAt = 0L

    fun hasUnseenCandidates(): Boolean {
        val now = System.currentTimeMillis()
        if (now - unseenCheckedAt < 5_000L) return unseenCache
        val known = synchronized(seen) { seen.toSet() }
        unseenCache = candidates(8).any { it.first !in known }
        unseenCheckedAt = now
        return unseenCache
    }

    /** Forces the next badge question to be answered afresh. */
    fun invalidateUnseen() {
        unseenCheckedAt = 0L
    }

    /**
     * Suggestions the user has turned down.
     *
     * Without this the same domain is offered again on the next launch, for
     * ever: the evidence that produced it does not go away when somebody
     * decides they are fine with it. A suggestion box that ignores the answer
     * stops being read.
     */
    private val ignored = mutableSetOf<String>()
    private var ignoreFile: File? = null

    fun ignore(domain: String) {
        val d = domain.trim().lowercase()
        synchronized(ignored) {
            if (!ignored.add(d)) return
            try { ignoreFile?.appendText(d + "\n") } catch (_: Exception) {}
        }
    }

    private val byDomain = HashMap<String, MutableSet<String>>()

    /**
     * How often a domain was resolved right after a known ad server, in the
     * same app.
     *
     * Counting distinct apps cannot see a network used by a single game — and
     * that is exactly where the worst ads live, on throwaway domains that only
     * one title carries. But an ad load is a burst: whatever a game resolves
     * two seconds after calling AppLovin is almost certainly part of the same
     * ad. Guilt by association, and it holds up well in practice.
     */
    private val nearAds = HashMap<String, Int>()
    private val lastAdMoment = HashMap<String, Long>()
    private const val WINDOW_MS = 3000L
    const val NEAR_THRESHOLD = 3
    private var file: File? = null
    private var dirty = false

    fun init(ctx: Context) {
        if (seenFile == null) {
            val f = File(ctx.applicationContext.filesDir, "seen_candidates.txt")
            seenFile = f
            if (f.exists()) try {
                f.forEachLine { line ->
                    line.trim().lowercase().takeIf { it.isNotEmpty() }?.let { seen.add(it) }
                }
            } catch (_: Exception) {
            }
        }
        if (ignoreFile == null) {
            val f = File(ctx.applicationContext.filesDir, "ignored_domains.txt")
            ignoreFile = f
            if (f.exists()) try {
                f.forEachLine { line ->
                    line.trim().lowercase().takeIf { it.isNotEmpty() }?.let { ignored.add(it) }
                }
            } catch (_: Exception) {
            }
        }
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "learning.txt")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    val head = parts[0]
                    if (head.startsWith("~")) {
                        nearAds[head.drop(1)] = parts[1].toIntOrNull() ?: 0
                    } else {
                        byDomain[head] =
                            parts[1].split(",").filter { it.isNotBlank() }.toMutableSet()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Shared with the ad reporting, which must not accuse a CDN either. */
    fun isInfrastructure(domain: String): Boolean =
        INFRASTRUCTURE.any { domain == it || domain.endsWith(".$it") }

    /** Called whenever an ad server was silenced for [app]. */
    fun noteAdMoment(app: String) {
        if (app == "?") return
        synchronized(nearAds) { lastAdMoment[app] = System.currentTimeMillis() }
    }

    /** Records that an app just queried this domain. */
    fun observe(host: String, app: String) {
        if (app == "?" || app == OWN_PACKAGE) return
        val root = Stats.rootOf(host)
        if (isInfrastructure(root)) return

        // Resolved in the wake of a real ad request?
        synchronized(nearAds) {
            val last = lastAdMoment[app] ?: 0L
            if (System.currentTimeMillis() - last < WINDOW_MS && !AdNetworks.matches(root)) {
                nearAds[root] = (nearAds[root] ?: 0) + 1
                dirty = true
            }
        }

        synchronized(byDomain) {
            val apps = byDomain.getOrPut(root) { mutableSetOf() }
            if (apps.add(app)) dirty = true
            // Well past the threshold there is no point accumulating more.
            if (apps.size > 12) apps.remove(apps.first())
            if (byDomain.size > 4000) byDomain.remove(byDomain.keys.first())
        }
    }

    /** Domains behaving like ad networks but not silenced yet, most suspicious first. */
    fun candidates(max: Int = 20): List<Pair<String, Int>> {
        val near = synchronized(nearAds) { nearAds.toMap() }
        val refused = synchronized(ignored) { ignored.toSet() }
        return synchronized(byDomain) {
            byDomain.entries
                // Infrastructure is filtered here too, not only when recording:
                // otherwise a domain learned before the exclusion list grew
                // keeps being suggested for ever.
                .filter {
                    // The refused set is copied once, not once per domain. It
                    // used to be snapshotted inside the predicate, so a scan of
                    // four thousand domains copied it four thousand times.
                    !isInfrastructure(it.key) && !AdNetworks.matches(it.key) &&
                            it.key !in refused &&
                            (it.value.size >= THRESHOLD ||
                                    (near[it.key] ?: 0) >= NEAR_THRESHOLD)
                }
                .sortedByDescending { maxOf(it.value.size * 2, near[it.key] ?: 0) }
                .take(max)
                .map { it.key to it.value.size }
        }
    }

    /** How many times this domain was seen in the wake of an ad. */
    fun adProximity(domain: String): Int = synchronized(nearAds) { nearAds[domain] ?: 0 }

    /**
     * The ad servers a given app talks to. Free to compute: the learning
     * already records which apps queried which domain, so we just read it
     * the other way round.
     */
    fun networksOf(app: String, max: Int = 8): List<String> = synchronized(byDomain) {
        byDomain.entries
            .filter { app in it.value && AdNetworks.matches(it.key) }
            .map { it.key }
            .sorted()
            .take(max)
    }

    /** Which apps queried this domain — the evidence behind a suggestion. */
    fun witnesses(domain: String): List<String> =
        synchronized(byDomain) { byDomain[domain]?.toList() ?: emptyList() }

    fun save() {
        val f = file ?: return
        if (!dirty) return
        try {
            val copy = synchronized(byDomain) { byDomain.toMap() }
            // Same rule as the leaderboard: an empty map must never overwrite
            // a file that still holds something.
            if (copy.isEmpty() && f.exists() && f.length() > 0) return
            val near = synchronized(nearAds) { nearAds.toMap() }
            f.bufferedWriter().use { w ->
                for ((domain, apps) in copy) w.write("$domain|${apps.joinToString(",")}\n")
                // Prefixed so the two kinds of line never collide on reload.
                for ((domain, n) in near) if (n > 0) w.write("~$domain|$n\n")
            }
            dirty = false
        } catch (_: Exception) {
        }
    }

    /** Our own package: its queries must not feed the learning. */
    private const val OWN_PACKAGE = "com.adzero.app"
}
