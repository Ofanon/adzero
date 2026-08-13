package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * Seven bars, one per day.
 *
 * Days with nothing still get a stub, otherwise the week looks broken rather
 * than quiet. Today's bar is brighter, which is the only cue needed to read
 * the chart without a legend.
 */
@SuppressLint("ViewConstructor")
class HistoryView(ctx: Context) : View(ctx) {

    var days: List<History.Day> = emptyList()

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.REGULAR
        color = Ui.DIM
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.BOLD
        color = Ui.GREY
        typeface = Ui.BOLD
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val d = { v: Int -> Ui.dp(context, v).toFloat() }

        val labelRoom = d(18)
        val valueRoom = d(16)
        val top = valueRoom
        val bottom = height - labelRoom
        val usable = bottom - top
        val slot = width.toFloat() / days.size
        val barWidth = slot * 0.46f
        val max = days.maxOf { it.count }.coerceAtLeast(1)

        label.textSize = d(11)
        value.textSize = d(11)

        for ((i, day) in days.withIndex()) {
            val cx = slot * i + slot / 2f
            val h = (usable * day.count / max.toFloat()).coerceAtLeast(d(3))
            val rect = RectF(cx - barWidth / 2, bottom - h, cx + barWidth / 2, bottom)

            bar.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(Ui.LIME_A, Ui.LIME_B), null, Shader.TileMode.CLAMP
            )
            bar.alpha = if (day.isToday) 255 else 120
            canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, bar)

            if (day.count > 0) {
                value.alpha = if (day.isToday) 255 else 160
                canvas.drawText(day.count.toString(), cx, rect.top - d(4), value)
            }
            label.alpha = if (day.isToday) 255 else 150
            canvas.drawText(day.label, cx, height - d(3), label)
        }
    }
}
