package com.adzero.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * Announces, on top of whatever app you just opened, that AdZero is working.
 *
 * The trigger is real: the banner only appears once ads have actually been
 * killed for that app. A timer-based version would eventually lie, and a
 * notice that lies is worse than no notice.
 *
 * One announcement per app at most every few minutes, so it stays an event
 * rather than becoming wallpaper.
 */
object Banner {

    private const val VISIBLE_MS = 3200L

    /**
     * Once per app per play session, which is what people expect it to mean.
     *
     * It used to be once every five minutes, so a long session produced an
     * alert every five minutes — enough to read as something restarting, and
     * enough to make somebody switch protection off and on to make it stop.
     *
     * A session ends when an app has gone quiet for a while: no ad traffic
     * from it for three minutes means it was closed or left alone, and the
     * next burst is a fresh visit worth announcing once.
     */
    private const val SESSION_GAP_MS = 3 * 60_000L

    /** A floor regardless of sessions, so a stutter cannot produce two. */
    private const val COOLDOWN_MS = 60_000L
    /** Ads killed for an app before we bother saying anything. */
    private const val TRIGGER_KILLS = 2

    /**
     * Apps that were already running when protection started, and until when
     * they stay suspect.
     *
     * They looked up their ad servers before the tunnel existed, so those
     * addresses are sitting in their own memory and they connect straight to
     * them. AdZero silences the next lookup and counts a win it did not get.
     * Telling these apps apart is the difference between "ads blocked" and
     * "close this and open it again", and only the second one is true.
     */
    private val stale = HashMap<String, Long>()

    /** How long cached addresses stay a plausible explanation. */
    private const val STALE_MS = 10 * 60_000L

    fun markStale(apps: Collection<String>) {
        val until = System.currentTimeMillis() + STALE_MS
        synchronized(stale) {
            stale.clear()
            for (app in apps) stale[app] = until
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val lastShown = HashMap<String, Long>()
    private val pending = HashMap<String, Int>()

    /** When each app last had an ad silenced, to tell sessions apart. */
    private val lastActivity = HashMap<String, Long>()
    private val announcedThisSession = HashSet<String>()

    private var view: BannerView? = null
    private var wm: WindowManager? = null

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)

    /**
     * Called for every killed ad request. Decides on its own whether this is
     * worth interrupting the user for.
     */
    fun onAdKilled(ctx: Context, app: String) {
        if (!Stats.bannerWanted || app == "?" || app == ctx.packageName) return
        if (!allowed(ctx)) return

        val now = System.currentTimeMillis()
        synchronized(lastShown) {
            // A long enough silence from this app means it was closed: the next
            // ad belongs to a new visit, and gets one announcement.
            if (now - (lastActivity[app] ?: 0L) > SESSION_GAP_MS) {
                announcedThisSession.remove(app)
            }
            lastActivity[app] = now

            if (app in announcedThisSession) return
            if (now - (lastShown[app] ?: 0L) < COOLDOWN_MS) return
            val count = (pending[app] ?: 0) + 1
            pending[app] = count
            if (count < TRIGGER_KILLS) return
            pending[app] = 0
            lastShown[app] = now
            announcedThisSession.add(app)
        }
        // Said once per app, then forgotten: it is advice, not a status.
        val wasOpen = synchronized(stale) {
            val until = stale[app] ?: 0L
            if (System.currentTimeMillis() < until) { stale.remove(app); true } else false
        }
        main.post { show(ctx, label(ctx, app), app, wasOpen) }
    }

    private fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    private fun show(ctx: Context, appLabel: String, pkg: String, wasOpen: Boolean) {
        hideNow()
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        // Never announce "0 ads killed": the banner fires after two silenced
        // requests, while the ad counter only moves once a whole burst is
        // grouped. Showing the zero was both wrong and deflating.
        val killed = Leaderboard.totalAttempts()
        val subtitle = if (killed > 0) ctx.getString(R.string.banner_sub, killed)
        else ctx.getString(R.string.banner_sub_first)

        // Three things this can announce, and only one of them is a win.
        //
        // In an app that serves its own ads, "12 ads blocked" claims something
        // AdZero cannot do — the exact false claim the report screen was just
        // taught to refuse. What it really blocked there is trackers, so that
        // is what it says.
        val firstParty = FirstParty.hopeless(pkg)

        val v = BannerView(
            ctx,
            ctx.getString(
                when {
                    firstParty -> R.string.banner_firstparty_title
                    wasOpen -> R.string.banner_stale_title
                    else -> R.string.banner_title
                },
                appLabel
            ),
            when {
                firstParty -> ctx.getString(R.string.banner_firstparty_sub)
                wasOpen -> ctx.getString(R.string.banner_stale_sub)
                else -> subtitle
            },
            // A private copy. The banner fades its icon by writing alpha on
            // the Drawable, and Drawables from the package manager are shared
            // instances — fading the shared one left every later use of that
            // icon stuck at alpha 0, which is why an app that had shown a
            // banner then had no icon anywhere else.
            AppsCatalog.iconFor(ctx, pkg)?.constantState?.newDrawable()?.mutate(),
        ).apply { restMs = VISIBLE_MS }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            Ui.dp(ctx, 110),
            type,
            // Not touchable: the banner must never eat a tap meant for the game.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            // Flush with the top of the screen. It used to be pushed down by
            // the height of the status bar, which is the right thing to do in
            // an app and the wrong thing here: games run full screen with the
            // bar hidden, so the offset was reserving space for something that
            // was not on screen.
        }

        try {
            manager.addView(v, params)
        } catch (_: Exception) {
            return
        }
        view = v
        wm = manager

        // The view animates itself off its own clock; all that is left here is
        // taking it down once it has finished sliding out.
        main.postDelayed({ hideNow() }, VISIBLE_MS + 500)
    }


    private fun hideNow() {
        view?.let { v -> try { wm?.removeView(v) } catch (_: Exception) {} }
        view = null
    }

    /** Called when protection stops, so nothing lingers on screen. */
    fun clear() {
        main.post { hideNow() }
        synchronized(lastShown) {
            pending.clear()
            announcedThisSession.clear()
            lastActivity.clear()
        }
    }
}
