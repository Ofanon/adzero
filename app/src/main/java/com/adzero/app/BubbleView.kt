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
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * The floating bubble.
 *
 * Nothing rectangular: the outline is a circle whose radius is modulated by
 * three sine waves of different periods. Because they never fall back into
 * phase, the shape does not repeat — it breathes instead of blinking.
 *
 * Every silenced request sends a ripple out from the centre.
 */
@SuppressLint("ViewConstructor")
class BubbleView(ctx: Context) : View(ctx) {

    var count: Int = 0
    var expanded: Boolean = false
    var domains: List<Pair<String, Int>> = emptyList()

    private val born = System.nanoTime()
    private val ripples = ArrayList<Long>()
    private var lastHit = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Ui.BOLD
        textAlign = Paint.Align.CENTER
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GREY
        typeface = Ui.REGULAR
    }
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Near-opaque so the text stays readable over a bright game.
        color = Color.parseColor("#F2101409")
    }
    private val path = Path()

    init {
        // The glow needs software rendering to blur.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** Called when a request has just been left unanswered. */
    fun pulse() {
        synchronized(ripples) {
            ripples.add(System.nanoTime())
            while (ripples.size > 5) ripples.removeAt(0)
        }
        lastHit = System.nanoTime()
        Ui.nextFrame(this, always = true)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0
        if (expanded) drawPanel(canvas, t) else drawBubble(canvas, t)
        // Keep animating: the shape breathes even with nothing happening.
        Ui.nextFrame(this, always = true)
    }

    // ------------------------------------------------------------- collapsed

    private fun drawBubble(canvas: Canvas, t: Double) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.30f

        // Heat: 1 right after a hit, back to 0 in about 1.2 s.
        val since = (System.nanoTime() - lastHit) / 1_000_000_000.0
        val heat = if (lastHit == 0L) 0.0 else (1.0 - since / 1.2).coerceIn(0.0, 1.0)

        drawRipples(canvas, cx, cy, base)

        // Resting lime, flaring to the app's red as requests are silenced.
        // It was green to orange, from a palette this app no longer has.
        val colour = blend(Ui.LIME_B, Ui.RED_A, heat.toFloat())

        glow.color = colour
        glow.alpha = (70 + 90 * heat).toInt().coerceAtMost(255)
        glow.setShadowLayer(base * 0.55f, 0f, 0f, colour)
        organicShape(cx, cy, base * (1f + 0.06f * heat.toFloat()), t)
        canvas.drawPath(path, glow)

        fill.shader = RadialGradient(
            cx, cy - base * 0.3f, base * 1.5f,
            intArrayOf(lighten(colour, 0.35f), colour),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, fill)

        label.textSize = base * (if (count > 999) 0.52f else 0.66f)
        canvas.drawText(count.toString(), cx, cy + label.textSize * 0.35f, label)
    }

    /**
     * The outline: a circle with a rippling radius. Three harmonics are enough
     * to make the motion unpredictable without making it jittery.
     */
    private fun organicShape(cx: Float, cy: Float, r: Float, t: Double) {
        path.reset()
        val steps = 64
        for (i in 0..steps) {
            val a = 2.0 * Math.PI * i / steps
            val wobble = 1.0 +
                    0.055 * sin(3 * a + t * 1.1) +
                    0.038 * sin(5 * a - t * 0.7) +
                    0.022 * sin(7 * a + t * 1.6)
            val rr = r * wobble
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun drawRipples(canvas: Canvas, cx: Float, cy: Float, base: Float) {
        val now = System.nanoTime()
        val snapshot = synchronized(ripples) { ripples.toList() }
        for (start in snapshot) {
            val age = (now - start) / 1_000_000_000.0
            if (age > 1.4) continue
            val p = (age / 1.4).toFloat()
            stroke.color = Ui.LIME_A
            stroke.alpha = ((1f - p) * 130).toInt()
            stroke.strokeWidth = base * 0.09f * (1f - p)
            canvas.drawCircle(cx, cy, base * (1f + p * 1.6f), stroke)
        }
        synchronized(ripples) { ripples.removeAll { (now - it) / 1_000_000_000.0 > 1.4 } }
    }

    // -------------------------------------------------------------- expanded

    /**
     * The open panel.
     *
     * It used to be a leftover from an earlier palette — green bubble, orange
     * hostnames, blue-grey labels — floating over a game while the rest of the
     * app was lime on near-black. And it listed raw domain names, which is the
     * one thing this project has learned nobody can read.
     *
     * Now it answers the question somebody actually has while playing: how
     * much has been stopped, and who was trying.
     */
    private fun drawPanel(canvas: Canvas, t: Double) {
        val m = width * 0.04f
        val r = RectF(m, m, width - m, height - m)
        canvas.drawRoundRect(r, m * 2.2f, m * 2.2f, panel)

        // A small living bubble in the header keeps the visual link.
        val cx = r.left + m * 3.2f
        val cy = r.top + m * 3.2f
        organicShape(cx, cy, m * 2.0f, t)
        fill.shader = null
        fill.color = Ui.LIME_B
        canvas.drawPath(path, fill)

        label.color = Ui.BG_TOP
        label.textSize = m * 1.5f
        canvas.drawText("$count", cx, cy + label.textSize * 0.35f, label)

        small.textSize = m * 1.35f
        small.color = Ui.TEXT
        canvas.drawText(context.getString(R.string.bubble_killed), cx + m * 3.4f, cy + m * 0.5f, small)

        // Minutes not spent watching, which is the part that means something.
        small.textSize = m * 1.15f
        small.color = Ui.GREY
        val seconds = count * Leaderboard.SECONDS_PER_AD
        canvas.drawText(
            context.getString(R.string.bubble_time, seconds / 60),
            cx + m * 3.4f, cy + m * 2.4f, small
        )

        small.textSize = m * 1.25f
        var y = r.top + m * 7.4f
        if (domains.isEmpty()) {
            small.color = Ui.DIM
            canvas.drawText("—", r.left + m * 1.6f, y, small)
        }
        for ((name, n) in domains.take(4)) {
            // The owner's name where it is known, the hostname only as a
            // fallback: "AppLovin" tells somebody something, "d.applvn.com"
            // does not.
            val who = Explain.cardFor(name)
            small.color = Ui.LIME_A
            canvas.drawText(
                (if (who.owner.isNotEmpty()) who.owner else name).take(22),
                r.left + m * 1.6f, y, small
            )
            small.color = Ui.DIM
            canvas.drawText("$n", r.right - m * 1.8f, y, small)
            y += m * 2.1f
        }
    }

    // --------------------------------------------------------------- colours

    private fun blend(a: Int, b: Int, p: Float): Int {
        val q = 1 - p
        return Color.rgb(
            (Color.red(a) * q + Color.red(b) * p).toInt(),
            (Color.green(a) * q + Color.green(b) * p).toInt(),
            (Color.blue(a) * q + Color.blue(b) * p).toInt(),
        )
    }

    private fun lighten(c: Int, p: Float): Int = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * p).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * p).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * p).toInt(),
    )
}
