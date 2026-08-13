package com.adzero.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Proves, on demand, that protection is actually working.
 *
 * An ad blocker succeeds by making nothing happen, which leaves the user with
 * no evidence either way. Worse, AdZero can be wrong about itself: the running
 * state is written to disk so it survives the process being restarted, and
 * after an install or a force stop that flag says "protected" while nothing is
 * running at all. Somebody could go weeks unprotected and never know.
 *
 * So this asks the network rather than the app's own memory. It resolves a real
 * ad domain and a real ordinary one, through the tunnel, and reports what came
 * back. That is the same path a game takes, which is the only test worth
 * trusting.
 */
object SelfTest {

    /** Long enough for a real answer, short enough not to feel broken. */
    private const val TIMEOUT_MS = 4000L

    /**
     * Neither of these is picked at random. The first is on the blocklist and
     * has to stay unanswered; the second must never be, or the tunnel is
     * breaking ordinary traffic.
     */
    private const val AD_HOST = "applovin.com"
    private const val NORMAL_HOST = "example.com"

    /**
     * [info] marks a line that is neither a pass nor a failure: something true
     * about how the blocking works that the person should know. Without it
     * every line has to be dressed as a verdict, and a plain fact ends up
     * wearing a red cross.
     */
    class Check(
        val ok: Boolean,
        val title: Int,
        val detail: Int,
        val info: Boolean = false,
        /** Substituted into [detail] when it carries a placeholder. */
        val arg: String? = null,
    )

    fun run(ctx: Context, onDone: (List<Check>) -> Unit) {
        val app = ctx.applicationContext
        Thread({
            val checks = ArrayList<Check>(3)

            // 1. Is the service actually alive in this process, rather than
            //    merely remembered as alive on disk?
            val live = SilenceVpnService.alive
            checks.add(
                if (live) Check(true, R.string.test_service_ok, R.string.test_service_ok_why)
                else Check(false, R.string.test_service_bad, R.string.test_service_bad_why)
            )

            // 2. Nothing essential is caught in the nets. Instant, and it
            //    runs before anything that touches the network: if the lists
            //    have started swallowing a bank, that matters more than
            //    whether an ad server is reachable.
            val overblocked = Guard.check()
            checks.add(
                if (overblocked.isEmpty())
                    Check(true, R.string.test_guard_ok, R.string.test_guard_ok_why,
                        arg = Guard.size.toString())
                else Check(
                    false, R.string.test_guard_bad, R.string.test_guard_bad_why,
                    arg = overblocked.take(3).joinToString(", ") { it.host },
                )
            )

            // 3. An ordinary name must still resolve. A blocker that breaks the
            //    rest of the internet is worse than no blocker.
            val normalOk = resolves(NORMAL_HOST)
            checks.add(
                if (normalOk) Check(true, R.string.test_normal_ok, R.string.test_normal_ok_why)
                else Check(false, R.string.test_normal_bad, R.string.test_normal_bad_why)
            )

            // 4. The real test: an ad domain must go unanswered. AdZero's own
            //    traffic goes through its tunnel like everybody else's, so this
            //    is the exact path a game takes.
            val adAnswered = if (!live) true else resolves(AD_HOST)
            checks.add(
                if (!adAnswered) Check(true, R.string.test_block_ok, R.string.test_block_ok_why)
                else Check(false, R.string.test_block_bad, R.string.test_block_bad_why)
            )

            // 5. Private DNS sends lookups straight to another server over
            //    HTTPS, where AdZero never sees them. Reported last because it
            //    explains an otherwise baffling failure above.
            val mode = Settings.Global.getString(app.contentResolver, "private_dns_mode")
            if (mode != null && mode != "off" && mode != "opportunistic") {
                checks.add(Check(false, R.string.test_dns_bad, R.string.test_dns_bad_why))
            }

            // Not a verdict: DNS blocking cannot reach a game that resolved
            // its ad server before protection started, because the address is
            // already in that app's memory. Nothing in the app said so, and
            // "I turned it on and the ads kept coming" has an answer.
            checks.add(Check(true, R.string.test_cache, R.string.test_cache_why, info = true))

            Handler(Looper.getMainLooper()).post { onDone(checks) }
        }, "self-test").start()
    }

    /** True if the name resolved within the timeout. A silenced name never does. */
    private fun resolves(host: String): Boolean {
        val done = CountDownLatch(1)
        // Written on the worker, read here: an atomic rather than a local,
        // which cannot carry @Volatile.
        val got = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread({
            try {
                InetAddress.getByName(host)
                got.set(true)
            } catch (_: Exception) {
                got.set(false)
            }
            done.countDown()
        }, "self-test-dns")
        worker.isDaemon = true
        worker.start()
        // Waiting on a latch rather than joining: a silenced lookup blocks in
        // the resolver for far longer than anyone will sit and watch.
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return got.get()
    }
}
