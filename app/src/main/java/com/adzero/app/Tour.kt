package com.adzero.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The guided tour: a dark screen with one thing lit at a time.
 *
 * The welcome flow explains what the app does. It cannot explain where things
 * are, because at that point the screen it would be pointing at is still
 * hidden behind it. So this runs once afterwards, on the real screen, with the
 * real buttons in their real places.
 *
 * Everything is measured from the views themselves rather than from fixed
 * coordinates: the tour follows the layout wherever it ends up, on any screen
 * size, in any language.
 */
@SuppressLint("ViewConstructor")
class Tour(
    ctx: Context,
    private val steps: List<Step>,
    private val onFinish: () -> Unit,
) : FrameLayout(ctx) {

    class Step(val target: View, val title: String, val body: String)

    private var index = 0

    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60A0C08")
    }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Ui.LIME_A
    }

    private val card = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(d(22), d(20), d(22), d(20))
        background = Ui.card(ctx).background
    }
    private val title = TextView(ctx).apply {
        typeface = Ui.BOLD
        setTextColor(Ui.TEXT)
        textSize = 18f
    }
    private val body = Ui.body(ctx, "").apply { setTextColor(Ui.GREY) }
    private val progress = TextView(ctx).apply {
        typeface = Ui.BOLD
        setTextColor(Ui.DIM)
        textSize = 11f
        letterSpacing = 0.16f
    }
    private val next = TextView(ctx).apply {
        typeface = Ui.BOLD
        setTextColor(Ui.BG_TOP)
        textSize = 13f
        gravity = android.view.Gravity.CENTER
        setPadding(0, d(13), 0, d(13))
        background = Ui.gradientPill(ctx, Ui.LIME_A, Ui.LIME_B)
    }
    private val skip = TextView(ctx).apply {
        typeface = Ui.REGULAR
        text = ctx.getString(R.string.tour_skip)
        setTextColor(Ui.GREY)
        textSize = 12f
        gravity = android.view.Gravity.CENTER
        setPadding(0, d(12), 0, 0)
    }

    private fun d(v: Int) = Ui.dp(context, v)

    init {
        // The cut-out needs a layer it can erase from.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setWillNotDraw(false)
        isClickable = true

        card.addView(progress)
        card.addView(Ui.spacer(context, 8))
        card.addView(title)
        card.addView(Ui.spacer(context, 8))
        card.addView(body)
        card.addView(Ui.spacer(context, 18))
        card.addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(skip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        next.setOnClickListener { advance() }
        skip.setOnClickListener { finish() }
        paint()
    }

    private fun advance() {
        index++
        if (index >= steps.size) finish() else paint()
    }

    private fun finish() {
        (parent as? android.view.ViewGroup)?.removeView(this)
        onFinish()
    }

    private fun paint() {
        val step = steps[index]
        progress.text = context.getString(R.string.tour_step, index + 1, steps.size)
        title.text = step.title
        body.text = step.body
        next.text = context.getString(
            if (index == steps.size - 1) R.string.tour_done else R.string.tour_next
        )
        skip.visibility = if (index == steps.size - 1) GONE else VISIBLE
        requestLayout()
        invalidate()
    }

    /** The lit area, in this view's own coordinates. */
    private fun spotlight(): RectF {
        val step = steps.getOrNull(index) ?: return RectF()
        val mine = IntArray(2).also { getLocationInWindow(it) }
        val theirs = IntArray(2).also { step.target.getLocationInWindow(it) }
        val pad = d(10).toFloat()
        val inset = d(3).toFloat()
        // The stats row is nearly the full width of the screen, so the padded
        // rectangle would spill past both edges and the ring would be clipped.
        return RectF(
            (theirs[0] - mine[0] - pad).coerceAtLeast(inset),
            theirs[1] - mine[1] - pad,
            (theirs[0] - mine[0] + step.target.width + pad)
                .coerceAtMost(width - inset),
            theirs[1] - mine[1] + step.target.height + pad,
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val light = spotlight()
        val margin = d(20)
        val width = right - left - margin * 2

        card.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )

        // Below the lit area when there is room, above it otherwise: the card
        // must never cover the thing it is describing.
        //
        // On a short screen — or with the system font turned up — the card can
        // be taller than both gaps. It used to be pinned to the top in that
        // case and run off the bottom, taking Next with it. So the card being
        // wholly on screen comes first: a card that overlaps what it describes
        // is a nuisance, a card whose button cannot be reached is a dead end.
        val gap = d(20)
        val edge = d(24)
        val tall = card.measuredHeight
        val roomBelow = height - edge - (light.bottom + gap)
        val roomAbove = (light.top - gap) - edge

        val top = when {
            tall <= roomBelow -> (light.bottom + gap).toInt()
            tall <= roomAbove -> (light.top - gap - tall).toInt()
            // Neither fits: take the larger gap and clamp inside the screen.
            roomBelow >= roomAbove -> (height - edge - tall).coerceAtLeast(edge)
            else -> edge
        }
        card.layout(margin, top, margin + width, top + tall)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        val light = spotlight()
        val radius = d(20).toFloat()
        canvas.drawRoundRect(light, radius, radius, hole)
        ring.strokeWidth = d(2).toFloat()
        canvas.drawRoundRect(light, radius, radius, ring)
    }

    /** Taps on the lit area advance too, so poking the thing works. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val light = Rect()
            spotlight().round(light)
            if (light.contains(event.x.toInt(), event.y.toInt())) advance()
        }
        return true
    }
}
