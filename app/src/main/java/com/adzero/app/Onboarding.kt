package com.adzero.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The welcome flow, shown once.
 *
 * Three pages, each with its own pictogram and three short lines. The middle
 * one is the only page that asks for anything, and it lists only what is
 * actually missing.
 */
@SuppressLint("ViewConstructor")
class Onboarding(
    private val activity: Activity,
    private val onFinished: () -> Unit,
) : LinearLayout(activity) {

    private var index = 0
    private var stripFilled = false

    private val blob = PowerButton(activity).apply { active = true }
    private val title = TextView(activity).apply {
        setTextColor(Ui.TEXT)
        textSize = 26f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER
    }
    private val bodyText = Ui.body(activity, "").apply { gravity = Gravity.CENTER }
    private val appStrip = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val bullets = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val permissionBox = LinearLayout(activity).apply { orientation = VERTICAL }
    private val dots = LinearLayout(activity).apply { gravity = Gravity.CENTER }
    private val primary = TextView(activity).apply {
        textSize = 16f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER
        setTextColor(Ui.BG_TOP)
    }
    private val skip = TextView(activity).apply {
        typeface = Ui.REGULAR
        text = activity.getString(R.string.ob_skip)
        setTextColor(Ui.DIM)
        textSize = 13f
        gravity = Gravity.CENTER
    }

    /** The block that fades in on each page change. */
    private val content = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun d(v: Int) = Ui.dp(activity, v)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = Ui.screenBackground()
        setPadding(d(28), d(16), d(28), d(20))
        isClickable = true   // swallow taps meant for the screen underneath

        addView(skip, LayoutParams(MATCH_PARENT, WRAP_CONTENT))


        content.addView(blob, LayoutParams(MATCH_PARENT, d(190)))
        content.addView(title, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(Ui.spacer(activity, 12))
        content.addView(bodyText, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(Ui.spacer(activity, 22))
        // Height reserved from the start: the row used to pop in and shove the
        // rest of the page down when the icons finally arrived.
        content.addView(appStrip, LayoutParams(MATCH_PARENT, d(46)))
        content.addView(Ui.spacer(activity, 22))
        content.addView(bullets, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(permissionBox, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        // Everything between the skip link and the dots scrolls, and is
        // centred when it fits. It used to be a fixed block wedged between two
        // stretching spacers, which was fine until the permissions page grew:
        // with five cards the content ran past the bottom of the screen,
        // taking the dots and the Continue button with it and leaving no way
        // to reach them.
        content.gravity = Gravity.CENTER_VERTICAL
        addView(
            android.widget.ScrollView(activity).apply {
                isFillViewport = true
                overScrollMode = OVER_SCROLL_NEVER
                // The white track down the edge belongs to a document, not to
                // a welcome screen. The page still scrolls; it just stops
                // advertising that it does.
                isVerticalScrollBarEnabled = false
                addView(content, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            },
            LayoutParams(MATCH_PARENT, 0, 1f)
        )

        addView(dots, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(Ui.spacer(activity, 18))
        addView(primary, LayoutParams(MATCH_PARENT, d(56)))

        skip.setOnClickListener { buzz(false); finish() }
        // advance() already chooses its own haptic — a soft tick between
        // pages, a firmer one on the last. Adding another here doubled it.
        primary.setOnClickListener { advance() }
        blob.onToggle = { advance() }

        paint(animated = false)
    }

    private fun advance() {
        if (index < 2) {
            index++
            buzz(false)
            paint(animated = true)
        } else {
            // No haptic here: this hands over to the activity, which switches
            // protection on and buzzes for it. Two reasons, one press — the
            // same doubling as the badge and the settings rows.
            finish()
            (activity as? MainActivity)?.startFromOnboarding()
        }
    }

    /** A soft tick between pages; the firmer one belongs to the activity. */
    private fun buzz(strong: Boolean) {
        val v = activity.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        // Same reasoning as in the activity: the old light pulse was too faint
        // to feel, and the phone's own tuned effects are better than anything
        // guessed at here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(
                VibrationEffect.createPredefined(
                    if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect =
                if (strong) VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 50, 32), intArrayOf(0, 200, 0, 255), -1
                )
                else VibrationEffect.createOneShot(18, 180)
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION") v.vibrate(if (strong) 40 else 18)
        }
    }

    private fun finish() {
        Stats.markOnboarded()
        onFinished()
    }

    fun refresh() {
        if (index == 1) paint(animated = false)
    }

    private fun paint(animated: Boolean) {
        title.text = activity.getString(
            when (index) {
                0 -> R.string.ob1_title
                1 -> R.string.ob2_title
                else -> R.string.ob3_title
            }
        )
        bodyText.text = activity.getString(
            when (index) {
                0 -> R.string.ob1_body
                1 -> R.string.ob2_body
                else -> R.string.ob3_body
            }
        )
        primary.text = activity.getString(if (index == 2) R.string.ob_start else R.string.ob_next)
        primary.background = Ui.gradientPill(activity, Ui.LIME_A, Ui.LIME_B)
        skip.visibility = if (index == 2) View.INVISIBLE else View.VISIBLE

        paintAppStrip()
        paintBullets()
        paintDots()
        paintPermissions()

        if (animated) {
            content.alpha = 0f
            content.translationY = d(20).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(280).start()
        }
    }


    /**
     * Real icons from this phone, on the first page only.
     *
     * Naming brands would be a rights problem and a lie by implication;
     * showing the apps that are actually installed is neither, and it is more
     * convincing — you recognise your own games.
     */
    private fun paintAppStrip() {
        if (index != 0) {
            appStrip.visibility = View.GONE
            return
        }
        appStrip.visibility = View.VISIBLE
        if (stripFilled) return
        stripFilled = true

        AppsCatalog.quickGames(activity, 6) { entries ->
            if (index != 0) return@quickGames
            appStrip.removeAllViews()
            for ((i, entry) in entries.withIndex()) {
                val icon = ImageView(activity).apply {
                    setImageDrawable(entry.icon)
                    Ui.circleClip(this)
                    layoutParams = LayoutParams(d(38), d(38))
                        .apply { marginStart = d(5); marginEnd = d(5) }
                    // Staggered fade so the row assembles instead of snapping
                    // into place all at once.
                    alpha = 0f
                    scaleX = 0.7f
                    scaleY = 0.7f
                }
                appStrip.addView(icon)
                icon.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(60L * i)
                    .setDuration(260)
                    .start()
            }
        }
    }

    /** Three short facts per page — easier to read than a paragraph. */
    private fun paintBullets() {
        bullets.removeAllViews()
        // Each line gets the icon of the thing it is about. A row of
        // identical dots carried no information; these say something before
        // the sentence is read.
        val lines = when (index) {
            0 -> listOf(
                R.string.ob1_p1 to R.drawable.ic_no_video,
                R.string.ob1_p2 to R.drawable.ic_no_banner,
                R.string.ob1_p3 to R.drawable.ic_wallet,
            )
            1 -> emptyList()
            else -> listOf(
                R.string.ob3_p1 to R.drawable.ic_tab_stats,
                R.string.ob3_p2 to R.drawable.ic_shield_check,
                R.string.ob3_p3 to R.drawable.ic_phone,
            )
        }
        for ((res, icon) in lines) {
            val row = LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(6), 0, d(6))
            }
            row.addView(android.widget.ImageView(activity).apply {
                setImageResource(icon)
                setColorFilter(Ui.LIME_A)
                layoutParams = LayoutParams(d(20), d(20)).apply { marginEnd = d(14) }
            })
            row.addView(TextView(activity).apply {
                typeface = Ui.REGULAR
                text = activity.getString(res)
                setTextColor(Ui.TEXT)
                textSize = 14f
            })
            bullets.addView(row)
        }
    }

    private fun paintDots() {
        dots.removeAllViews()
        for (i in 0..2) {
            dots.addView(View(activity).apply {
                background = Ui.gradientPill(
                    activity,
                    if (i == index) Ui.LIME_A else Ui.BORDER,
                    if (i == index) Ui.LIME_B else Ui.BORDER
                )
                layoutParams = LayoutParams(if (i == index) d(24) else d(7), d(7))
                    .apply { marginStart = d(4); marginEnd = d(4) }
            })
        }
    }

    private fun paintPermissions() {
        permissionBox.removeAllViews()
        if (index != 1) return

        val missing = Permissions.missing(activity)
        if (missing.isEmpty()) {
            permissionBox.addView(TextView(activity).apply {
                text = activity.getString(R.string.ob2_ready)
                setTextColor(Ui.LIME_A)
                textSize = 15f
                typeface = Ui.BOLD
                gravity = Gravity.CENTER
            })
            return
        }

        for (item in missing) {
            val card = Ui.card(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val block = LinearLayout(activity).apply { orientation = VERTICAL }
            block.addView(TextView(activity).apply {
                text = activity.getString(Permissions.titleOf(item))
                setTextColor(Ui.TEXT)
                textSize = 14f
                typeface = Ui.BOLD
            })
            block.addView(TextView(activity).apply {
                typeface = Ui.REGULAR
                text = activity.getString(Permissions.detailOf(item))
                setTextColor(Ui.GREY)
                textSize = 11f
            })
            if (Permissions.restricted(item)) {
                block.addView(TextView(activity).apply {
                    typeface = Ui.REGULAR
                    text = activity.getString(R.string.setup_restricted)
                    setTextColor(Ui.LIME_A)
                    textSize = 11f
                    setPadding(0, d(6), 0, 0)
                })
            }
            card.addView(block, LayoutParams(0, WRAP_CONTENT, 1f))
            card.addView(TextView(activity).apply {
                (layoutParams as? MarginLayoutParams)?.marginStart = d(16)
                text = activity.getString(R.string.setup_grant)
                setTextColor(Ui.BG_TOP)
                textSize = 11f
                typeface = Ui.BOLD
                setPadding(d(16), d(9), d(16), d(9))
                background = Ui.gradientPill(activity, Ui.LIME_A, Ui.LIME_B)
                setOnClickListener { buzz(false); ask(item) }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(16) })
            permissionBox.addView(
                card,
                LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = d(8) }
            )
        }
    }

    private fun ask(item: Permissions.Item) {
        // Notifications are a runtime permission: a system dialog, not a
        // settings page. Nothing to launch, so it is handled before the
        // intent lookup rather than falling through it.
        if (item == Permissions.Item.NOTICES) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2
                )
            }
            return
        }
        val intent: Intent = Permissions.intentFor(activity, item) ?: return
        if (item == Permissions.Item.VPN) activity.startActivityForResult(intent, 1)
        else activity.startActivity(intent)
    }
}
