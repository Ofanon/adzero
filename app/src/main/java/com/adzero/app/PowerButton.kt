package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The one control that matters: a big power button.
 *
 * Turning protection on should feel like something happened, so the switch is
 * not instant — the button sinks under the finger, a ring sweeps outward, and
 * the glow builds over about a second. The state is readable from across the
 * room: lime and breathing when on, dark and still when off.
 */
@SuppressLint("ViewConstructor")
class PowerButton(ctx: Context) : View(ctx) {

    var onToggle: (() -> Unit)? = null

    /** Target state. The visuals ease towards it rather than snapping. */
    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) burst = System.nanoTime()
            }
        }

    private var progress = 0f          // 0 = off, 1 = on, eased
    private var pressed = 0f           // 0 = released, 1 = fully pressed
    private var burst = 0L
    private val born = System.nanoTime()

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { pressed = 1f; invalidate(); return true }
            MotionEvent.ACTION_CANCEL -> { pressed = 0f; invalidate(); return true }
            MotionEvent.ACTION_UP -> {
                pressed = 0f
                performClick()
                onToggle?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0

        // Ease towards the target: roughly one second to settle.
        val target = if (active) 1f else 0f
        progress += (target - progress) * 0.06f
        if (kotlin.math.abs(target - progress) < 0.002f) progress = target

        val cx = width / 2f
        val cy = height / 2f
        val squeeze = 1f - 0.04f * pressed
        // The glow is a shadow layer, and a shadow is clipped by the view
        // bounds. Its reach is about 1.8x the radius, so the radius has to
        // stay under 0.5/1.8 of the half-side or the halo gets sliced off in
        // a visible straight line.
        val radius = minOf(width, height) * 0.24f * squeeze

        val breath = 0.5 + 0.5 * sin(t * 1.4)
        val a = mix(Ui.IDLE_A, Ui.LIME_A, progress)
        val b = mix(Ui.IDLE_B, Ui.LIME_B, progress)

        // Halo: grows with the state and pulses gently once on.
        glow.color = a
        glow.alpha = (30 + 150 * progress * (0.75 + 0.25 * breath)).toInt().coerceIn(0, 255)
        glow.setShadowLayer(radius * (0.55f + 0.25f * progress), 0f, 0f, a)
        organicOutline(cx, cy, radius * 0.95f, t, 1f)
        canvas.drawPath(path, glow)

        // The ring that sweeps outward when it is switched on.
        if (burst != 0L) {
            val age = (System.nanoTime() - burst) / 1_000_000_000.0
            if (age < 1.2) {
                val p = (age / 1.2).toFloat()
                ring.color = Ui.LIME_A
                ring.alpha = ((1f - p) * 160).toInt()
                ring.strokeWidth = radius * 0.10f * (1f - p)
                canvas.drawCircle(cx, cy, radius * (1.05f + p * 0.9f), ring)
            }
        }

        // Thin orbit line, so the button is not a bare disc.
        ring.color = a
        ring.alpha = (40 + 90 * progress).toInt()
        ring.strokeWidth = Ui.dp(context, 2).toFloat()
        val orbit = radius * 1.20f
        val sweep = 90f + 200f * progress
        canvas.drawArc(
            RectF(cx - orbit, cy - orbit, cx + orbit, cy + orbit),
            (t * 24 % 360).toFloat(), sweep, false, ring
        )

        // The disc itself.
        disc.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.4f, radius * 1.7f,
            intArrayOf(lighten(a, 0.25f * progress), b), null, Shader.TileMode.CLAMP
        )
        // Not a circle: the radius is modulated by three sine waves of
        // different periods, so the silhouette drifts and never repeats.
        organicOutline(cx, cy, radius, t, 1f)
        canvas.drawPath(path, disc)

        // Power glyph: a broken circle with a stem, drawn rather than an asset.
        val gr = radius * 0.42f
        glyph.color = if (progress > 0.5f) Ui.BG_TOP else Color.parseColor("#8A9382")
        glyph.strokeWidth = max(Ui.dp(context, 3).toFloat(), radius * 0.075f)
        path.reset()
        // Sweep from 30 to 330 degrees so the 60-degree gap sits centred at the
        // top, right under the stem, the way a power symbol is drawn.
        val start = 30.0
        val extent = 300.0
        val steps = 48
        for (i in 0..steps) {
            val ang = Math.toRadians(start + extent * i / steps)
            val x = cx + (gr * sin(ang)).toFloat()
            val y = cy - (gr * cos(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, glyph)
        canvas.drawLine(cx, cy - gr * 1.18f, cx, cy - gr * 0.10f, glyph)

        Ui.nextFrame(this, everyMs = 60L)
    }

    /** [amplitude] scales the wobble; 0 gives a plain circle. */
    private fun organicOutline(cx: Float, cy: Float, r: Float, t: Double, amplitude: Float) {
        path.reset()
        val steps = 84
        for (i in 0..steps) {
            val angle = 2.0 * Math.PI * i / steps
            val wobble = 1.0 + amplitude * (
                    0.045 * sin(3 * angle + t * 0.9) +
                            0.030 * sin(5 * angle - t * 0.6) +
                            0.018 * sin(7 * angle + t * 1.3)
                    )
            val rr = r * wobble
            val x = cx + (rr * cos(angle)).toFloat()
            val y = cy + (rr * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun mix(from: Int, to: Int, p: Float): Int {
        val q = 1 - p
        return Color.rgb(
            (Color.red(from) * q + Color.red(to) * p).toInt(),
            (Color.green(from) * q + Color.green(to) * p).toInt(),
            (Color.blue(from) * q + Color.blue(to) * p).toInt(),
        )
    }

    private fun lighten(c: Int, p: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * p).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * p).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * p).toInt(),
    )
}
