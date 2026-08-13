package com.adzero.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Near-black background, one loud lime accent, and the logo's red kept for
 * stopping. Two colours only: anything that is not the accent is a shade of
 * grey, which is what makes the accent read as "on".
 */
object Ui {

    val BG_TOP = Color.parseColor("#080A08")
    val BG_BOTTOM = Color.parseColor("#0E120D")

    val PANEL_TOP = Color.parseColor("#141812")
    val PANEL_BOTTOM = Color.parseColor("#10140F")
    val BORDER = Color.parseColor("#20261C")

    val TEXT = Color.parseColor("#F2F5EE")
    val GREY = Color.parseColor("#8A9382")
    val DIM = Color.parseColor("#525B4C")

    /** The signature lime, straight from the reference. */
    val LIME_A = Color.parseColor("#C9F73D")
    val LIME_B = Color.parseColor("#8BE04A")

    /** The logo's red: only ever used to stop something. */
    val RED_A = Color.parseColor("#FF4D4D")
    val RED_B = Color.parseColor("#E23434")

    val IDLE_A = Color.parseColor("#333A2E")
    val IDLE_B = Color.parseColor("#242A20")

    // Older call sites.
    val BACKGROUND = BG_TOP
    val PANEL = PANEL_TOP
    val ACCENT_A = LIME_A
    val ACCENT_B = LIME_B
    val GREEN = LIME_A
    val RED = RED_A
    val AMBER = LIME_A

    /**
     * Whether the in-app animations should keep running.
     *
     * They repaint every frame, so left unchecked the UI thread never rests
     * while the activity exists — costly, and it also keeps the system from
     * ever considering the screen idle. Turned off as soon as the app is not
     * in front. Overlays (bubble, banner) are not affected: they animate
     * precisely when the app is *not* in front.
     */
    @Volatile
    var animating: Boolean = true

    /**
     * Montserrat, in the three weights the app actually uses.
     *
     * Loaded from the assets rather than res/font because the app supports
     * Android 7, where font resources need an androidx compatibility layer
     * this project deliberately does not have. Assets work everywhere.
     *
     * They start as the system faces, so nothing anywhere has to check whether
     * the fonts finished loading: the worst case is one frame of Roboto.
     */
    var REGULAR: Typeface = Typeface.DEFAULT
        private set
    var MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private set
    var BOLD: Typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        private set

    @Volatile private var fontsLoaded = false

    fun initFonts(ctx: Context) {
        if (fontsLoaded) return
        try {
            val assets = ctx.applicationContext.assets
            REGULAR = Typeface.createFromAsset(assets, "montserrat_regular.ttf")
            MEDIUM = Typeface.createFromAsset(assets, "montserrat_medium.ttf")
            BOLD = Typeface.createFromAsset(assets, "montserrat_bold.ttf")
            fontsLoaded = true
        } catch (_: Exception) {
            // Keep the system faces rather than crash over a missing file.
        }
    }

    /**
     * Schedules the next frame of a continuous animation, at a sane rate.
     *
     * Measured on the phone: with every animated view repainting on every
     * display frame, the app burned 97% of a core the whole time it was open —
     * and nothing at all once backgrounded. The blurs and glows are drawn on
     * the CPU because Android will not blur on the GPU, so each frame is
     * genuinely expensive, and there were four of them running at once.
     *
     * These are slow organic movements: sine waves with periods measured in
     * seconds. At 25 frames a second they look identical and cost a quarter as
     * much. Nothing here is a game.
     */
    private const val FRAME_MS = 40L

    /**
     * Not everything deserves the same rate.
     *
     * The drifting background is the most expensive frame in the app — a
     * full-screen blur, rasterised on the CPU — and the slowest movement:
     * blobs on a twenty-second cycle. Eight frames a second is indistinguishable
     * from sixty on something moving that slowly. The power button is where the
     * eye is, so it keeps the higher rate.
     */
    const val FRAME_SLOW = 120L
    const val FRAME_MEDIUM = 80L

    fun nextFrame(view: View, always: Boolean = false, everyMs: Long = FRAME_MS) {
        // The overlays are the exception to [animating]: the banner and the
        // bubble draw exactly when the app is not in front, which is when that
        // flag is false. They ask to keep going regardless.
        if (!always && !animating) return
        view.postInvalidateDelayed(everyMs)
    }

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun screenBackground(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM)
    )

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(PANEL_TOP, PANEL_BOTTOM)
        ).apply {
            cornerRadius = dp(ctx, 20).toFloat()
            setStroke(dp(ctx, 1), BORDER)
        }
        setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))
    }

    fun gradientPill(ctx: Context, a: Int, b: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)
    ).apply { cornerRadius = dp(ctx, 16).toFloat() }

    fun softPill(ctx: Context): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PANEL_TOP, PANEL_BOTTOM)
    ).apply {
        cornerRadius = dp(ctx, 16).toFloat()
        setStroke(dp(ctx, 1), BORDER)
    }

    fun title(ctx: Context, s: String) = TextView(ctx).apply {
        text = s
        setTextColor(TEXT)
        textSize = 26f
        typeface = Ui.BOLD
        // One line, always. It shares the header with a badge and a gear, and
        // on a narrow screen — or with the system font turned up — it was
        // given so little width that it wrapped to one letter per line.
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        // And it shrinks rather than truncates. Cutting the name of the app
        // down to nothing is not better than wrapping it; at 17sp it still
        // fits beside the badge on the narrowest phone worth supporting.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setAutoSizeTextTypeUniformWithConfiguration(
                17, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP
            )
        }
    }

    fun sectionLabel(ctx: Context, s: String) = TextView(ctx).apply {
        text = s
        setTextColor(DIM)
        textSize = 11f
        letterSpacing = 0.16f
        typeface = Ui.BOLD
    }

    fun body(ctx: Context, s: String) = TextView(ctx).apply {
        text = s
        typeface = REGULAR
        setTextColor(GREY)
        textSize = 14f
        setLineSpacing(dp(ctx, 5).toFloat(), 1f)
    }

    fun bigNumber(ctx: Context) = TextView(ctx).apply {
        typeface = BOLD
        setTextColor(TEXT)
        textSize = 40f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER
    }

    fun spacer(ctx: Context, height: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, height))
    }

    fun buttonBackground(ctx: Context, colour: Int) = GradientDrawable().apply {
        setColor(colour)
        cornerRadius = dp(ctx, 16).toFloat()
    }

    /**
     * Gives a view a real drop shadow.
     *
     * Android only casts a shadow from a view's outline, and a view with a
     * plain background has none — so elevation alone does nothing. Setting the
     * outline explicitly is what makes the button lift off the page.
     */
    fun lift(view: View, radiusDp: Int, elevationDp: Int, tint: Int? = null) {
        val ctx = view.context
        view.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, dp(ctx, radiusDp).toFloat())
            }
        }
        view.clipToOutline = true
        view.elevation = dp(ctx, elevationDp).toFloat()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && tint != null) {
            view.outlineAmbientShadowColor = tint
            view.outlineSpotShadowColor = tint
        }
    }

    /**
     * An icon set inside a line of text, centred on it.
     *
     * The stock ImageSpan offers alignment to the baseline or to the bottom of
     * the line, and neither is the middle: a small icon next to short text ends
     * up sitting noticeably low. This measures the line and centres on it.
     */
    class CenteredIcon(icon: android.graphics.drawable.Drawable) :
        android.text.style.ImageSpan(icon) {

        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint,
        ) {
            val icon = drawable
            canvas.save()
            canvas.translate(x, top + (bottom - top - icon.bounds.height()) / 2f)
            icon.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Clips a view to a circle.
     *
     * Adaptive icons already come out round because the system applies the
     * device mask to them. Legacy icons — the square bitmaps shipped by older
     * games that were never updated — arrive raw, and the launcher only looks
     * consistent because it rounds them itself. Any list of app icons has to
     * do the same or it ends up with squares scattered among circles.
     */
    fun circleClip(view: View) {
        view.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, v.width, v.height)
            }
        }
        view.clipToOutline = true
    }

    /** Typography: one family, three weights, tightened where it is large. */
    fun display(ctx: Context) = TextView(ctx).apply {
        setTextColor(TEXT)
        typeface = Ui.MEDIUM
    }
}
