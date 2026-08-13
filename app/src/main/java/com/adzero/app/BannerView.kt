package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.sin

/**
 * The heads-up pill that drops in when AdZero starts killing ads in the app
 * you just opened.
 *
 * It slides down, waits, and leaves on its own. A live dot pulses on the left
 * so it reads as something happening now rather than a static notice.
 */
@SuppressLint("ViewConstructor")
class BannerView(
    ctx: Context,
    private val title: String,
    private val subtitle: String,
    private val appIcon: android.graphics.drawable.Drawable? = null,
) : View(ctx) {

    /**
     * How long the pill stays at rest, between sliding in and sliding out.
     *
     * The whole animation is computed here, from the clock, rather than driven
     * from outside by a ValueAnimator. Animators obey the system animation
     * scale, and a phone with animations turned off — battery saver does it,
     * so does the accessibility setting — ran the animator to completion
     * without ever delivering an update. The progress stayed at zero and the
     * banner was added to the screen fully transparent: present, invisible.
     * A clock cannot be scaled to zero.
     */
    var restMs = 3200L

    private val born = System.nanoTime()

    /** 0 = off screen, 1 = fully in place. */
    private var progress = 0f

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.TEXT
        typeface = Ui.BOLD
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GREY }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - born) / 1_000_000_000.0
        val ms = t * 1000.0
        val slideIn = (ms / 260.0).coerceIn(0.0, 1.0)
        val slideOut = if (ms < restMs) 1.0 else 1.0 - (ms - restMs) / 220.0
        progress = minOf(slideIn, slideOut).coerceIn(0.0, 1.0).toFloat()

        val d = { v: Int -> Ui.dp(context, v) }

        val margin = d(14).toFloat()
        // The gap above the pill, separate from the side margins. The window
        // already starts below the status bar, so re-using the 14dp side
        // margin here stacked two offsets and left the alert floating well
        // below where a system heads-up notification sits.
        val topGap = d(10).toFloat()
        val height = d(64).toFloat()
        // Slides in from above and fades at the same time.
        val top = topGap + (progress - 1f) * (height + topGap * 2)

        val rect = RectF(margin, top, width - margin, top + height)
        val radius = height / 2f

        pill.color = Color.parseColor("#FF0E1109")
        pill.setShadowLayer(d(18).toFloat(), 0f, d(4).toFloat(), Color.parseColor("#CC000000"))
        pill.alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, pill)

        edge.shader = LinearGradient(
            rect.left, 0f, rect.right, 0f,
            intArrayOf(Ui.LIME_A, Ui.BORDER), null, Shader.TileMode.CLAMP
        )
        edge.strokeWidth = d(2).toFloat()
        edge.alpha = (210 * progress).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, edge)

        // The app's own icon says which game this is about faster than the
        // title does; the pulsing halo behind it says it is happening now.
        val pulse = 0.55 + 0.45 * sin(t * 5.0)
        val cx = rect.left + d(30)
        val cy = rect.centerY()
        dot.color = Ui.LIME_A
        dot.alpha = (70 * pulse * progress).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, d(19) * (1f + 0.14f * pulse.toFloat()), dot)

        if (appIcon != null) {
            val r = d(15)
            appIcon.setBounds((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
            appIcon.alpha = (255 * progress).toInt().coerceIn(0, 255)
            appIcon.draw(canvas)
        } else {
            dot.alpha = (255 * progress).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, d(6).toFloat(), dot)
        }

        val textLeft = rect.left + d(56)
        titlePaint.textSize = d(14).toFloat()
        titlePaint.alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.drawText(
            ellipsize(title, titlePaint, rect.right - textLeft - d(18)),
            textLeft, cy - d(3).toFloat(), titlePaint
        )

        subPaint.textSize = d(11).toFloat()
        subPaint.alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.drawText(
            ellipsize(subtitle, subPaint, rect.right - textLeft - d(18)),
            textLeft, cy + d(14).toFloat(), subPaint
        )

        Ui.nextFrame(this, always = true)
    }

    private fun ellipsize(s: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(s) <= maxWidth) return s
        var cut = s.length
        while (cut > 1 && paint.measureText(s.substring(0, cut) + "…") > maxWidth) cut--
        return s.substring(0, cut) + "…"
    }
}
