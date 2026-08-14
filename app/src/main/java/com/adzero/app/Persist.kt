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

    /**
     * L'ecriture ne se fait pas sur le thread qui dessine.
     *
     * Le minuteur, lui, y reste : un Handler du Looper principal est la facon
     * la plus simple de reveiller quelque chose toutes les 90 secondes. Mais
     * ce qu'il declenche part sur ce fil-ci.
     *
     * C'etait deja douteux avec cinq petits fichiers ; le journal des requetes
     * en ajoute un de plusieurs milliers de lignes, et l'interface s'arretait
     * le temps de l'ecrire — toutes les 90 secondes, pendant les animations.
     *
     * Un seul fil, donc deux sauvegardes ne peuvent pas se chevaucher et
     * chaque store garde son verrou pour lui seul.
     */
    private val disk = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "adzero-disk").apply { isDaemon = true }
    }

    /**
     * Synchrone, et elle doit le rester : les deux appels de l'arret du
     * service se font quelques millisecondes avant que le processus meure, et
     * une ecriture partie en fond n'aurait pas le temps d'aboutir.
     */
    fun saveAll() {
        Stats.save()
        Learning.save()
        Leaderboard.save()
        History.save()
        Shield.save()
        Recent.save()
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
            // L'ecriture part en fond ; le prochain reveil est reprogramme
            // tout de suite, sans attendre qu'elle finisse.
            disk.execute { saveAll() }
            if (scheduled) main.postDelayed(this, EVERY_MS)
        }
    }
}
