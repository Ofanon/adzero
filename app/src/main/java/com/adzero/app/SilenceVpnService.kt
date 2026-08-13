package com.adzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * A local VPN that carries DNS and nothing else.
 *
 * The trick is in the routing: only the tunnel's DNS address is routed to us.
 * All other traffic leaves normally, without going through the app. So there
 * is no TCP stack to write, and no battery cost.
 *
 * For an ad-network domain we answer nothing at all. The system resolver
 * waits, the connection never starts, and the ad waterfall stalls. That is
 * exactly what the test bench measured: a slow failure neutralises mediation,
 * where a fast failure just makes it fail over to the next network.
 */
class SilenceVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.adzero.app.START"
        const val ACTION_STOP = "com.adzero.app.STOP"
        const val ACTION_PAUSE = "com.adzero.app.PAUSE"
        const val ACTION_REPORT = "com.adzero.app.REPORT_ASK"

        /**
         * Rebuilds the ongoing notification.
         *
         * Toggling the report button used to send ACTION_START, which returns
         * immediately when the tunnel is already up — so the notification kept
         * whatever actions it was built with, and the setting appeared to do
         * nothing until the next restart.
         */
        const val ACTION_REFRESH = "com.adzero.app.REFRESH"
        const val ACTION_BLOCK_OFFERED = "com.adzero.app.REPORT_BLOCK"

        private const val TAG = "AdSilence"
        private const val CHANNEL = "adsilence"

        /**
         * Whether the tunnel is up in *this* process.
         *
         * Distinct from the state kept in the preferences, which survives the
         * process on purpose so the UI does not flicker off during a restart.
         * That flag can outlive the thing it describes — after an install or a
         * force stop it still reads "protected" while nothing is running. This
         * one cannot lie: it dies with the process, exactly as the tunnel does.
         */
        @Volatile var alive = false

        private const val LOCAL_IP = "10.111.222.1"
        private const val DNS_IP = "10.111.222.2"

        /**
         * Used only when the phone will not say what its own resolvers are.
         *
         * Every legitimate query used to go here unconditionally, which was
         * wrong twice over. It made the app's one promise false — the domain
         * names of everything the phone does were leaving the device to a
         * company nobody had been told about — and it broke any network that
         * runs its own resolver, because a school's or a company's internal
         * names mean nothing to a public one. "The wifi stops working when
         * AdZero is on" would have been the bug report.
         */
        private const val FALLBACK_DNS = "1.1.1.1"
    }

    /**
     * Where a legitimate question gets sent, and over which network.
     *
     * Re-read periodically rather than once at startup: the answer changes the
     * moment the phone leaves wifi for mobile data, and a resolver belonging to
     * a network no longer attached answers nothing at all.
     */
    @Volatile private var route: Pair<Network, InetAddress>? = null
    @Volatile private var routeReadAt = 0L

    private fun upstream(): Pair<Network?, InetAddress> {
        val now = System.currentTimeMillis()
        if (now - routeReadAt > 10_000L || route == null) {
            routeReadAt = now
            val fresh = findRoute()
            // Logged when it changes, never per query. Which resolver AdZero
            // hands questions to is the one claim in the privacy policy that
            // somebody might reasonably want to check for themselves, and
            // "read the source" is a worse answer than "run one command".
            if (fresh?.second != route?.second) {
                Log.i(TAG, "resolver: " + (fresh?.second?.hostAddress ?: "$FALLBACK_DNS (fallback)"))
            }
            route = fresh
        }
        return route ?: (null to InetAddress.getByName(FALLBACK_DNS))
    }

    /**
     * A network that is not our tunnel, and the resolver that belongs to it.
     *
     * The two have to be chosen together, and the socket has to be bound to the
     * network afterwards. A phone commonly has wifi and mobile data up at the
     * same time, each with its own resolver, and an ISP's resolver is only
     * reachable from inside that ISP's network. Picking the mobile resolver
     * while the traffic leaves over wifi does not fail fast — it hangs until
     * the timeout, on every single query, which would look exactly like the
     * silence AdZero reserves for ad servers.
     */
    private fun findRoute(): Pair<Network, InetAddress>? = try {
        val cm = getSystemService(ConnectivityManager::class.java)

        fun usable(n: Network): Boolean {
            val caps = cm.getNetworkCapabilities(n) ?: return false
            return !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        // The network the phone would be using by itself. Before the tunnel is
        // up that is simply the active one; afterwards the active one is the
        // tunnel, so the ranking below stands in for the choice the system had
        // already made — a validated network first, wifi ahead of mobile data.
        val active = cm.activeNetwork
        val candidates: List<Network> =
            if (active != null && usable(active)) listOf(active)
            else cm.allNetworks.filter { usable(it) }.sortedBy { network ->
                val caps = cm.getNetworkCapabilities(network)
                val validated = caps?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true
                val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                (if (validated) 0 else 2) + (if (wifi) 0 else 1)
            }

        candidates.firstNotNullOfOrNull { network ->
            // IPv4 first: the tunnel is v4-only, and a v6 resolver on a network
            // whose v6 does not actually work is another silent timeout.
            cm.getLinkProperties(network)?.dnsServers
                ?.sortedBy { if (it is Inet4Address) 0 else 1 }
                ?.firstOrNull()
                ?.let { network to it }
        }
    } catch (e: Exception) {
        Log.d(TAG, "no system resolver available", e)
        null
    }

    private var attribution: Attribution? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var lastNotice = 0L

    private val pool = Executors.newFixedThreadPool(8)
    private lateinit var toTunnel: FileOutputStream

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            if (running) {
                lastNotice = 0L
                refreshNotice()
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_REPORT) {
            Report.offer(this)
            return START_STICKY
        }
        if (intent?.action == ACTION_BLOCK_OFFERED) {
            Report.blockOffered(this)
            return START_STICKY
        }
        start()
        return START_STICKY
    }

    private fun start() {
        if (running) return
        // The banner and the bubble draw from here, with no activity involved.
        Ui.initFonts(this)
        Stats.init(this)
        AdNetworks.init(this)
        Learning.init(this)
        Leaderboard.init(this)
        History.init(this)
        Shield.init(this)
        // One ad in the history per ad in the leaderboard, never per request.
        // The same signal arms the shield: a burst means an ad is loading now.
        Leaderboard.onAttempt = { app ->
            History.recordAd()
            Shield.openWindow(app)
        }
        AppFilter.init(this)
        attribution = Attribution(this)

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(LOCAL_IP, 32)
            .addDnsServer(DNS_IP)
            // The tunnel's only route. Without it every packet would come
            // through the app; with it, only DNS reaches us.
            .addRoute(DNS_IP, 32)
            .setBlocking(true)

        // Excluding is the right primitive here: everything is protected, and
        // the user removes the apps they want left alone.
        for (pkg in AppFilter.excludedApps()) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // Uninstalled since it was excluded: skip it rather than
                // failing to establish the tunnel at all.
            }
        }
        AppFilter.dirty = false

        val fd = builder.establish()

        if (fd == null) {
            // Most likely another VPN already holds the slot — Android allows
            // only one. Say so instead of dying quietly.
            Log.e(TAG, "establish() returned null")
            Stats.error = getString(R.string.error_no_tunnel)
            Stats.markRunning(false)
            stopSelf()
            return
        }

        tunnel = fd
        toTunnel = FileOutputStream(fd.fileDescriptor)
        running = true
        alive = true
        Stats.error = null
        Stats.markRunning(true)

        // Whoever was on screen just before this moment is holding stale
        // addresses. Recorded here because it is the only instant where the
        // question "before or after protection" still has an answer.
        Banner.markStale(appOnScreenBefore())

        // Written every minute and a half from here on: an update kills this
        // process without a clean shutdown, and whatever was only in memory
        // went with it.
        Persist.startAutosave()

        startForegroundNotice()
        AdZeroWidget.refresh(this)
        if (Stats.bubbleWanted) Bubble.show(this)

        worker = Thread({ loop(FileInputStream(fd.fileDescriptor)) }, "dns-tunnel")
            .also { it.start() }
    }

    private fun loop(fromTunnel: FileInputStream) {
        val buf = ByteArray(32767)
        while (running) {
            val read = try {
                fromTunnel.read(buf)
            } catch (e: Exception) {
                if (running) Log.w(TAG, "tunnel read interrupted", e)
                break
            }
            if (read <= 0) continue

            val query = Packets.readUdp(buf, read) ?: continue
            if (query.destPort != 53) continue

            val payload = query.payload
            val host = Packets.queriedName(payload)
            if (host == null) {
                forward(query, payload)
                continue
            }

            // One attribution, shared by the learning and the leaderboard.
            val app = attribution?.whoAsked(query) ?: "?"
            val kind = AdNetworks.classify(host)
            // Nothing recognised the name. Ask the shield whether the timing
            // gives it away instead — and let it watch the quiet moments,
            // which is how it learns what this app normally talks to.
            val byTiming = kind == AdNetworks.Kind.NONE &&
                    Shield.shouldSilence(app, Stats.rootOf(host))
            // A pause lets everything through without dropping the tunnel.
            val isAd = kind != AdNetworks.Kind.NONE || byTiming

            // Every query feeds the learning, including the ones we let
            // through: that is precisely where unknown ad networks hide.
            // In an app that serves its own ads, what gets silenced here is a
            // tracker, never one of its ads. Counting those as ads blocked
            // inflated the figure with the one thing AdZero cannot do.
            val ownAds = FirstParty.hopeless(app)

            Learning.observe(host, app)
            Leaderboard.observe(app, host, isAd && !ownAds)
            // Kept for a few seconds only, so that if an ad slips through the
            // user can point at it while the evidence is still around.
            Recent.note(host, app, isAd)

            if (isAd) {
                // Silence is the whole point: we do not answer.
                Stats.record(host, silencedHost = true)
                // Opens the window in which anything else this app resolves is
                // probably part of the same ad load.
                Learning.noteAdMoment(app)
                if (kind == AdNetworks.Kind.TRACKER || ownAds) {
                    Stats.trackers.incrementAndGet()
                } else {
                    Banner.onAdKilled(this, app)
                    refreshNotice()
                }
            } else {
                Stats.record(host, silencedHost = false)
                forward(query, payload)
            }
        }
    }

    /**
     * The app that was on screen just before protection started, if any.
     *
     * Deliberately one app, not a list. Marking everything used in the past
     * few minutes flagged every game on the phone, so the first alert each of
     * them showed said "close this" instead of counting a blocked ad — the
     * warning drowned the thing it was supposed to qualify.
     *
     * Only the app someone was actually in is holding stale addresses, and
     * that is the last one resumed before AdZero itself came to the front.
     */
    private fun appOnScreenBefore(): List<String> = try {
        val usage = getSystemService(android.app.usage.UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 3 * 60_000, now)
        val event = android.app.usage.UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = event.packageName ?: continue
            // Our own screen is what the user is looking at while switching
            // protection on; the app before it is the one that matters.
            if (pkg != packageName) last = pkg
        }
        listOfNotNull(last)
    } catch (_: Exception) {
        // No usage access: the banner simply never claims an app was stale.
        emptyList()
    }

    /** A legitimate query: pass it to a real resolver and hand back the answer. */
    private fun forward(query: Packets.Query, question: ByteArray) {
        pool.execute {
            try {
                DatagramSocket().use { socket ->
                    // Without protect(), the answer would loop back through the tunnel.
                    protect(socket)
                    socket.soTimeout = 5000
                    val (network, server) = upstream()
                    // Bound to the network the resolver belongs to, so the
                    // question leaves by the door it can be answered through.
                    network?.bindSocket(socket)
                    socket.send(DatagramPacket(question, question.size, server, 53))

                    val back = ByteArray(4096)
                    val packet = DatagramPacket(back, back.size)
                    socket.receive(packet)

                    val reply = Packets.buildReply(query, back.copyOf(packet.length))
                    synchronized(toTunnel) { toTunnel.write(reply) }
                }
            } catch (e: Exception) {
                // A DNS server that does not answer is not fatal: the app will
                // retry on its own.
                Log.d(TAG, "DNS forward failed", e)
            }
        }
    }

    private fun startForegroundNotice() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        startForeground(1, buildNotice())
    }

    private fun action(what: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, SilenceVpnService::class.java).setAction(what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotice(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = getString(R.string.notification_live, Leaderboard.totalAttempts())

        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.action_stop_short), action(ACTION_STOP, 11)
                ).build()
            )
            // The report has to be reachable from inside a game, and the
            // notification shade is the only surface that always is. An ad the
            // user has to remember about until they next open the app is an ad
            // that never gets reported.
            .apply {
                if (Stats.reportWanted) addAction(
                    Notification.Action.Builder(
                        null, getString(R.string.report_action), action(ACTION_REPORT, 12)
                    ).build()
                )
            }
            .build()

    }

    /** Refreshed at most every few seconds: a counter that repaints on every
     *  killed request would wake the notification shade constantly. */
    private fun refreshNotice() {
        val now = System.currentTimeMillis()
        if (now - lastNotice < 4000) return
        lastNotice = now
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotice())
        } catch (_: Exception) {
        }
    }

    private fun stopEverything() {
        running = false
        alive = false
        Persist.stopAutosave()
        Bubble.hide()
        Banner.clear()
        Stats.markRunning(false)
        Persist.saveAll()
        AdZeroWidget.refresh(this)
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
        worker?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        alive = false
        Bubble.hide()
        Banner.clear()
        Stats.markRunning(false)
        Persist.saveAll()
        AdZeroWidget.refresh(this)
        pool.shutdownNow()
        try { tunnel?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
