package com.adzero.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One number per day: how many ads were killed.
 *
 * A running total says how much you have saved; a week of bars says whether
 * it is getting better. The second is the one people come back for.
 */
object History {

    private val key = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val days = LinkedHashMap<String, Int>()
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "history.txt")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val p = line.split("|")
                if (p.size == 2) days[p[0]] = p[1].toIntOrNull() ?: 0
            }
        } catch (_: Exception) {
        }
    }

    fun recordAd() {
        val today = key.format(Date())
        synchronized(days) {
            days[today] = (days[today] ?: 0) + 1
            // A year of daily counts is plenty; older entries help nobody.
            while (days.size > 400) days.remove(days.keys.first())
        }
    }

    class Day(val label: String, val count: Int, val isToday: Boolean)

    /** The last [span] days, oldest first, including days with nothing. */
    fun lastDays(span: Int = 7): List<Day> {
        val out = ArrayList<Day>(span)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(span - 1))
        val labels = arrayOf("D", "L", "M", "M", "J", "V", "S")
        val todayKey = key.format(Date())
        repeat(span) {
            val k = key.format(cal.time)
            val n = synchronized(days) { days[k] ?: 0 }
            out.add(Day(labels[cal.get(Calendar.DAY_OF_WEEK) - 1], n, k == todayKey))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** The best single day so far. */
    fun bestDay(): Int = synchronized(days) { days.values.maxOrNull() ?: 0 }

    /** How many days in a row, ending today, saw at least one ad killed. */
    fun streak(): Int {
        val cal = Calendar.getInstance()
        var count = 0
        while (true) {
            val n = synchronized(days) { days[key.format(cal.time)] ?: 0 }
            // Today counts as unbroken even before the first ad of the day.
            if (n == 0 && count > 0) break
            if (n == 0 && count == 0) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val prev = synchronized(days) { days[key.format(cal.time)] ?: 0 }
                if (prev == 0) break
                continue
            }
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    fun save() {
        val f = file ?: return
        try {
            val copy = synchronized(days) { days.toMap() }
            f.bufferedWriter().use { w ->
                for ((d, n) in copy) w.write("$d|$n\n")
            }
        } catch (_: Exception) {
        }
    }
}
