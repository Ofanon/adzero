package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * The centrepiece: a large breathing shape carrying a rotating gradient.
 *
 * Three things give it life, and none of them is an image:
 *  - the outline is a circle whose radius is modulated by three sine waves of
 *    different periods, so the silhouette never repeats;
 *  - a sweep gradient slowly rotates inside it, which reads as light moving
 *    across a surface;
 *  - a halo behind pulses on a slower cycle than the shape itself.
 *
 * When filtering is off everything desaturates and slows down, so the state is
 * legible without reading a word.
 */
@SuppressLint("ViewConstructor")
class HeroView(ctx: Context) : View(ctx) {

    var active: Boolean = false
    var number: String = "0"
    var caption: String = ""

    private val born = System.nanoTime()
    private val path = Path()
    private val matrix = Matrix()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.TEXT
        typeface = Ui.BOLD
        textAlign = Paint.Align.CENTER
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GREY
        typeface = Ui.REGULAR
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.32f

        val a = if (active) Ui.ACCENT_A else Ui.IDLE_A
        val b = if (active) Ui.ACCENT_B else Ui.IDLE_B
        val speed = if (active) 1.0 else 0.35

        // Halo: slower cycle than the shape, so the two never beat together.
        val breath = 0.5 + 0.5 * sin(t * 0.6 * speed)
        halo.color = a
        halo.alpha = if (active) (40 + 45 * breath).toInt() else 26
        halo.setShadowLayer(base * (0.75f + 0.12f * breath.toFloat()), 0f, 0f, a)
        canvas.drawCircle(cx, cy, base * 0.92f, halo)

        // Outer ring, drawn on the organic outline rather than a plain circle.
        organicOutline(cx, cy, base * 1.16f, t * speed, 0.9f)
        ring.strokeWidth = Ui.dp(context, 1).toFloat()
        ring.shader = sweep(cx, cy, a, b, t * speed * 12)
        ring.alpha = if (active) 110 else 55
        canvas.drawPath(path, ring)

        // The shape itself.
        organicOutline(cx, cy, base, t * speed, 1f)
        fill.shader = sweep(cx, cy, a, b, -t * speed * 18)
        fill.alpha = 255
        canvas.drawPath(path, fill)

        // A darker core so the number stays readable over the gradient.
        core.shader = RadialGradient(
            cx, cy, base * 0.86f,
            intArrayOf(Ui.BG_TOP, Color.argb(215, 11, 15, 26), Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, base * 0.86f, core)

        numberPaint.textSize = base * (if (number.length > 3) 0.52f else 0.68f)
        canvas.drawText(number, cx, cy + numberPaint.textSize * 0.30f, numberPaint)

        captionPaint.textSize = base * 0.13f
        captionPaint.letterSpacing = 0.14f
        canvas.drawText(caption.uppercase(), cx, cy + base * 0.46f, captionPaint)

        Ui.nextFrame(this, everyMs = Ui.FRAME_MEDIUM)
    }

    private fun sweep(cx: Float, cy: Float, a: Int, b: Int, degrees: Double): Shader {
        val shader = SweepGradient(cx, cy, intArrayOf(a, b, a), floatArrayOf(0f, 0.5f, 1f))
        matrix.reset()
        matrix.setRotate(degrees.toFloat(), cx, cy)
        shader.setLocalMatrix(matrix)
        return shader
    }

    /** [amplitude] lets the outer ring wobble less than the shape it surrounds. */
    private fun organicOutline(cx: Float, cy: Float, r: Float, t: Double, amplitude: Float) {
        path.reset()
        val steps = 96
        for (i in 0..steps) {
            val angle = 2.0 * Math.PI * i / steps
            val wobble = 1.0 + amplitude * (
                    0.042 * sin(3 * angle + t * 1.1) +
                            0.030 * sin(5 * angle - t * 0.7) +
                            0.018 * sin(7 * angle + t * 1.6)
                    )
            val rr = r * wobble
            val x = cx + (rr * cos(angle)).toFloat()
            val y = cy + (rr * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}
