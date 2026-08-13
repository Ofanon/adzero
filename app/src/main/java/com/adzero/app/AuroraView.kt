package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A living background: three big, very faint blobs drifting behind everything.
 *
 * Each one moves on its own Lissajous path — different frequencies on x and y,
 * and different periods between blobs — so the composition never repeats and
 * never looks like it is looping. They are barely visible on purpose: you
 * should feel the screen breathing without being able to point at what moves.
 */
@SuppressLint("ViewConstructor")
class AuroraView(ctx: Context) : View(ctx) {

    /** Blobs brighten a little when protection is on. */
    var active: Boolean = false

    private val born = System.nanoTime()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var intensity = 0f

    private class Blob(
        val colour: Int,
        val radius: Float,   // fraction of the smaller side
        val cx: Float, val cy: Float,
        val ax: Float, val ay: Float,
        val fx: Double, val fy: Double,
        val phase: Double,
    )

    private val blobs = listOf(
        Blob(Ui.LIME_A, 0.85f, 0.22f, 0.18f, 0.16f, 0.10f, 0.037, 0.053, 0.0),
        Blob(Ui.LIME_B, 0.70f, 0.80f, 0.42f, 0.13f, 0.14f, 0.029, 0.041, 1.7),
        Blob(Color.parseColor("#2E7D5B"), 0.95f, 0.45f, 0.86f, 0.18f, 0.09f, 0.023, 0.031, 3.1),
    )

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0
        val target = if (active) 1f else 0.45f
        intensity += (target - intensity) * 0.04f

        val w = width.toFloat()
        val h = height.toFloat()
        val unit = minOf(w, h)

        for (b in blobs) {
            val x = w * (b.cx + b.ax * sin(t * b.fx * 2 * Math.PI + b.phase).toFloat())
            val y = h * (b.cy + b.ay * cos(t * b.fy * 2 * Math.PI + b.phase * 1.3).toFloat())
            val r = unit * b.radius

            // Low, but high enough to be felt: below about 40 it vanishes on
            // an OLED screen and the background looks flat again.
            val alpha = (52 * intensity).toInt().coerceIn(0, 255)
            paint.shader = RadialGradient(
                x, y, r,
                intArrayOf(
                    Color.argb(alpha, Color.red(b.colour), Color.green(b.colour), Color.blue(b.colour)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, r, paint)
        }

        Ui.nextFrame(this, everyMs = Ui.FRAME_SLOW)
    }
}
