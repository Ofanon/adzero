package com.adzero.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File

/**
 * Draws the statistics as an image somebody can send.
 *
 * The numbers already existed; what they could not do is leave the screen they
 * live on. An app spreads on a figure said out loud — "I have blocked fourteen
 * hundred ads" — and that sentence needs something to accompany it.
 *
 * This is the left half of a loop whose right half is already built: the card
 * is what makes somebody ask what it is, and "Send AdZero to someone" is the
 * answer. Neither is worth much without the other.
 *
 * Square, because every messaging app shows a square preview without cropping
 * the number out of it.
 */
object Card {

    private const val SIZE = 1080

    fun render(ctx: Context): File {
        Ui.initFonts(ctx)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = SIZE.toFloat()

        // Ground, then a soft lime glow behind the mark: the same two gestures
        // the app's own home screen is built from.
        paint.shader = LinearGradient(
            0f, 0f, 0f, s,
            Ui.BG_TOP, Ui.BG_BOTTOM, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = RadialGradient(
            s / 2f, s * 0.3f, s * 0.55f,
            intArrayOf(0x33C9F73D, 0x00000000), null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = null

        // The mark's own radius has to be counted in the gap: at the previous
        // spacing its bottom edge and the cap height of the word overlapped.
        drawMark(ctx, canvas, paint, s / 2f, s * 0.165f, s * 0.055f)

        paint.typeface = Ui.BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.color = Ui.TEXT
        paint.textSize = s * 0.055f
        canvas.drawText("AdZero", s / 2f, s * 0.30f, paint)

        // The number, at the size it deserves.
        val ads = Leaderboard.totalAttempts()
        paint.textSize = s * 0.235f
        paint.color = Ui.LIME_A
        canvas.drawText(ads.toString(), s / 2f, s * 0.52f, paint)

        paint.typeface = Ui.REGULAR
        paint.textSize = s * 0.042f
        paint.color = Ui.GREY
        canvas.drawText(ctx.getString(R.string.stat_ads).lowercase(), s / 2f, s * 0.575f, paint)

        // Two figures underneath, because one number alone reads as a score
        // and these two say what it bought.
        val seconds = ads * Leaderboard.SECONDS_PER_AD
        pair(
            canvas, paint, s,
            leftValue = formatDuration(seconds),
            leftLabel = ctx.getString(R.string.stat_time),
            rightValue = formatData(ads * 2.0),
            rightLabel = ctx.getString(R.string.card_data),
        )

        paint.typeface = Ui.REGULAR
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.031f
        paint.color = Ui.DIM
        canvas.drawText(ctx.getString(R.string.card_footer), s / 2f, s * 0.93f, paint)

        val file = File(ApkProvider.shareDir(ctx), "AdZero.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /**
     * La carte d'une partie : un jeu, une duree, un nombre.
     *
     * Le partage general dit "j'ai bloque 2 431 pubs", ce qui impressionne
     * sans rien vouloir dire. Celle-ci dit "23 pubs pendant mes 42 minutes sur
     * MyHotel" — un chiffre que la personne en face peut rapporter a sa propre
     * soiree, et qui nomme le jeu coupable.
     */
    fun renderSession(ctx: Context, label: String, ads: Int, minutes: Int): File {
        Ui.initFonts(ctx)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = SIZE.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, 0f, s, Ui.BG_TOP, Ui.BG_BOTTOM, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = RadialGradient(
            s / 2f, s * 0.3f, s * 0.55f,
            intArrayOf(0x33C9F73D, 0x00000000), null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = null

        drawMark(ctx, canvas, paint, s / 2f, s * 0.145f, s * 0.048f)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Ui.BOLD
        paint.color = Ui.TEXT
        paint.textSize = s * 0.052f
        canvas.drawText(label, s / 2f, s * 0.275f, paint)

        paint.textSize = s * 0.235f
        paint.color = Ui.LIME_A
        canvas.drawText(ads.toString(), s / 2f, s * 0.50f, paint)

        paint.typeface = Ui.REGULAR
        paint.textSize = s * 0.042f
        paint.color = Ui.GREY
        canvas.drawText(
            ctx.getString(R.string.card_session_ads), s / 2f, s * 0.555f, paint
        )

        pair(
            canvas, paint, s,
            leftValue = minutes.toString() + " min",
            leftLabel = ctx.getString(R.string.card_session_played),
            rightValue = formatDuration(ads * Leaderboard.SECONDS_PER_AD),
            rightLabel = ctx.getString(R.string.stat_time),
        )

        paint.typeface = Ui.REGULAR
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.031f
        paint.color = Ui.DIM
        canvas.drawText(ctx.getString(R.string.card_footer), s / 2f, s * 0.93f, paint)

        val file = File(ApkProvider.shareDir(ctx), "AdZero-session.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /**
     * The card that travels with the installer.
     *
     * An APK arrives in a messaging app as a document with a grey file icon and
     * a name — nothing that says what it is or why anyone would open it. This
     * goes alongside it and does that job: the mark, the promise, and the three
     * things somebody gets.
     *
     * The words are the welcome flow's own, deliberately. They were written for
     * exactly this moment — explaining the app to somebody who has never seen
     * it — and having two different pitches would only let them drift apart.
     */
    fun renderPromo(ctx: Context): File {
        Ui.initFonts(ctx)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = SIZE.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, 0f, s, Ui.BG_TOP, Ui.BG_BOTTOM, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = RadialGradient(
            s / 2f, s * 0.26f, s * 0.5f,
            intArrayOf(0x3DC9F73D, 0x00000000), null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.shader = null

        drawMark(ctx, canvas, paint, s / 2f, s * 0.2f, s * 0.075f)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Ui.BOLD
        paint.color = Ui.TEXT
        paint.textSize = s * 0.062f
        canvas.drawText("AdZero", s / 2f, s * 0.35f, paint)

        // The promise, wrapped by hand: a canvas has no idea where to break a
        // line, and the sentence is a different length in every language.
        paint.textSize = s * 0.072f
        paint.color = Ui.LIME_A
        var y = s * 0.47f
        for (line in wrap(ctx.getString(R.string.ob1_title), paint, s * 0.8f)) {
            canvas.drawText(line, s / 2f, y, paint)
            y += paint.textSize * 1.22f
        }

        paint.typeface = Ui.REGULAR
        paint.textSize = s * 0.04f
        paint.textAlign = Paint.Align.LEFT
        y = s * 0.68f
        val left = s * 0.16f
        for (res in listOf(R.string.ob1_p1, R.string.ob1_p2, R.string.ob1_p3)) {
            paint.color = Ui.LIME_B
            canvas.drawCircle(left, y - s * 0.013f, s * 0.008f, paint)
            paint.color = Ui.GREY
            canvas.drawText(ctx.getString(res), left + s * 0.035f, y, paint)
            y += s * 0.075f
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = s * 0.031f
        paint.color = Ui.DIM
        canvas.drawText(ctx.getString(R.string.card_footer), s / 2f, s * 0.93f, paint)

        val file = File(ApkProvider.shareDir(ctx), "AdZero-app.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** Greedy word wrap, which is all a single sentence needs. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = ArrayList<String>(2)
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) out.add(line.toString())
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    /**
     * The logo, at the size the caller was already asking for.
     *
     * This used to compose the mark by hand — a lime circle, then the
     * megaphone, then a diagonal over it — which was the only way to draw
     * something that existed as vector paths. The logo is now a single
     * rendered image with its own shading, so redrawing it here would mean
     * maintaining a second, worse copy of it that drifts every time Oscar
     * exports a new one.
     *
     * [r] stays what it always was, the ring's mid radius, so every call site
     * keeps its layout. In the artwork that ring sits at 1.17 r from the
     * centre and the file extends to 512/444 of it, which is where the box
     * below comes from.
     */
    private fun drawMark(
        ctx: Context,
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
    ) {
        val half = r * 1.17f * (512f / 444f)
        val logo = logoBitmap(ctx)
        if (logo != null) {
            canvas.drawBitmap(
                logo, null, RectF(cx - half, cy - half, cx + half, cy + half),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            return
        }
        // The image failing to decode must not cost the card its mark: a ring
        // and a bar are still recognisably AdZero.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.34f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Ui.LIME_A
        canvas.drawCircle(cx, cy, r, paint)
        val d = r * 0.62f
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * Decoded once and kept: both cards draw it, and a card can be rendered
     * repeatedly in a session.
     *
     * A drawable of its own, not R.mipmap.ic_launcher. That name resolves to
     * the adaptive icon — an XML document on Android 8 and up — which
     * BitmapFactory cannot decode at all: it returns null without throwing,
     * and the card silently fell back to the hand-drawn mark this was meant to
     * replace. It also carries the near-black plate, which a card does not
     * want.
     *
     * Filed under drawable-nodpi so decodeResource leaves it at its native
     * size. The card is 1080 px on every phone, so scaling the logo by the
     * screen's density would make it a different size on each one.
     */
    private var logo: Bitmap? = null

    private fun logoBitmap(ctx: Context): Bitmap? {
        logo?.let { return it }
        return try {
            BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_logo)
                .also { logo = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun pair(
        canvas: Canvas,
        paint: Paint,
        s: Float,
        leftValue: String,
        leftLabel: String,
        rightValue: String,
        rightLabel: String,
    ) {
        val boxTop = s * 0.655f
        val boxHeight = s * 0.145f
        val gap = s * 0.03f
        val margin = s * 0.11f
        val width = (s - margin * 2 - gap) / 2f

        for ((index, content) in listOf(leftValue to leftLabel, rightValue to rightLabel)
            .withIndex()) {
            val left = margin + index * (width + gap)
            val box = RectF(left, boxTop, left + width, boxTop + boxHeight)
            paint.color = Ui.PANEL_TOP
            canvas.drawRoundRect(box, s * 0.028f, s * 0.028f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.0015f
            paint.color = Ui.BORDER
            canvas.drawRoundRect(box, s * 0.028f, s * 0.028f, paint)
            paint.style = Paint.Style.FILL

            paint.typeface = Ui.BOLD
            paint.textSize = s * 0.058f
            paint.color = Ui.TEXT
            canvas.drawText(content.first, box.centerX(), boxTop + boxHeight * 0.52f, paint)
            paint.typeface = Ui.REGULAR
            paint.textSize = s * 0.028f
            paint.color = Ui.GREY
            canvas.drawText(
                content.second.lowercase(), box.centerX(), boxTop + boxHeight * 0.82f, paint
            )
        }
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60}"
    }

    private fun formatData(megabytes: Double): String =
        if (megabytes >= 1024) String.format("%.1f Go", megabytes / 1024)
        else String.format("%.0f Mo", megabytes)
}
