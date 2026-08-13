package com.adzero.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.system.OsConstants
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Which app does a DNS query belong to?
 *
 * Two routes, most reliable first:
 *
 * 1. getConnectionOwnerUid() returns the exact owner of the socket. That only
 *    works when the app resolves DNS itself; when it goes through the system
 *    resolver, the UID returned is the resolver's, not the app's.
 * 2. In that case we fall back to the foreground app. Approximate, but plenty
 *    good enough for what we count: how many *distinct apps* query a domain.
 */
class Attribution(private val ctx: Context) {

    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val pm = ctx.packageManager
    private val uidCache = HashMap<Int, String>()

    private var lastForeground = "?"
    private var lastLookup = 0L

    fun whoAsked(query: Packets.Query): String {
        byUid(query)?.let { return it }
        return foreground()
    }

    private fun byUid(query: Packets.Query): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val uid = cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(query.sourceIp), query.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(query.destIp), query.destPort),
            )
            // Below 10000 are system accounts, including the DNS resolver:
            // they tell us nothing useful.
            if (uid < Process.FIRST_APPLICATION_UID) return null
            uidCache.getOrPut(uid) {
                pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Needs usage access; returns "?" otherwise. */
    private fun foreground(): String {
        val now = System.currentTimeMillis()
        if (now - lastLookup < 1500) return lastForeground
        lastLookup = now

        lastForeground = try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(now - 60_000, now)
            val event = UsageEvents.Event()
            var latest = "?"
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latest = event.packageName
                }
            }
            latest
        } catch (_: Throwable) {
            "?"
        }
        return lastForeground
    }

    companion object {
        /**
         * Has the user granted usage access?
         *
         * Ask AppOps directly. Probing for recent events is tempting but
         * wrong: it answers "no" whenever nothing happened in the window,
         * even with the permission granted, so the setup card would keep
         * asking for something already given.
         */
        fun usageAccessGranted(ctx: Context): Boolean = try {
            val ops = ctx.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }
}
