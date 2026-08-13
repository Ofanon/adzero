package com.adzero.app

import android.content.Context
import java.io.File

/**
 * How many ads each app tried to load.
 *
 * Counting DNS queries is meaningless: a single ad triggers a dozen of them,
 * because the mediation layer polls every network at once. So we count
 * *bursts* instead.
 *
 * A burst is several ad-network domains queried by the same app within two and
 * a half seconds — the signature of a waterfall starting up, which is one ad
 * attempt, not ten.
 */
object Leaderboard {

    private const val BURST_MS = 2500L
    private const val MIN_DOMAINS = 3

    /**
     * Cooldown between two counted attempts for the same app.
     *
     * Without it the counter lies: when an ad is blocked the SDK retries in a
     * loop, and every retry looks like a fresh attempt. Measured on the test
     * bench logs: 63 attempts with no cooldown, 24 with 20 s, over the same
     * session. Retries land within seconds; two genuine ads are at least a
     * play session apart.
     */
    private const val COOLDOWN_MS = 20_000L

    /**
     * Fired when a burst is finally counted as one ad.
     *
     * The daily history used to increment on every silenced request instead,
     * so the chart claimed several hundred ads for a single game launch while
     * the home screen said forty. One ad triggers about a dozen requests: the
     * two counters were measuring different things and only one of them meant
     * anything to a person.
     */
    @Volatile var onAttempt: ((String) -> Unit)? = null

    /** Assumed length of one avoided ad. An estimate, and displayed as one. */
    const val SECONDS_PER_AD = 25

    class Entry {
        var requests = 0
        var attempts = 0
        var lastSeen = 0L

        // Live burst state, never written to disk.
        @Transient val current = HashSet<String>()
        @Transient var burstStart = 0L
        @Transient var lastAttempt = 0L
    }

    private val entries = LinkedHashMap<String, Entry>()
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "leaderboard.txt")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val p = line.split("|")
                if (p.size >= 4) {
                    entries[p[0]] = Entry().apply {
                        requests = p[1].toIntOrNull() ?: 0
                        attempts = p[2].toIntOrNull() ?: 0
                        lastSeen = p[3].toLongOrNull() ?: 0L
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun observe(app: String, host: String, isAdNetwork: Boolean) {
        if (app == "?" || app == "com.adzero.app") return
        val now = System.currentTimeMillis()

        synchronized(entries) {
            val e = entries.getOrPut(app) { Entry() }
            e.lastSeen = now
            if (!isAdNetwork) return
            e.requests++

            // Long enough since the last one: the previous burst is over.
            if (now - e.burstStart > BURST_MS) {
                close(e, now, app)
                e.burstStart = now
            }
            e.current.add(Stats.rootOf(host))
        }
    }

    private fun close(e: Entry, now: Long, app: String) {
        if (e.current.size >= MIN_DOMAINS && now - e.lastAttempt > COOLDOWN_MS) {
            e.attempts++
            e.lastAttempt = now
            onAttempt?.invoke(app)
        }
        e.current.clear()
    }

    /** Closes dangling bursts, otherwise the last one would never count. */
    private fun closeStale() {
        val now = System.currentTimeMillis()
        synchronized(entries) {
            for ((app, e) in entries) {
                if (e.current.isNotEmpty() && now - e.burstStart > BURST_MS) close(e, now, app)
            }
        }
    }

    class Row(val app: String, val attempts: Int, val requests: Int, val lastSeen: Long)

    fun ranking(max: Int = 12): List<Row> {
        closeStale()
        return synchronized(entries) {
            entries.entries
                .filter { it.value.attempts > 0 }
                .sortedByDescending { it.value.attempts }
                .take(max)
                .map { Row(it.key, it.value.attempts, it.value.requests, it.value.lastSeen) }
        }
    }

    fun totalAttempts(): Int {
        closeStale()
        return synchronized(entries) { entries.values.sumOf { it.attempts } }
    }

    fun save() {
        val f = file ?: return
        val copy = synchronized(entries) { entries.entries.map { it.key to it.value } }

        // Never replace a populated file with nothing. If the in-memory map is
        // empty while the file is not, something went wrong upstream — a failed
        // load, a half-started process — and writing would destroy real data
        // for good. Losing an update is recoverable; losing the file is not.
        if (copy.isEmpty() && f.exists() && f.length() > 0) return

        try {
            f.bufferedWriter().use { w ->
                for ((app, e) in copy) w.write("$app|${e.requests}|${e.attempts}|${e.lastSeen}\n")
            }
        } catch (_: Exception) {
        }
    }

    fun reset() {
        synchronized(entries) { entries.clear() }
        try { file?.writeText("") } catch (_: Exception) {}
    }
}
