package com.adzero.app

import android.content.Context
import java.io.File

/**
 * Silences unknown servers during the seconds an ad is loading.
 *
 * Everything else in AdZero identifies ad servers by name — a shipped list, or
 * a domain seen in three different apps. Both are defeated by the same trick:
 * register a domain, use it in one game for a few weeks, throw it away. No list
 * has heard of it, and it dies before the learning reaches three apps. That is
 * where the ads that still get through come from.
 *
 * The name is useless, but the timing is not. Loading one ad makes a game query
 * a dozen servers in about two seconds, and AdZero already detects that burst —
 * it is how ads are counted. So during a burst, any server this app has never
 * contacted before is almost certainly part of the ad. It does not need to be
 * recognised; it only needs to turn up at the wrong moment.
 *
 * Safety rests on knowing what ordinary traffic looks like before judging
 * anything — either from watching this app in particular, or from watching
 * enough others to recognise shared infrastructure. See [armed].
 */
object Shield {

    /** How long after a burst starts unknown names stay unwelcome. */
    private const val WINDOW_MS = 6_000L

    /**
     * How many of an app's own servers must be on file before the shield is
     * allowed to judge it on its own record.
     *
     * On the very first launch of a game nothing is familiar, so an unguarded
     * shield would silence the game's own backend and break it.
     */
    private const val ARM_AFTER = 8

    /**
     * What it takes to judge an app AdZero has never watched before.
     *
     * The per-app rule above leaves a hole exactly where it hurts: a game
     * installed today and played once is never covered, and that is precisely
     * when its ad network is freshest and least likely to be on any list.
     *
     * The way out is that the apps do not have to be judged in isolation. A
     * server used quietly by several different apps is infrastructure —
     * analytics, a CDN, a push service — and no new game is going to be the
     * first to touch it during an ad burst. So once AdZero has watched enough
     * apps overall, it knows enough about the ordinary internet to spot a name
     * that belongs to none of it.
     */
    private const val ARM_GLOBALLY_APPS = 3
    private const val ARM_GLOBALLY_DOMAINS = 40

    /** A server this many apps use quietly is infrastructure, not an ad. */
    private const val COMMON_ENOUGH = 2

    /**
     * Packages the shield never touches, whatever they resolve.
     *
     * Play Services is the phone's plumbing: sign-in, saved games, backups,
     * push. It talks to endpoints no game has ever used, at moments that look
     * exactly like an ad burst from the outside, and silencing one of them
     * breaks something the user will never connect to an ad blocker.
     *
     * The named blocklist still applies to them — Play Services really does
     * fetch ad identifiers, and that stays blocked. This only stops the shield
     * from guessing about names nobody vetted.
     */
    private val NEVER_JUDGED = listOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.play",
        "com.android.providers",
        "com.google.android.packageinstaller",
        "com.android.systemui",
    )

    /** Per app, the servers it has resolved outside any ad burst. */
    private val familiar = HashMap<String, LinkedHashSet<String>>()

    /**
     * How many apps have quietly used each server. Kept incrementally: walking
     * [familiar] on every DNS query would cost far too much on the hot path.
     */
    private val usedBy = HashMap<String, Int>()

    /** An app talks to a bounded number of its own servers; ads are the tail. */
    private const val MAX_PER_APP = 80

    /**
     * How a burst is recognised when no name in it is known.
     *
     * Until now the shield could only act during a burst, and a burst was only
     * detected by counting *known* ad domains. So the two layers hung on the
     * same link: a game using an entirely unknown ad network never triggered a
     * burst, and the shield — which exists precisely for that case — never
     * opened. It could catch a stranger standing next to a familiar face, and
     * nothing at all when the whole crowd was strangers.
     *
     * A burst has a shape that needs no names. An app asks for six servers it
     * has never asked for, inside two and a half seconds. Apps do not do that
     * once they are past their first launch — they talk to the same handful of
     * addresses every time. Sudden novelty, arriving in a group, is the
     * signature by itself.
     */
    private const val NOVEL_ENOUGH = 6
    private const val NOVEL_WINDOW_MS = 2_500L
    private const val NOVEL_COOLDOWN_MS = 20_000L

    /**
     * Quarantine before a server joins an app's notebook.
     *
     * This is what makes the detector above possible. A domain used to be
     * filed as familiar the instant it was seen outside a burst — so the first
     * six names of an unrecognised ad load were learned as ordinary, and the
     * detector was blinded for next time by the very burst it was meant to
     * catch. Now a name waits three seconds, and is only committed if no burst
     * followed it. The buffer doubles as the detection window.
     */
    private const val QUARANTINE_MS = 3_000L

    private val pending = HashMap<String, MutableList<Pair<String, Long>>>()
    private val lastNovelBurst = HashMap<String, Long>()

    private val openUntil = HashMap<String, Long>()
    private var file: File? = null
    @Volatile private var dirty = false

    /**
     * Servers silenced by timing rather than by name, and which rule did it.
     *
     * The two rules fail differently, so a list that mixes them is much harder
     * to act on. NEIGHBOUR fires on a stranger standing next to a name we
     * recognise; SHAPE fires on a crowd of strangers arriving together, and is
     * the newer and less proven of the two. When a game breaks, knowing which
     * one caught the server is the difference between a fix and a guess.
     */
    enum class Rule { NEIGHBOUR, SHAPE }

    class Catch(val root: String, val rule: Rule)

    private val caught = LinkedHashMap<String, Rule>()

    fun catches(): List<Catch> = synchronized(caught) {
        caught.entries.reversed().map { Catch(it.key, it.value) }
    }

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "familiar.txt")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val parts = line.split("|")
                if (parts.size == 2) {
                    val set = LinkedHashSet(parts[1].split(",").filter { it.isNotEmpty() })
                    familiar[parts[0]] = set
                    for (d in set) usedBy[d] = (usedBy[d] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
        }
    }

    /** True once this app's own behaviour is known well enough to judge it. */
    fun armed(app: String): Boolean =
        synchronized(familiar) { (familiar[app]?.size ?: 0) } >= ARM_AFTER

    /**
     * True when AdZero has watched enough apps to recognise ordinary traffic,
     * without knowing this particular one.
     */
    private fun globallyArmed(): Boolean = synchronized(familiar) {
        familiar.size >= ARM_GLOBALLY_APPS && usedBy.size >= ARM_GLOBALLY_DOMAINS
    }

    /** How many apps quietly use this server. Read by the repair ranking. */
    fun usedByCount(root: String): Int = synchronized(familiar) { usedBy[root] ?: 0 }

    /** Used quietly by several different apps, so not this game's ad server. */
    private fun common(root: String): Boolean =
        synchronized(familiar) { (usedBy[root] ?: 0) >= COMMON_ENOUGH }

    /** Called when a burst is recognised: an ad is loading for this app now. */
    fun openWindow(app: String) {
        synchronized(openUntil) { openUntil[app] = System.currentTimeMillis() + WINDOW_MS }
    }

    /**
     * Buffers a name seen outside a burst, and looks for a burst in the buffer.
     *
     * Returns true when the shape of an ad load is recognised here: the window
     * opens, the quarantined names are thrown away rather than learned, and
     * this very lookup is silenced — it is the sixth stranger, not an innocent
     * one that happens to follow them.
     */
    private fun watchQuiet(app: String, root: String, now: Long): Boolean {
        if (Learning.isInfrastructure(root)) return false

        val novel: Int
        synchronized(familiar) {
            val known = familiar[app]
            val queue = pending.getOrPut(app) { mutableListOf() }
            queue.add(root to now)

            // Anything that has waited out its quarantine without a burst
            // following it is ordinary traffic, and joins the notebook.
            val settled = queue.filter { now - it.second > QUARANTINE_MS }
            queue.removeAll(settled)
            for ((name, _) in settled) commit(app, name)

            novel = queue
                .filter { now - it.second <= NOVEL_WINDOW_MS }
                .map { it.first }
                .distinct()
                .count { known?.contains(it) != true && !common(it) }
        }

        if (novel < NOVEL_ENOUGH) return false
        // Its own setting: the newer rule can be switched off without giving up
        // the one that has been working since yesterday.
        if (!Stats.shapeWanted) return false
        // Judging novelty needs something to compare against: on an app whose
        // habits are not on file yet, everything is novel and this would fire
        // on the first launch of anything.
        if (!armed(app)) return false
        val since = synchronized(lastNovelBurst) { now - (lastNovelBurst[app] ?: 0L) }
        if (since < NOVEL_COOLDOWN_MS) return false

        synchronized(lastNovelBurst) { lastNovelBurst[app] = now }
        synchronized(familiar) { pending.remove(app) }
        openWindow(app)
        note(root, Rule.SHAPE)
        return true
    }

    /** Files a name in the notebook. Caller holds the lock. */
    private fun commit(app: String, root: String) {
        val set = familiar.getOrPut(app) { LinkedHashSet() }
        if (!set.add(root)) return
        dirty = true
        usedBy[root] = (usedBy[root] ?: 0) + 1
        while (set.size > MAX_PER_APP) {
            val dropped = set.first()
            set.remove(dropped)
            val left = (usedBy[dropped] ?: 1) - 1
            if (left <= 0) usedBy.remove(dropped) else usedBy[dropped] = left
        }
    }

    private fun note(root: String, rule: Rule) {
        synchronized(caught) {
            caught[root] = rule
            while (caught.size > 60) caught.remove(caught.keys.first())
        }
    }

    /**
     * Records a server as one of the app's own.
     *
     * Only ever called outside a burst window. Anything resolved during a burst
     * is exactly what we are trying to catch, so learning it as familiar would
     * quietly disarm the shield after the first ad.
     */
    fun remember(app: String, root: String) {
        if (app == "?" || app == "com.adzero.app") return
        synchronized(familiar) { commit(app, root) }
    }

    /**
     * The decision, for a name no list recognised.
     *
     * Deliberately conservative: outside a burst it never fires, on an app it
     * does not know it never fires, and on infrastructure it never fires.
     * A false positive here breaks somebody's game.
     */
    fun shouldSilence(app: String, root: String): Boolean {
        if (!Stats.shieldWanted) return false
        if (app == "?" || app == "com.adzero.app") return false
        if (NEVER_JUDGED.any { app.startsWith(it) }) return false
        // Guessing at an unknown YouTube or Instagram endpoint is asking to
        // break them, and the reward is nil: their ads are unreachable anyway.
        if (FirstParty.kindOf(app) != null) return false

        val now = System.currentTimeMillis()
        val open = synchronized(openUntil) { openUntil[app] ?: 0L }
        if (now > open) {
            // No burst under way. Either this is the app going about its
            // business — in which case it belongs in the notebook — or it is
            // the beginning of one nobody would have recognised.
            return watchQuiet(app, root, now)
        }

        if (Learning.isInfrastructure(root)) return false
        if (synchronized(familiar) { familiar[app]?.contains(root) } == true) return false
        // Anything several apps rely on is part of the furniture, whoever is
        // asking for it.
        if (common(root)) return false

        // On an app whose own habits are on file, an unknown name arriving
        // mid-burst is enough. On an app AdZero has never watched, it is not:
        // that was yesterday's change, and it made the shield silence servers
        // games genuinely needed, because a game's own new endpoint looks
        // exactly like an ad server when there is nothing to compare it to.
        //
        // For those, the timing has to be backed by the name looking wrong as
        // well — a throwaway extension, an advertising word, a machine-made
        // string, or a domain already seen next to a real ad elsewhere.
        if (!armed(app)) {
            if (!globallyArmed()) return false
            if (!Recent.looksLikeAnAd(root)) return false
        }

        note(root, Rule.NEIGHBOUR)
        return true
    }

    fun save() {
        val f = file ?: return
        if (!dirty) return
        val copy = synchronized(familiar) { familiar.entries.map { it.key to it.value.toList() } }
        if (copy.isEmpty()) return
        try {
            f.bufferedWriter().use { w ->
                for ((app, domains) in copy) w.write(app + "|" + domains.joinToString(",") + "\n")
            }
            dirty = false
        } catch (_: Exception) {
        }
    }

    fun reset() {
        synchronized(familiar) { familiar.clear(); usedBy.clear(); pending.clear() }
        synchronized(caught) { caught.clear() }
        try { file?.writeText("") } catch (_: Exception) {}
    }
}
