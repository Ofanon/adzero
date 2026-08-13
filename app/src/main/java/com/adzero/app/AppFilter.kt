package com.adzero.app

import android.content.Context
import java.io.File

/**
 * Apps AdZero leaves alone.
 *
 * Protection is on everywhere by default, and picking an app *removes* it —
 * which is the way round people expect. The old logic was inverted: an empty
 * list meant "everything", and choosing an app silently excluded all the
 * others. Nobody guessed that.
 */
object AppFilter {

    private val excluded = mutableSetOf<String>()
    private var file: File? = null

    /** Set when the selection changed and the tunnel needs restarting. */
    @Volatile var dirty: Boolean = false

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "excluded_apps.txt")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { l -> l.trim().takeIf { it.isNotEmpty() }?.let { excluded.add(it) } }
        } catch (_: Exception) {
        }
    }

    fun protectsEverything(): Boolean = synchronized(excluded) { excluded.isEmpty() }

    fun isExcluded(pkg: String): Boolean = synchronized(excluded) { pkg in excluded }

    fun excludedApps(): Set<String> = synchronized(excluded) { excluded.toSet() }

    fun toggle(pkg: String) {
        synchronized(excluded) {
            if (!excluded.add(pkg)) excluded.remove(pkg)
        }
        dirty = true
        save()
    }

    fun protectEverythingAgain() {
        synchronized(excluded) { excluded.clear() }
        dirty = true
        save()
    }

    private fun save() {
        try {
            file?.writeText(synchronized(excluded) { excluded.joinToString("\n", postfix = "\n") })
        } catch (_: Exception) {
        }
    }
}
