package com.adzero.app

import android.os.Handler
import android.os.Looper

/**
 * Writes everything the app has learned to disk, on a schedule.
 *
 * The data was already local — five files in the app's own folder. What was
 * missing was writing them anywhere except at a clean shutdown, and an update
 * is not a clean shutdown: Android kills the process to replace it, onDestroy
 * never runs, and everything since the last stop is gone. Same for the system
 * reclaiming memory, and for a force stop.
 *
 * Losing counters is annoying. Losing [Shield]'s notebook of each app's usual
 * servers is worse: it disarms the burst shield everywhere until days of play
 * have rebuilt it.
 *
 * There is a second reason this exists as one function. The bug was not really
 * "no autosave" — it was that four call sites each listed the stores by hand,
 * and one of them had been written before the shield existed and never updated.
 * A single list cannot go out of step with itself.
 */
object Persist {

    /** Often enough that an update costs little, rarely enough to be free. */
    private const val EVERY_MS = 90_000L

    private val main = Handler(Looper.getMainLooper())
    private var scheduled = false

    fun saveAll() {
        Stats.save()
        Learning.save()
        Leaderboard.save()
        History.save()
        Shield.save()
    }

    /** Starts the periodic write. Safe to call more than once. */
    fun startAutosave() {
        if (scheduled) return
        scheduled = true
        main.postDelayed(loop, EVERY_MS)
    }

    fun stopAutosave() {
        scheduled = false
        main.removeCallbacks(loop)
    }

    private val loop = object : Runnable {
        override fun run() {
            saveAll()
            if (scheduled) main.postDelayed(this, EVERY_MS)
        }
    }
}
