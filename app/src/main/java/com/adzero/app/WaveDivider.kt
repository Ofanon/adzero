package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

/**
 * A soft wave between sections, instead of a straight rule or plain space.
 *
 * It drifts slowly and fades out at both ends, so it separates without
 * drawing a hard line across the screen — the whole point of the app's look
 * is that nothing is a rectangle.
 */
@SuppressLint("ViewConstructor")
class WaveDivider(ctx: Context) : View(ctx) {

    private val born = System.nanoTime()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return

        paint.strokeWidth = Ui.dp(context, 2).toFloat()
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.TRANSPARENT, Ui.LIME_A, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = 70

        path.reset()
        val steps = 60
        for (i in 0..steps) {
            val x = w * i / steps
            // Two frequencies so the crest never sits at the same place twice.
            val y = h / 2f + (h * 0.26f) * (
                    sin(i * 0.30 + t * 0.7) * 0.7 + sin(i * 0.11 - t * 0.4) * 0.3
                    ).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        Ui.nextFrame(this, everyMs = Ui.FRAME_MEDIUM)
    }
}
