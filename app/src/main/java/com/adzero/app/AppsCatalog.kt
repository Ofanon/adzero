package com.adzero.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/**
 * The installed apps, with their icons, loaded once off the main thread.
 *
 * Reading a couple of hundred icons takes long enough to drop frames, and the
 * list never changes while the screen is open, so it is built once and kept.
 */
object AppsCatalog {

    class Entry(val pkg: String, val label: String, val icon: Drawable, val isGame: Boolean)

    @Volatile private var cache: List<Entry>? = null
    @Volatile private var loading = false

    fun cached(): List<Entry>? = cache

    /**
     * Single icon, without waiting for the whole catalogue.
     *
     * Falls back to the system's default app icon rather than null: a missing
     * icon used to leave a hole and knock the whole row out of alignment.
     */
    fun iconFor(ctx: Context, pkg: String): Drawable? {
        // Callers that draw this themselves must take a copy first: these are
        // shared instances, and writing alpha or bounds on one is visible
        // everywhere it is used.
        cache?.firstOrNull { it.pkg == pkg }?.let { return it.icon }
        return try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (_: Exception) {
            try {
                ctx.packageManager.defaultActivityIcon
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Games, as declared by the Play Store category. */
    fun games(): List<Entry> = cache?.filter { it.isGame } ?: emptyList()

    /**
     * A handful of game icons, fast.
     *
     * The onboarding shows six icons but [load] resolves every installed app
     * before it hands anything back — a couple of hundred icon reads, which is
     * why the row used to appear seconds after the page. This walks the same
     * list but only decodes the icons it actually needs.
     */
    fun quickGames(ctx: Context, count: Int, onReady: (List<Entry>) -> Unit) {
        cache?.let { full ->
            val games = full.filter { it.isGame }
            onReady((if (games.size >= count) games else full).take(count))
            return
        }
        val app = ctx.applicationContext
        Thread({
            val pm = app.packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            // queryIntentActivities already carries applicationInfo, so the
            // category test costs nothing — only loadIcon is expensive.
            val infos = pm.queryIntentActivities(main, 0)
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != app.packageName }
            val games = infos.filter {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        it.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            }
            val picked = (if (games.size >= count) games else infos).take(count)
            val out = picked.mapNotNull { info ->
                try {
                    Entry(
                        info.packageName,
                        pm.getApplicationLabel(info).toString(),
                        pm.getApplicationIcon(info),
                        true,
                    )
                } catch (_: Exception) {
                    null
                }
            }
            Handler(Looper.getMainLooper()).post { onReady(out) }
        }, "apps-quick").start()
    }

    /** Loads in the background and calls [onReady] on the main thread. */
    fun load(ctx: Context, onReady: (List<Entry>) -> Unit) {
        cache?.let { onReady(it); return }
        if (loading) return
        loading = true

        val app = ctx.applicationContext
        Thread({
            val pm = app.packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(main, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .filter { it != app.packageName }
                .mapNotNull { pkg ->
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val game = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                        Entry(
                            pkg,
                            pm.getApplicationLabel(info).toString(),
                            pm.getApplicationIcon(info),
                            game,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                .sortedBy { it.label.lowercase() }

            cache = list
            loading = false
            Handler(Looper.getMainLooper()).post { onReady(list) }
        }, "apps-catalog").start()
    }
}
