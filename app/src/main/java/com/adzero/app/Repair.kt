package com.adzero.app

/**
 * Finds which silenced server a game actually needed, one guess at a time.
 *
 * Handing somebody a list of eight hostnames and a Release button next to each
 * is not help: they cannot know which one matters, so they either release all
 * of them — losing the protection for that app — or none, and give up.
 *
 * The app can do better, because the question is answerable by experiment. It
 * releases the single most likely candidate, the user reopens the game, and
 * says whether it worked. If not, that one goes back on the list and the next
 * is tried. It converges, and every step is one yes-or-no question about
 * something the user can see for themselves.
 *
 * Held in memory only. The tunnel runs as a foreground service in this same
 * process, so it survives the user leaving to test the game — and if the
 * process does die, losing a diagnosis in progress costs one restart of a
 * flow that takes fifteen seconds.
 */
object Repair {

    class Session(val app: String, val candidates: List<String>) {
        var index = 0
        val current: String? get() = candidates.getOrNull(index)
    }

    @Volatile private var session: Session? = null

    fun sessionFor(app: String): Session? = session?.takeIf { it.app == app }

    /**
     * Orders the silenced servers by how likely the game is to need one.
     *
     * The mirror image of how the ad report ranks: everything that makes a
     * domain look like an ad network pushes it down here, and everything that
     * makes it look like plumbing pushes it up. A server several apps use
     * quietly, contacted steadily rather than once mid-burst, with an ordinary
     * name, is what a game breaks without.
     */
    fun rank(app: String, domains: List<String>): List<String> = domains.sortedByDescending { d ->
        var score = 0
        if (Learning.isInfrastructure(d)) score += 40
        if (Shield.usedByCount(d) >= 2) score += 30
        if (Recent.timesSeen(app, d) >= 3) score += 25

        // Anything the app can name as an ad network goes last: releasing it
        // is how the protection gets quietly dismantled one game at a time.
        val known = Explain.cardFor(d)
        if (known.owner.isNotEmpty() && known.kind != Explain.Kind.ENGINE) score -= 35
        if (Recent.looksLikeAnAd(d)) score -= 40
        score
    }

    /** Starts a diagnosis and releases the first candidate. */
    fun start(app: String, domains: List<String>): String? {
        val ordered = rank(app, domains)
        if (ordered.isEmpty()) return null
        val fresh = Session(app, ordered)
        session = fresh
        AdNetworks.allow(ordered.first())
        return ordered.first()
    }

    /** The user says the game works: keep the release and finish. */
    fun worked() {
        session = null
    }

    /**
     * The user says it is still broken: put that one back and try the next.
     * Returns the new candidate, or null when the list is exhausted.
     */
    fun stillBroken(): String? {
        val s = session ?: return null
        s.current?.let { AdNetworks.blockAgain(it) }
        s.index++
        val next = s.current
        if (next == null) {
            session = null
            return null
        }
        AdNetworks.allow(next)
        return next
    }

    fun abandon() {
        val s = session ?: return
        s.current?.let { AdNetworks.blockAgain(it) }
        session = null
    }
}
