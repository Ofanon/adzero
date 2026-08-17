package com.adzero.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Two screens, one job each.
 *
 * Home is the power button and three numbers — nothing to read, nothing to
 * decide. Stats is where the detail lives, for whoever wants it. Splitting
 * them is what keeps the first screen calm.
 */
class MainActivity : Activity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }


    private lateinit var root: LinearLayout
    private lateinit var aurora: AuroraView
    private lateinit var stack: FrameLayout
    private var onboarding: Onboarding? = null
    private lateinit var pages: LinearLayout
    private lateinit var homePage: ScrollView
    private lateinit var statsPage: ScrollView

    private lateinit var power: PowerButton
    private lateinit var stateLabel: TextView
    private lateinit var timer: TextView
    private lateinit var hint: TextView
    private lateinit var adsValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var bannerButton: TextView
    private lateinit var bubbleButton: TextView
    private lateinit var warningCard: LinearLayout
    private lateinit var warningText: TextView

    private lateinit var leaderboardList: LinearLayout
    private lateinit var statsEmpty: LinearLayout
    private lateinit var statsContent: LinearLayout
    private lateinit var shieldList: LinearLayout
    private lateinit var candidateList: LinearLayout
    private lateinit var candidateHint: TextView
    private lateinit var customList: LinearLayout
    private lateinit var domainList: LinearLayout
    private lateinit var advancedToggle: TextView
    private lateinit var advancedBox: LinearLayout
    private lateinit var languageRow: TextView
    private lateinit var shareRow: TextView
    private lateinit var tourRow: TextView
    private lateinit var promoRow: TextView
    private lateinit var settingsPage: ScrollView
    private lateinit var setupDone: TextView
    private var advancedOpen = false
    private var appsBatch = 0

    private val BROWSERS = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser",
        "com.opera.browser", "com.opera.mini.native", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser",
        "org.chromium.chrome", "com.kiwibrowser.browser", "com.vivaldi.browser",
    )

    private lateinit var appsPage: ScrollView
    private lateinit var appsList: LinearLayout
    private lateinit var appFilterChips: List<TextView>
    private lateinit var appsHeader: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var setupList: LinearLayout
    private lateinit var searchField: EditText
    private var expandedApp: String? = null
    private var expandedCard: LinearLayout? = null

    /**
     * Demande de defiler jusqu'a l'app qu'on vient d'ouvrir depuis l'accueil.
     *
     * Consommee a la premiere repeinte : ouvrir un panneau qu'il faut ensuite
     * aller chercher plus bas n'est pas l'avoir ouvert.
     */
    private var scrollToExpanded = false

    /**
     * What each section of the statistics last drew.
     *
     * The page is asked to repaint on every tick of the counter, which is once
     * a second, and almost every one of those repaints would produce exactly
     * what is already on screen. Comparing first costs a string; not comparing
     * cost a full teardown of every row, and — once the sections were animated
     * — a visible blink each time.
     */
    private val drawn = HashMap<String, String>()

    /**
     * Suspends a container's animations for the frame about to be drawn.
     *
     * Restored on the next frame rather than at the end of the caller: views
     * are added now but laid out in the pass after, and putting the transition
     * back any earlier lets it catch that pass — which is the whole thing being
     * avoided.
     *
     * Does nothing if already suspended, so two calls in one frame cannot leave
     * a container with its animations permanently off.
     */
    private fun pauseAnimation(container: LinearLayout) {
        val saved = container.layoutTransition ?: return
        container.layoutTransition = null
        container.post { container.layoutTransition = saved }
    }

    private fun unchanged(section: String, signature: String): Boolean {
        if (drawn[section] == signature) return true
        drawn[section] = signature
        return false
    }
    private lateinit var bestLine: TextView
    private lateinit var dataLine: TextView
    private lateinit var moneyLine: TextView
    private lateinit var shareStatsRow: TextView
    private lateinit var troubleCard: LinearLayout
    private lateinit var reportRow: TextView
    private lateinit var sessionRow: TextView
    private lateinit var remoteRow: TextView
    private lateinit var newsBar: LinearLayout
    private lateinit var newsIcon: ImageView
    private lateinit var newsTitle: TextView
    private lateinit var newsBody: TextView
    private lateinit var newsClose: ImageView
    private lateinit var shieldRow: TextView
    private lateinit var shapeRow: TextView
    private lateinit var trackerValue: TextView
    private lateinit var historyView: HistoryView

    private lateinit var gear: ImageView
    private var tabs: List<LinearLayout> = emptyList()
    private lateinit var statsRow: LinearLayout
    private lateinit var homeStrip: LinearLayout
    private lateinit var homeStripLabel: TextView
    private var page = 0   // 0 = home, 1 = stats, 2 = apps

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            paint()
            ticker.postDelayed(this, 500)
        }
    }

    private fun d(v: Int) = Ui.dp(this, v)

    companion object {
        /** Sent by the notification action while a game is in the foreground. */
        const val ACTION_SESSION_CARD = "com.adzero.app.SESSION_CARD"
        const val EXTRA_SESSION_APP = "session_app"
        const val EXTRA_SESSION_ADS = "session_ads"
        const val EXTRA_SESSION_MINUTES = "session_minutes"

        const val ACTION_REPORT = "com.adzero.app.REPORT"

        /** Survives the recreation that a language change causes. */
        private const val KEY_PAGE = "page"
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        Ui.initFonts(this)
        Stats.init(this)
        AdNetworks.init(this)
        Remote.init(this)
        Learning.init(this)
        Leaderboard.init(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(28), d(20), d(12))
        }

        root.addView(buildHeader(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        pages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homePage = buildHome()
        statsPage = buildStats()
        appsPage = buildApps()
        settingsPage = buildSettings()
        pages.addView(homePage, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        pages.addView(statsPage, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        pages.addView(appsPage, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        pages.addView(settingsPage, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(pages, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        root.addView(buildTabs(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // The aurora sits behind everything; the column is transparent so the
        // blobs drift under the content instead of next to it.
        aurora = AuroraView(this)
        stack = FrameLayout(this).apply {
            background = Ui.screenBackground()
            addView(
                aurora,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        attachSwipe()
        setContentView(stack)

        // Android draws apps edge to edge now: without this the content sits
        // under the status bar at the top and the gesture bar at the bottom.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stack.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }

        // Android recreates the activity when the app's language changes, and
        // landing back on the home page made choosing a language feel like
        // being thrown out of the settings.
        showPage(saved?.getInt(KEY_PAGE, 0) ?: 0)
        if (!Stats.onboarded) showOnboarding()
        checkPrivateDns()

        // Cold start straight from the notification action. Posted rather than
        // called: the views have no size yet, and the sheet measures itself.
        if (intent?.action == ACTION_REPORT && Stats.onboarded) root.post { showReport() }
        if (intent?.action == ACTION_SESSION_CARD) root.post { shareSession(intent) }
    }

    // ------------------------------------------------------------------ build

    private fun buildHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // The mark alone, with nothing behind it.
        //
        // It used to be the whole launcher icon, which carries its own
        // near-black plate — necessary on a home screen full of wallpapers,
        // pointless here where the app's background is already that colour,
        // and it read as a dark square around the logo.
        //
        // ic_logo, and not R.mipmap.ic_launcher: that name resolves to the
        // adaptive icon, which brings its own background layer along and put
        // the dark square straight back. Not the adaptive foreground either —
        // it carries the margin that stops a round mask biting into the ring,
        // which is why this used to be scaled up by 1.5 to cancel it out.
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            layoutParams = LinearLayout.LayoutParams(d(30), d(30))
                .apply { marginEnd = d(12) }
        })
        header.addView(
            Ui.title(this, getString(R.string.app_name)),
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        stateLabel = TextView(this).apply {
            // Never wraps either, and never gives up its own width: the title
            // beside it is the one that yields when the header is tight.
            isSingleLine = true
            textSize = 11f
            typeface = Ui.MEDIUM
            letterSpacing = 0.12f
            setPadding(d(14), d(7), d(14), d(7))
            // The badge is where the eye goes to ask "am I protected?", so it
            // is where the answer should be checkable. The button at the bottom
            // of the page stays for anyone who never thinks to press a label.
            isClickable = true
            setOnClickListener { buzz(); runSelfTest() }
        }
        stateLabel.gravity = Gravity.CENTER_VERTICAL
        stateLabel.setPadding(d(12), d(7), d(14), d(7))
        header.addView(stateLabel)

        gear = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            setPadding(d(10), d(10), d(10), d(10))
            setOnClickListener {
                tick()
                // Settings arrive from the right and leave to the left, so the
                // gear feels like a place you go to and come back from.
                val goingBack = page == 3
                showPage(if (goingBack) 0 else 3, slideFrom = if (goingBack) -1 else 1)
            }
        }
        header.addView(gear, LinearLayout.LayoutParams(d(44), d(44)).apply { marginStart = d(6) })
        return header
    }


    /**
     * "An ad got through" — the user reports what the app could not predict.
     *
     * Everything else in AdZero decides in advance. This is the one path that
     * runs backwards, from an ad the person actually saw to the domain that
     * served it, and it exists because the ads that still get through come
     * from domains too short-lived for any list to catch. The user supplies
     * the one fact no heuristic has: that an ad just appeared.
     */
    private fun showReport() {
        val app = Recent.busiestApp()

        // Some ads were never reachable. Offering a list of servers to block
        // after one of those lets somebody conclude the app is broken, when
        // the honest answer is that this kind cannot be blocked at all.
        FirstParty.kindOf(app ?: "")?.let { kind ->
            explainFirstParty(app!!, kind)
            return
        }

        val suspects = Recent.suspects(app)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.report_title)
            setTextColor(Ui.TEXT)
            textSize = 20f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 10))

        if (suspects.isEmpty()) {
            box.addView(Ui.body(this, getString(R.string.report_empty)))
            sheet(box)
            return
        }

        box.addView(Ui.body(this, getString(R.string.report_body, appLabel(app ?: ""))))
        box.addView(Ui.spacer(this, 16))

        for ((rank, suspect) in suspects.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(8), 0, d(8))
            }
            val label = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setOnClickListener { buzz(); explainHost(suspect.domain) }
            }
            // The verdict leads, the reason follows, and the hostname comes
            // last in small type. Nobody can judge "unity3d.com"; everybody can
            // judge "seen next to an ad in two of your games".
            val card = Explain.cardFor(suspect.domain)
            val safe = card.kind == Explain.Kind.ENGINE
            label.addView(TextView(this).apply {
                text = getString(
                    when {
                        safe -> R.string.verdict_known_safe
                        suspect.confident -> R.string.verdict_high
                        suspect.plausible -> R.string.verdict_medium
                        else -> R.string.verdict_low
                    }
                )
                setTextColor(
                    when {
                        safe -> Ui.RED_A
                        suspect.confident -> Ui.LIME_A
                        suspect.plausible -> Explain.levelColour(Explain.Level.ADS)
                        else -> Ui.GREY
                    }
                )
                textSize = 15f
                typeface = Ui.BOLD
            })
            for (reason in suspect.reasons.take(2)) {
                label.addView(TextView(this).apply {
                    typeface = Ui.REGULAR
                    text = "· " + getString(reason.text, reason.arg)
                    setTextColor(Ui.GREY)
                    textSize = 12f
                })
            }
            label.addView(TextView(this).apply {
                text = suspect.domain
                setTextColor(Ui.DIM)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            })
            row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            val button = TextView(this).apply {
                text = getString(R.string.report_block)
                setTextColor(Ui.BG_TOP)
                textSize = 11f
                typeface = Ui.BOLD
                setPadding(d(14), d(8), d(14), d(8))
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            }
            button.setOnClickListener {
                buzz()
                AdNetworks.add(suspect.domain)
                button.text = getString(R.string.report_blocked)
                button.setTextColor(Ui.DIM)
                button.background = Ui.softPill(this@MainActivity)
                button.isClickable = false
                paintCustom()
            }
            row.addView(button)
            box.addView(row)
        }
        sheet(box)
    }


    /**
     * "This game stopped working" — the way out when AdZero is the problem.
     *
     * A blocker that can break an app and offers no remedy gets uninstalled the
     * first time it does. Until now the only fix was buried in the technical
     * details, behind hostnames, and it could not even undo the built-in list.
     *
     * Two remedies, in order of precision: release the domains silenced for
     * this app in the last few minutes, or stop protecting the app entirely.
     * The second always works, which is why it is always offered — a person
     * whose game is broken needs a guaranteed way out, not a good guess.
     */
    /**
     * Runs the checks and shows what came back.
     *
     * Deliberately the only screen in AdZero that can say "this is not
     * working". Everything else reports success; something has to be able to
     * report failure, or the app is only ever reassuring.
     */
    /**
     * Sends AdZero itself to somebody else.
     *
     * The app will never be on the Play Store, so it travels hand to hand.
     * Until now that meant finding the installer on a computer every time;
     * this lets anyone who already has it pass it on from their phone.
     *
     * Copied out of the install directory first: the file there is called
     * base.apk, which arrives looking like nothing anybody would open.
     */
    private fun toast(message: String) =
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()

    /**
     * Renders the statistics card and hands it to the share sheet.
     *
     * Drawing a 1080-pixel bitmap is not main-thread work, and the card is the
     * one thing in this app somebody is about to send to a friend — a dropped
     * frame here is worse than anywhere else.
     */
    /** La carte d'une partie, fabriquee a la demande depuis la notification. */
    private fun shareSession(from: Intent) {
        val pkg = from.getStringExtra(EXTRA_SESSION_APP) ?: return
        val ads = from.getIntExtra(EXTRA_SESSION_ADS, 0)
        val minutes = from.getIntExtra(EXTRA_SESSION_MINUTES, 0)
        if (ads <= 0) return
        val label = appLabel(pkg)
        Thread({
            val file = try {
                Card.renderSession(this, label, ads, minutes)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || file == null) return@runOnUiThread
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, ApkProvider.uriFor(file))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.card_caption))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.card_share)))
            }
        }, "session-card").start()
    }

    /**
     * La celebration d'un palier.
     *
     * Retardee de six dixiemes de seconde apres l'ouverture : arriver dans le
     * meme instant que la page donne l'impression d'un ecran qui a mal charge,
     * alors qu'une demi-seconde plus tard, c'est une bonne nouvelle.
     */
    private fun celebrate() {
        val step = Milestones.take()
        if (step <= 0 || isFinishing || isDestroyed) return
        buzz()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(d(24), d(28), d(24), d(10))
        }
        box.addView(TextView(this).apply {
            text = step.toString()
            setTextColor(Ui.LIME_A)
            textSize = 56f
            typeface = Ui.BOLD
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.milestone_title)
            setTextColor(Ui.TEXT)
            textSize = 19f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(6), 0, 0)
        })
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(
            R.string.milestone_body,
            formatDuration(step * Leaderboard.SECONDS_PER_AD)
        )).apply { gravity = Gravity.CENTER })

        box.addView(Ui.spacer(this, 20))
        box.addView(TextView(this).apply {
            text = getString(R.string.milestone_share)
            setTextColor(Ui.BG_TOP)
            textSize = 13f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(13), 0, d(13))
            background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            setOnClickListener { buzz(); currentSheet?.dismiss(); shareCard() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        sheet(box)
    }

    private fun shareCard() {
        Thread({
            val file = try {
                Card.render(this)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file == null) {
                    toast(getString(R.string.share_failed))
                    return@runOnUiThread
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, ApkProvider.uriFor(file))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.card_caption))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.card_share)))
            }
        }, "stats-card").start()
    }

    /**
     * Sends the presentation card on its own, as an ordinary image.
     *
     * Its own action rather than an attachment on the installer: a share sheet
     * carries one kind of file reliably, and the two are sent one after the
     * other in the same conversation anyway.
     */
    private fun sharePromo() {
        Thread({
            val file = try {
                Card.renderPromo(this)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file == null) {
                    toast(getString(R.string.share_failed))
                    return@runOnUiThread
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, ApkProvider.uriFor(file))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.card_caption))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.promo_share)))
            }
        }, "promo-card").start()
    }

    private fun shareApp() {
        // No haptic: its only caller is a settings row, and those already tick
        // on press. Fifth time this pair has doubled up — a button vibrating
        // because it was pressed, an action vibrating because it ran.
        Thread({
            val copy = try {
                val source = java.io.File(applicationInfo.sourceDir)
                java.io.File(ApkProvider.shareDir(this), "AdZero.apk").also { out ->
                    source.inputStream().use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (copy == null) {
                    toast(getString(R.string.share_failed))
                    return@runOnUiThread
                }
                // One attachment, declared as a package archive.
                //
                // Sending the installer together with a picture meant a
                // mixed-type multiple share, and WhatsApp keeps only one of the
                // two — so the thing that actually matters stopped arriving.
                // Bundling was a nicety; the APK getting through is the point.
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = ApkProvider.APK_MIME
                    putExtra(Intent.EXTRA_STREAM, ApkProvider.uriFor(copy))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                    putExtra(Intent.EXTRA_TITLE, getString(R.string.share_subject))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.share_action)))
            }
        }, "share-apk").start()
    }

    /**
     * The three things to do when something is not right.
     *
     * Grouped rather than removed: the report is also in the notification,
     * where it belongs, but the other two have nowhere else to live and
     * somebody whose game just broke should not have to hunt through settings.
     */
    private fun runSelfTest() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.test_title)
            setTextColor(Ui.TEXT)
            textSize = 20f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 12))

        // The checks take four seconds against a real network, and four
        // seconds of a static sentence reads as an app that did nothing.
        val startedAt = System.currentTimeMillis()
        val waiting = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        waiting.addView(ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.LIME_A)
            layoutParams = LinearLayout.LayoutParams(d(22), d(22))
                .apply { marginEnd = d(14) }
        })
        waiting.addView(Ui.body(this, getString(R.string.test_running)))
        box.addView(waiting)
        sheet(box)

        SelfTest.run(this) { checks ->
            if (isFinishing || isDestroyed) return@run
            box.removeView(waiting)
            for (check in checks) {
                box.addView(Ui.spacer(this, 12))
                box.addView(TextView(this).apply {
                    val mark = when {
                        check.info -> "ℹ  "
                        check.ok -> "✓  "
                        else -> "✗  "
                    }
                    text = mark + getString(check.title)
                    setTextColor(
                        when {
                            check.info -> Ui.GREY
                            check.ok -> Ui.LIME_A
                            else -> Ui.RED_A
                        }
                    )
                    textSize = 15f
                    typeface = Ui.BOLD
                })
                box.addView(Ui.body(
                    this,
                    if (check.arg == null) getString(check.detail)
                    else getString(check.detail, check.arg)
                ).apply { textSize = 13f })
            }
            // The press already buzzed. A second one is worth having when the
            // checks took four seconds against the network and the phone has
            // been sitting there; when protection is off they finish in two
            // hundred milliseconds, and the two land as one double tap.
            if (System.currentTimeMillis() - startedAt > 900) buzz()
        }
    }

    /** Says plainly that this app's ads are out of reach, and why. */
    private fun explainFirstParty(pkg: String, kind: FirstParty.Kind) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(
                if (kind == FirstParty.Kind.MIXED) R.string.firstparty_title_partly
                else R.string.firstparty_title
            )
            typeface = Ui.BOLD
            setTextColor(Ui.TEXT)
            textSize = 20f
        })
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(
            when (kind) {
                FirstParty.Kind.VIDEO -> R.string.firstparty_video
                FirstParty.Kind.FEED -> R.string.firstparty_feed
                FirstParty.Kind.MIXED -> R.string.firstparty_mixed
            },
            appLabel(pkg)
        )))
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(R.string.firstparty_why)))
        sheet(box)
    }

    private fun showBroken() {
        val apps = (Recent.silencedApps() + Leaderboard.ranking(6).map { it.app })
            .distinct()
            .take(6)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.broken_title)
            setTextColor(Ui.TEXT)
            textSize = 20f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 10))

        if (apps.isEmpty()) {
            box.addView(Ui.body(this, getString(R.string.broken_empty)))
            sheet(box)
            return
        }

        box.addView(Ui.body(this, getString(R.string.broken_pick)))
        box.addView(Ui.spacer(this, 14))

        for (pkg in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(9), 0, d(9))
                isClickable = true
            }
            AppsCatalog.iconFor(this, pkg)?.let { icon ->
                row.addView(ImageView(this).apply {
                    setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
                    layoutParams = LinearLayout.LayoutParams(d(30), d(30))
                        .apply { marginEnd = d(12) }
                    Ui.circleClip(this)
                })
            }
            row.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = appLabel(pkg)
                setTextColor(Ui.TEXT)
                textSize = 15f
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.setOnClickListener { buzz(); currentSheet?.dismiss(); showFixes(pkg) }
            box.addView(row)
        }
        sheet(box)
    }

    /**
     * The guided version of "which one broke my game".
     *
     * It used to list every silenced server with a Release button next to each,
     * which asks the user a question only the app can answer. Now the app makes
     * the guess, releases one server, and asks the only question the user can
     * answer better than it can: does the game work now.
     */
    private fun showFixes(pkg: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = appLabel(pkg)
            setTextColor(Ui.TEXT)
            textSize = 20f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 12))

        val session = Repair.sessionFor(pkg)
        if (session?.current != null) {
            paintRepairStep(box, pkg, session)
        } else {
            paintRepairStart(box, pkg)
        }

        // The guaranteed way out, whatever the diagnosis is doing.
        box.addView(Ui.spacer(this, 18))
        box.addView(TextView(this).apply {
            text = getString(
                if (AppFilter.isExcluded(pkg)) R.string.broken_already_off
                else R.string.broken_stop_protecting
            )
            setTextColor(if (AppFilter.isExcluded(pkg)) Ui.DIM else Ui.RED_A)
            textSize = 14f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(12), 0, d(12))
            background = Ui.softPill(this@MainActivity)
            isClickable = !AppFilter.isExcluded(pkg)
            setOnClickListener {
                buzz()
                Repair.abandon()
                AppFilter.toggle(pkg)
                currentSheet?.dismiss()
                paintApps()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        sheet(box)
    }

    private fun paintRepairStart(box: LinearLayout, pkg: String) {
        val domains = Recent.silencedFor(pkg, max = 12)
        if (domains.isEmpty()) {
            box.addView(Ui.body(this, getString(R.string.broken_none_recent)))
            return
        }
        box.addView(Ui.body(this, getString(R.string.repair_intro, domains.size)))
        box.addView(Ui.spacer(this, 16))
        box.addView(TextView(this).apply {
            // For somebody who already knows which one it is, or who does not
            // want to reopen the game six times. The guided path is the default
            // because most people cannot tell; it should not be the only one.
            typeface = Ui.BOLD
            text = getString(R.string.repair_manual)
            setTextColor(Ui.GREY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, d(12), 0, d(12))
            isClickable = true
            setOnClickListener {
                buzz()
                currentSheet?.dismiss()
                showManualFixes(pkg, domains)
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        box.addView(TextView(this).apply {
            text = getString(R.string.repair_start)
            setTextColor(Ui.BG_TOP)
            textSize = 14f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(14), 0, d(14))
            background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            setOnClickListener {
                buzz()
                Repair.start(pkg, domains)
                currentSheet?.dismiss()
                showFixes(pkg)
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** The whole ranked list at once, with a button on each. */
    private fun showManualFixes(pkg: String, domains: List<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = appLabel(pkg)
            typeface = Ui.BOLD
            setTextColor(Ui.TEXT)
            textSize = 20f
        })
        box.addView(Ui.spacer(this, 10))
        box.addView(Ui.body(this, getString(R.string.repair_manual_body)))
        box.addView(Ui.spacer(this, 14))

        // Ranked the same way the guided path ranks: most likely needed first,
        // so the order still carries the app's opinion even when the choice is
        // handed over.
        for (domain in Repair.rank(pkg, domains)) {
            val who = Explain.cardFor(domain)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(8), 0, d(8))
            }
            val label = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setOnClickListener { buzz(); explainHost(domain) }
            }
            label.addView(TextView(this).apply {
                text = if (who.owner.isNotEmpty()) who.owner
                else Explain.kindLabel(this@MainActivity, who.kind)
                typeface = Ui.BOLD
                setTextColor(Ui.TEXT)
                textSize = 14f
            })
            label.addView(TextView(this).apply {
                typeface = Typeface.MONOSPACE
                text = domain
                setTextColor(Ui.DIM)
                textSize = 11f
            })
            row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            val button = TextView(this).apply {
                text = getString(R.string.broken_unblock)
                setTextColor(Ui.BG_TOP)
                typeface = Ui.BOLD
                textSize = 11f
                setPadding(d(14), d(8), d(14), d(8))
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            }
            button.setOnClickListener {
                buzz()
                AdNetworks.allow(domain)
                button.text = getString(R.string.broken_unblocked)
                button.setTextColor(Ui.DIM)
                button.background = Ui.softPill(this@MainActivity)
                button.isClickable = false
                paintCustom()
            }
            row.addView(button)
            box.addView(row)
        }
        sheet(box)
    }

    private fun paintRepairStep(box: LinearLayout, pkg: String, session: Repair.Session) {
        val domain = session.current ?: return
        val who = Explain.cardFor(domain)

        box.addView(TextView(this).apply {
            text = getString(R.string.repair_testing, session.index + 1, session.candidates.size)
            setTextColor(Ui.DIM)
            textSize = 11f
            typeface = Ui.BOLD
            letterSpacing = 0.14f
        })
        box.addView(Ui.spacer(this, 8))
        box.addView(TextView(this).apply {
            text = if (who.owner.isNotEmpty()) who.owner
            else Explain.kindLabel(this@MainActivity, who.kind)
            setTextColor(Ui.LIME_A)
            textSize = 17f
            typeface = Ui.BOLD
        })
        box.addView(TextView(this).apply {
            typeface = Typeface.MONOSPACE
            text = domain
            setTextColor(Ui.DIM)
            textSize = 11f
            isClickable = true
            setOnClickListener { buzz(); explainHost(domain) }
        })
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(R.string.repair_testing_body)))
        box.addView(Ui.spacer(this, 16))

        val answers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        answers.addView(TextView(this).apply {
            text = getString(R.string.repair_worked)
            setTextColor(Ui.BG_TOP)
            textSize = 13f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(d(10), d(13), d(10), d(13))
            background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            setOnClickListener {
                buzz()
                Repair.worked()
                currentSheet?.dismiss()
                toast(getString(R.string.repair_done, domain))
                paintCustom()
            }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        answers.addView(TextView(this).apply {
            text = getString(R.string.repair_still)
            setTextColor(Ui.TEXT)
            textSize = 13f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(d(10), d(13), d(10), d(13))
            background = Ui.softPill(this@MainActivity)
            setOnClickListener {
                buzz()
                val next = Repair.stillBroken()
                currentSheet?.dismiss()
                if (next == null) toast(getString(R.string.repair_exhausted))
                else showFixes(pkg)
            }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = d(10) })
        box.addView(answers, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** La cle de la nouvelle affichee, ou null quand il n'y en a pas. */
    private fun newsKey(): String? {
        Remote.available()?.let { return "v" + it.name }
        if (Remote.added() > 0) return "list" + AdNetworks.remoteCount()
        return null
    }

    private fun paintNews() {
        val key = newsKey()
        if (key == null || Stats.newsWasSeen(key)) {
            newsBar.visibility = View.GONE
            return
        }
        val update = Remote.available()

        // Deux poids : une nouvelle version merite d'interrompre le regard,
        // un serveur de plus dans la liste ne le merite pas. Une app qui crie
        // pour tout n'est plus entendue quand elle a quelque chose a dire.
        if (update != null) {
            newsBar.background = Ui.gradientPill(this, Ui.LIME_A, Ui.LIME_B)
            newsIcon.setImageResource(R.drawable.ic_bell)
            newsIcon.imageTintList = ColorStateList.valueOf(Ui.BG_TOP)
            newsClose.imageTintList = ColorStateList.valueOf(Ui.BG_TOP)
            newsTitle.setTextColor(Ui.BG_TOP)
            newsBody.setTextColor(0x99000000.toInt())
            newsTitle.text = getString(R.string.news_update, update.name)
            newsBody.text = getString(R.string.news_update_body)
        } else {
            newsBar.background = Ui.softPill(this)
            newsIcon.setImageResource(R.drawable.ic_globe)
            newsIcon.imageTintList = ColorStateList.valueOf(Ui.LIME_A)
            newsClose.imageTintList = ColorStateList.valueOf(Ui.DIM)
            newsTitle.setTextColor(Ui.TEXT)
            newsBody.setTextColor(Ui.GREY)
            newsTitle.text = getString(R.string.news_list, Remote.added())
            newsBody.text = getString(R.string.news_list_body)
        }
        newsBar.visibility = View.VISIBLE
    }

    private fun onNewsTapped() {
        newsKey()?.let { Stats.newsSeen(it) }
        Remote.markSeen()
        val update = Remote.available()
        if (update != null) {
            // Vers la page de telechargement, et nulle part ailleurs :
            // Remote refuse deja toute adresse hors du depot d'AdZero.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.url)))
            } catch (_: Exception) {
            }
        }
        paintNews()
    }

    private fun buildHome(): ScrollView {
        // Centred vertically: the button is the whole screen's subject, and a
        // block of content pinned to the top with dead space below reads as
        // unfinished. It stays centred while it fits, and scrolls when it does
        // not — which is the case on a small phone, and on any phone once the
        // system font size is turned up.
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        newsBar = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d(14), d(12), d(8), d(12))
            isClickable = true
            setOnClickListener { buzz(); onNewsTapped() }
        }
        newsIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(d(20), d(20))
                .apply { marginEnd = d(12) }
        }
        newsBar.addView(newsIcon)

        val newsTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        newsTitle = TextView(this).apply {
            textSize = 13f
            typeface = Ui.BOLD
        }
        newsBody = TextView(this).apply {
            textSize = 11f
            typeface = Ui.REGULAR
            setPadding(0, d(2), 0, 0)
        }
        newsTexts.addView(newsTitle)
        newsTexts.addView(newsBody)
        newsBar.addView(newsTexts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        // La croix manquait : toucher la banniere ouvrait le telechargement,
        // donc rien ne permettait de la faire taire sans agir.
        newsClose = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            layoutParams = LinearLayout.LayoutParams(d(34), d(34))
            setPadding(d(8), d(8), d(8), d(8))
            isClickable = true
            setOnClickListener {
                tick()
                newsKey()?.let { Stats.newsSeen(it) }
                Remote.markSeen()
                paintNews()
            }
        }
        newsBar.addView(newsClose)
        Ui.lift(newsBar, 16, 5)
        page.addView(newsBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        page.addView(Ui.spacer(this, 14))

        setupCard = Ui.card(this).apply { visibility = View.GONE }
        setupCard.addView(Ui.sectionLabel(this@MainActivity, getString(R.string.setup_title)))
        setupCard.addView(Ui.spacer(this@MainActivity, 10))
        setupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupCard.addView(setupList)
        page.addView(setupCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        page.addView(Ui.spacer(this, 14))

        page.addView(Ui.spacer(this, 10))
        timer = TextView(this).apply {
            setTextColor(Ui.TEXT)
            textSize = 34f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
        }
        page.addView(timer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        hint = Ui.body(this, "").apply { gravity = Gravity.CENTER }
        page.addView(hint, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        power = PowerButton(this).apply { onToggle = { toggleFiltering() } }
        // Shrinks on a short screen rather than pushing the counters off the
        // bottom. 260dp is right on a normal phone and too tall on a small one.
        val powerHeight = minOf(d(260), resources.displayMetrics.heightPixels / 4)
        page.addView(power, LinearLayout.LayoutParams(MATCH_PARENT, powerHeight))

        // Three numbers, no jargon.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        statsRow = row
        adsValue = statValue(); timeValue = statValue(); trackerValue = statValue()
        // "300 ads for one game launch" reads as a tracker, not a shield. The
        // number is right — one launch really does fire that many attempts —
        // but nobody should have to take that on faith.
        row.addView(statCell(adsValue, getString(R.string.stat_ads)).apply {
            isClickable = true
            setOnClickListener {
                buzz()
                explainNumber()
            }
        })
        row.addView(statCell(timeValue, getString(R.string.stat_time)))
        row.addView(statCell(trackerValue, getString(R.string.stat_trackers)))
        page.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // The icons used to sit alone under a three-column row of numbers,
        // and the eye lined them up with the columns — a tight cluster in the
        // middle then read as a missing one on the left. A caption makes it a
        // block of its own instead of a broken row.
        page.addView(Ui.spacer(this, 18))

        homeStrip = buildIconStrip()
        page.addView(homeStrip, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        homeStripLabel = TextView(this).apply {
            typeface = Ui.REGULAR
            setTextColor(Ui.DIM)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, d(6), 0, 0)
        }
        page.addView(homeStripLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // One entry instead of three buttons: all three answer a question
        // about something going wrong, and they were sitting on the one screen
        // whose job is to say that nothing is. Below the watched apps rather
        // than above them, so it is not the only thing under the counter.
        page.addView(Ui.spacer(this, 22))
        page.addView(Ui.spacer(this, 18))

        // Le meme bloc que dans les statistiques. C'est ici qu'on arrive quand
        // quelque chose cloche — bien avant de penser a ouvrir un onglet de
        // chiffres.
        page.addView(troubleBlock())
        page.addView(Ui.spacer(this, 18))

        return ScrollView(this).apply {
            addView(page, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
    }


    /**
     * A row of real app icons.
     *
     * On the home screen it shows the apps AdZero has actually caught, which
     * is the clearest possible answer to "what does this thing do". During
     * onboarding there is no data yet, so it shows the phone's own apps —
     * still true, still concrete, and it makes the promise tangible.
     */
    private fun buildIconStrip(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun fillIconStrip(
        strip: LinearLayout,
        packages: List<String>,
        size: Int,
        tappable: Boolean = false,
    ) {
        strip.removeAllViews()
        for (pkg in packages.take(7)) {
            val icon = AppsCatalog.iconFor(this, pkg) ?: continue
            strip.addView(ImageView(this).apply {
                setImageDrawable(icon)
                Ui.circleClip(this)
                layoutParams = LinearLayout.LayoutParams(d(size), d(size))
                    .apply { marginStart = d(5); marginEnd = d(5) }
                if (tappable) {
                    isClickable = true
                    // An app icon on a screen of statistics is an invitation
                    // to ask about that app. It led nowhere.
                    setOnClickListener {
                        buzz()
                        expandedApp = pkg
                        scrollToExpanded = true
                        showPage(1, slideFrom = 1)
                    }
                }
            })
        }
        strip.visibility = if (strip.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun statValue() = TextView(this).apply {
        typeface = Ui.BOLD
        setTextColor(Ui.TEXT)
        textSize = 21f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER
    }

    private fun statCell(value: TextView, label: String): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        cell.addView(value, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        cell.addView(TextView(this).apply {
            typeface = Ui.REGULAR
            text = label
            setTextColor(Ui.DIM)
            textSize = 11f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return cell
    }

    private fun buildStats(): ScrollView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, d(8), 0, d(16))
        }

        // Before the first game there is nothing to chart and nothing to rank,
        // and the page was four headings each announcing that it had nothing.
        // One sentence says the same thing without looking broken.
        statsEmpty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(d(16), d(60), d(16), d(40))
            visibility = View.GONE
        }
        statsEmpty.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_tab_stats)
            setColorFilter(Ui.IDLE_A)
            layoutParams = LinearLayout.LayoutParams(d(56), d(56))
        })
        statsEmpty.addView(Ui.spacer(this, 22))
        statsEmpty.addView(TextView(this).apply {
            text = getString(R.string.stats_empty_title)
            typeface = Ui.BOLD
            setTextColor(Ui.TEXT)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        statsEmpty.addView(Ui.spacer(this, 10))
        statsEmpty.addView(Ui.body(this, getString(R.string.stats_empty_body)).apply {
            gravity = Gravity.CENTER
        })
        body.addView(statsEmpty, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        statsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Rows appear and disappear here whenever the learning finds
            // something or a suggestion is answered. Without this they blink in
            // and out, and the page looks like it glitched rather than changed.
            layoutTransition = android.animation.LayoutTransition().apply {
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(220)
            }
        }
        body.addView(statsContent, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        statsContent.addView(Ui.sectionLabel(this, getString(R.string.section_history)))
        statsContent.addView(Ui.spacer(this, 10))
        historyView = HistoryView(this)
        statsContent.addView(historyView, LinearLayout.LayoutParams(MATCH_PARENT, d(120)))
        statsContent.addView(Ui.spacer(this, 8))
        bestLine = Ui.body(this, "").apply { textSize = 12f }
        statsContent.addView(bestLine)
        dataLine = Ui.body(this, "").apply { textSize = 12f }
        statsContent.addView(dataLine)
        moneyLine = Ui.body(this, "").apply {
            textSize = 12f
            isClickable = true
            setOnClickListener { buzz(); explainMoney() }
        }
        statsContent.addView(moneyLine)
        statsContent.addView(Ui.spacer(this, 14))
        shareStatsRow = TextView(this).apply {
            text = getString(R.string.card_share)
            typeface = Ui.BOLD
            setTextColor(Ui.GREY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(d(22), d(13), d(22), d(13))
            background = Ui.softPill(this@MainActivity)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_share, 0, 0, 0)
            compoundDrawablePadding = d(12)
            compoundDrawableTintList =
                android.content.res.ColorStateList.valueOf(Ui.GREY)
            isClickable = true
            setOnClickListener { buzz(); shareCard() }
        }
        Ui.lift(shareStatsRow, 16, 5)
        statsContent.addView(
            shareStatsRow,
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        statsContent.addView(wave())
        statsContent.addView(Ui.sectionLabel(this, getString(R.string.section_leaderboard)))
        statsContent.addView(Ui.spacer(this, 10))
        leaderboardList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutTransition = android.animation.LayoutTransition().apply {
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(220)
            }
        }
        statsContent.addView(leaderboardList)

        // Le bloc "un souci ?" vivait ici aussi. Il est sur l'accueil, qui est
        // la page ou l'on arrive quand quelque chose cloche ; le repeter au
        // milieu des chiffres n'ajoutait qu'une occasion de le lire deux fois.
        // Les statistiques redeviennent ce qu'elles disent etre.

        // Everything below is server names and domains. Nobody outside this
        // project knows what that means, so it hides behind one line.
        // What the learning found, in front of the technical fold rather
        // than behind it. The app watches every app on the phone and works out
        // which servers behave like ad networks; filing that where nobody
        // looks meant the work produced nothing.
        statsContent.addView(wave())
        statsContent.addView(Ui.sectionLabel(this, getString(R.string.section_learned)))
        statsContent.addView(Ui.spacer(this, 4))
        candidateHint = Ui.body(this, getString(R.string.learned_hint)).apply { textSize = 12f }
        statsContent.addView(candidateHint)
        statsContent.addView(Ui.spacer(this, 10))
        candidateList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutTransition = android.animation.LayoutTransition().apply {
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(220)
            }
        }
        statsContent.addView(candidateList)

        // The shield acts on its own judgement rather than on a list, so it
        // owes the user an account of what it decided. Without this it silences
        // names nobody ever sees, which is exactly the behaviour people fear
        // from an app holding their DNS.
        statsContent.addView(wave())
        statsContent.addView(Ui.sectionLabel(this, getString(R.string.section_shield)))
        statsContent.addView(Ui.spacer(this, 10))
        shieldList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutTransition = android.animation.LayoutTransition().apply {
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(220)
            }
        }
        statsContent.addView(shieldList)

        statsContent.addView(wave())
        advancedToggle = TextView(this).apply {
            textSize = 13f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setTextColor(Ui.GREY)
            setPadding(0, d(13), 0, d(13))
            background = Ui.softPill(this@MainActivity)
            setOnClickListener {
                tick()
                advancedOpen = !advancedOpen
                paint()
            }
        }
        statsContent.addView(advancedToggle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        advancedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statsContent.addView(advancedBox)

        advancedBox.addView(wave())
        advancedBox.addView(Ui.sectionLabel(this, getString(R.string.section_custom)))
        advancedBox.addView(Ui.spacer(this, 10))
        customList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advancedBox.addView(customList)

        advancedBox.addView(wave())
        advancedBox.addView(Ui.sectionLabel(this, getString(R.string.section_domains)))
        advancedBox.addView(Ui.spacer(this, 4))
        advancedBox.addView(Ui.body(this, getString(R.string.explain_hint)).apply {
            textSize = 12f
            setTextColor(Ui.DIM)
        })
        advancedBox.addView(Ui.spacer(this, 8))
        domainList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advancedBox.addView(domainList)



        return ScrollView(this).apply {
            addView(body)
            isVerticalScrollBarEnabled = false
        }
    }

    private fun wave(): WaveDivider = WaveDivider(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, d(46))
    }

    /**
     * The tab bar: an icon and a label per page, with a lime pill that slides
     * under the active one. Swiping still works — the bar follows it.
     */
    /**
     * L'ecran ou l'on va quand quelque chose ne va pas.
     *
     * Il repond a trois questions, dans l'ordre ou elles se posent : un jeu ne
     * marche plus, une pub est passee, et qu'est-ce qui merite un coup d'oeil.
     * Les deux premieres sont des boutons parce qu'elles sont urgentes ; la
     * troisieme est une liste parce qu'elle ne l'est pas.
     *
     * Tout ceci vivait au bas des statistiques, ou personne ne le trouvait :
     * une page qui commence par un graphique de fierte ne se lit pas quand on
     * cherche pourquoi un jeu ne demarre plus.
     */
    /** Les deux depannages, identiques sur l'accueil et dans les statistiques. */
    private fun troubleBlock(): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(Ui.sectionLabel(this, getString(R.string.fix_trouble)))
        box.addView(Ui.spacer(this, 10))
        box.addView(troubleRow(R.drawable.ic_bug, R.string.fix_broken_title,
                               R.string.fix_broken_body) { showCulprit(broken = true) })
        box.addView(Ui.spacer(this, 8))
        box.addView(troubleRow(R.drawable.ic_no_banner, R.string.fix_leaked_title,
                               R.string.fix_leaked_body) { showCulprit(broken = false) })
        box.addView(Ui.spacer(this, 8))
        // Le troisieme outil de l'ancien panneau d'aide. Il n'etait joignable
        // que par la, donc supprimer le bouton l'aurait fait disparaitre.
        box.addView(troubleRow(R.drawable.ic_activity, R.string.test_action,
                               R.string.help_test) { runSelfTest() })
        troubleCard = box
        return box
    }

    /** Une grande ligne cliquable : une icone, un titre, une explication. */
    private fun troubleRow(
        icon: Int, title: Int, body: Int, onTap: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.softPill(this@MainActivity)
            setPadding(d(14), d(14), d(14), d(14))
            isClickable = true
            setOnClickListener { buzz(); onTap() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Ui.LIME_A)
            layoutParams = LinearLayout.LayoutParams(d(22), d(22))
                .apply { marginEnd = d(14) }
        })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = getString(title)
            setTextColor(Ui.TEXT)
            textSize = 14f
            typeface = Ui.BOLD
        })
        texts.addView(TextView(this).apply {
            text = getString(body)
            setTextColor(Ui.GREY)
            textSize = 12f
            typeface = Ui.REGULAR
            setPadding(0, d(3), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    private fun buildTabs(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.softPill(this@MainActivity)
            setPadding(d(6), d(6), d(6), d(6))
        }
        Ui.lift(bar, 22, 10)

        tabs = listOf(
            makeTab(R.drawable.ic_tab_home, getString(R.string.tab_home), 0),
            makeTab(R.drawable.ic_tab_stats, getString(R.string.tab_stats), 1),
            makeTab(R.drawable.ic_tab_apps, getString(R.string.tab_apps), 2),
        )
        for (t in tabs) bar.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    private fun makeTab(iconRes: Int, label: String, index: Int): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, d(9), 0, d(9))
            setOnClickListener {
                tick()
                showPage(index, slideFrom = if (index > page) 1 else -1)
            }
        }
        // The icon sits in a frame so a badge can be placed over its corner.
        // Tinting the whole icon lime, as this did before, was indistinguishable
        // from the colour that means "this tab is selected" — so a waiting
        // suggestion looked either like a second selected tab or like a bug.
        val holder = FrameLayout(this)
        holder.addView(ImageView(this).apply {
            setImageResource(iconRes)
            layoutParams = FrameLayout.LayoutParams(d(22), d(22))
        })
        holder.addView(View(this).apply {
            background = Ui.buttonBackground(this@MainActivity, Ui.LIME_A)
                .apply { cornerRadius = d(4).toFloat() }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(d(8), d(8)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        })
        cell.addView(holder, LinearLayout.LayoutParams(d(26), d(22)))
        cell.addView(TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Ui.MEDIUM
            gravity = Gravity.CENTER
            setPadding(0, d(3), 0, 0)
        })
        return cell
    }

    private fun paintTabs() {
        for ((i, cell) in tabs.withIndex()) {
            // The settings page keeps the bar visible but with nothing lit, so
            // the gear never looks like a fourth tab.
            val on = i == page && page != 3
            cell.background =
                if (on) Ui.gradientPill(this, Ui.LIME_A, Ui.LIME_B) else null
            val tint = if (on) Ui.BG_TOP else Ui.GREY
            val holder = cell.getChildAt(0) as FrameLayout
            (holder.getChildAt(0) as ImageView).setColorFilter(tint)
            // A waiting suggestion puts a dot on its own tab, so the discovery
            // does not depend on somebody happening to open the statistics.
            holder.getChildAt(1).visibility =
                if (i == 1 && Learning.hasUnseenCandidates()) View.VISIBLE
                else View.GONE
            (cell.getChildAt(1) as TextView).setTextColor(tint)
        }
    }

    /**
     * The light tap, for anything that toggles or opens.
     *
     * Was a 10 ms pulse at amplitude 70 out of 255, which on most phones is
     * below the threshold anyone can feel — present in the code, absent to the
     * hand. Predefined effects are the right answer where they exist: the
     * manufacturer has tuned them for that particular motor, and they follow
     * the phone's own haptic setting instead of ignoring it.
     */
    private fun tick() = Ui.tick(this)

    private fun showPage(which: Int, slideFrom: Int = 0) {
        // Pressing the tab you are already on is not a navigation. It used to
        // replay the slide anyway, so an impatient double tap animated the page
        // in twice from a place it had never left.
        if (which == page && onboarding == null) return
        // Leaving the Apps tab is the moment to apply a changed selection:
        // restarting the tunnel on every tap would thrash the connection.
        if (page == 2 && which != 2) applyAppFilterIfNeeded()

        page = which
        homePage.visibility = if (which == 0) View.VISIBLE else View.GONE
        statsPage.visibility = if (which == 1) View.VISIBLE else View.GONE
        appsPage.visibility = if (which == 2) View.VISIBLE else View.GONE
        settingsPage.visibility = if (which == 3) View.VISIBLE else View.GONE

        // Opening the statistics is what marks its suggestions as seen, which
        // is what puts the dot out. Done before painting the tabs so the badge
        // clears in the same frame as the page arrives.
        if (which == 1) Learning.markCandidatesSeen()

        paintTabs()
        if (which == 2) paintApps()
        paint()

        if (slideFrom != 0) {
            val target = listOf(homePage, statsPage, appsPage, settingsPage)[which]
            target.translationX = slideFrom * d(90).toFloat()
            target.alpha = 0f
            target.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(260)
                // Fast out, slow in: the page arrives quickly then settles,
                // which reads as a screen moving rather than a screen fading.
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        }
    }

    /** The allow-list is baked into the tunnel, so it only changes on restart. */
    private fun applyAppFilterIfNeeded() {
        if (!AppFilter.dirty || !Stats.running) return
        startService(
            Intent(this, SilenceVpnService::class.java)
                .setAction(SilenceVpnService.ACTION_STOP)
        )
        ticker.postDelayed({
            val i = Intent(this, SilenceVpnService::class.java)
                .setAction(SilenceVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        }, 400)
    }



    /**
     * Language picker. "Device language" is the default and the first entry:
     * almost nobody needs to change it, and those who do are looking for their
     * own language written in their own language.
     */

    private fun paintToggles() {
        val bannerOn = Stats.bannerWanted && Banner.allowed(this)
        bannerButton.text = rowText(getString(
            when {
                !Banner.allowed(this) -> R.string.banner_allow
                bannerOn -> R.string.banner_on
                else -> R.string.banner_off
            }
        ), getString(R.string.banner_desc))
        tintRow(bannerButton, if (bannerOn) Ui.LIME_A else Ui.GREY)

        val bubbleOn = Stats.bubbleWanted && Bubble.allowed(this)
        bubbleButton.text = rowText(getString(
            when {
                !Bubble.allowed(this) -> R.string.bubble_allow
                bubbleOn -> R.string.bubble_on
                else -> R.string.bubble_off
            }
        ), getString(R.string.bubble_desc))
        tintRow(bubbleButton, if (bubbleOn) Ui.LIME_A else Ui.GREY)

        // Une date relative plutot qu'un horodatage : "il y a deux heures"
        // se lit, "14/08 21:47" se dechiffre. Android la traduit lui-meme dans
        // les sept langues de l'app.
        val updated = Remote.lastUpdate()
        val since = if (updated <= 0L) getString(R.string.remote_never)
        else android.text.format.DateUtils.getRelativeTimeSpanString(
            updated, System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS
        ).toString().replaceFirstChar { it.lowercase() }
        remoteRow.text = rowText(
            getString(R.string.setting_remote),
            resources.getQuantityString(
                R.plurals.setting_remote_body, AdNetworks.remoteCount(),
                AdNetworks.remoteCount(), since
            )
        )
        tintRow(remoteRow, if (Stats.remoteList) Ui.LIME_A else Ui.GREY)

        sessionRow.text = rowText(
            getString(R.string.setting_session),
            getString(R.string.setting_session_body)
        )
        tintRow(sessionRow, if (Stats.sessionReports) Ui.LIME_A else Ui.GREY)

        reportRow.text = rowText(
            getString(
                if (Stats.reportWanted) R.string.report_setting_on else R.string.report_setting_off
            ),
            getString(R.string.report_desc)
        )
        tintRow(reportRow, if (Stats.reportWanted) Ui.LIME_A else Ui.GREY)

        shieldRow.text = rowText(
            getString(if (Stats.shieldWanted) R.string.shield_on else R.string.shield_off),
            getString(R.string.shield_desc)
        )
        tintRow(shieldRow, if (Stats.shieldWanted) Ui.LIME_A else Ui.GREY)

        shapeRow.text = rowText(
            getString(if (Stats.shapeWanted) R.string.shape_on else R.string.shape_off),
            getString(R.string.shape_desc)
        )
        tintRow(shapeRow, if (Stats.shapeWanted) Ui.LIME_A else Ui.GREY)
    }

    private fun paintLanguages() {
        val chosen = Locales.current(this)
        val label = chosen?.let { Locales.displayName(it) } ?: getString(R.string.language_system)
        languageRow.text = rowText(
            getString(R.string.language) + "  ·  " + label,
            getString(R.string.language_desc)
        )
    }

    /**
     * The language picker, as a sheet rather than a list unfolding in place.
     *
     * Unfolding under the row pushed everything below it down, and left the
     * choice competing for attention with the settings around it. A sheet is
     * also what every other choice in the app already uses, so this stops
     * being the one screen that behaves differently.
     */
    private fun showLanguages() {
        val chosen = Locales.current(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(20), d(22), d(20), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.language)
            typeface = Ui.BOLD
            setTextColor(Ui.TEXT)
            textSize = 20f
            setPadding(d(4), 0, d(4), 0)
        })
        box.addView(Ui.spacer(this, 14))

        val entries = listOf<Pair<String?, String>>(null to getString(R.string.language_system)) +
                Locales.TAGS.map { it to Locales.displayName(it) }

        for ((tag, name) in entries) {
            val active = tag == chosen
            box.addView(TextView(this).apply {
                text = name
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextColor(if (active) Ui.LIME_A else Ui.TEXT)
                typeface = if (active) Ui.BOLD else Ui.REGULAR
                setPadding(d(18), d(14), d(18), d(14))
                // The tick goes at the end, where a list of choices normally
                // puts it. On the left it pushed every other line right to
                // make room, so nothing lined up and the mark was easy to
                // miss among eight names.
                if (active) {
                    background = Ui.softPill(this@MainActivity)
                    val mark = getDrawable(R.drawable.ic_check)
                        ?.apply { setBounds(0, 0, d(19), d(19)) }
                    setCompoundDrawablesRelative(null, null, mark, null)
                    compoundDrawableTintList =
                        android.content.res.ColorStateList.valueOf(Ui.LIME_A)
                }
                setOnClickListener {
                    tick()
                    currentSheet?.dismiss()
                    Locales.set(this@MainActivity, tag)
                    // Below Android 13 nothing reloads on its own.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
                    else paintLanguages()
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(2) })
        }
        sheet(box)
    }


    // -------------------------------------------------------------- settings

    private fun buildSettings(): ScrollView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, d(8), 0, d(16))
        }

        body.addView(Ui.sectionLabel(this, getString(R.string.section_permissions)))
        body.addView(Ui.spacer(this, 10))
        // A plain container, not a card: each permission gets its own card
        // inside it. Stacked in a single frame they read as one long thing
        // with several buttons rather than as separate decisions.
        setupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        setupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupCard.addView(setupList)
        body.addView(setupCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setupDone = Ui.body(this, getString(R.string.setup_done)).apply { textSize = 13f }
        body.addView(setupDone)

        body.addView(Ui.spacer(this, 14))
        warningCard = Ui.card(this).apply { visibility = View.GONE }
        warningText = TextView(this).apply {
            typeface = Ui.REGULAR
            setTextColor(Ui.RED_A)
            textSize = 13f
        }
        warningCard.addView(warningText)
        body.addView(warningCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        body.addView(Ui.spacer(this, 26))
        body.addView(Ui.sectionLabel(this, getString(R.string.section_display)))
        body.addView(Ui.spacer(this, 10))

        bannerButton = settingRow(R.drawable.ic_bell) { toggleBanner() }
        body.addView(bannerButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))
        bubbleButton = settingRow(R.drawable.ic_bubble) { toggleBubble() }
        body.addView(bubbleButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))
        remoteRow = settingRow(R.drawable.ic_globe) {
            Stats.remoteList = !Stats.remoteList
            // Allumer le reglage verifie tout de suite : attendre le lendemain
            // pour qu'il se passe quelque chose donne l'impression que
            // l'interrupteur ne fait rien.
            if (Stats.remoteList) Remote.refresh(this, force = true)
            paintToggles()
        }
        body.addView(remoteRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))

        // Le rapport de fin de partie. Coupable ici, sinon les gens le
        // coupent au niveau d'Android et perdent aussi les alertes utiles.
        sessionRow = settingRow(R.drawable.ic_clock) {
            Stats.sessionReports = !Stats.sessionReports
            paintToggles()
        }
        body.addView(sessionRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))

        reportRow = settingRow(R.drawable.ic_flag) {
            Stats.rememberReport(!Stats.reportWanted)
            paintToggles()
            if (Stats.running) startService(
                Intent(this, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_REFRESH)
            )
        }
        body.addView(reportRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))
        shieldRow = settingRow(R.drawable.ic_shield_check) {
            Stats.rememberShield(!Stats.shieldWanted)
            paintToggles()
        }
        body.addView(shieldRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))
        shapeRow = settingRow(R.drawable.ic_activity) {
            Stats.rememberShape(!Stats.shapeWanted)
            paintToggles()
        }
        body.addView(shapeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        body.addView(Ui.spacer(this, 26))
        body.addView(Ui.sectionLabel(this, getString(R.string.section_general)))
        body.addView(Ui.spacer(this, 10))

        languageRow = settingRow(R.drawable.ic_globe) { showLanguages() }
        tintRow(languageRow, Ui.GREY)
        body.addView(languageRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))
        shareRow = settingRow(R.drawable.ic_share) { shareApp() }
        promoRow = settingRow(R.drawable.ic_share) { sharePromo() }
        promoRow.text = rowText(getString(R.string.promo_share), getString(R.string.promo_desc))
        tintRow(promoRow, Ui.GREY)
        body.addView(promoRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))

        tourRow = settingRow(R.drawable.ic_activity) {
            // Back to the home page first: the tour points at views that live
            // there, and pointing at something on a hidden page would light an
            // empty rectangle.
            showPage(0)
            root.postDelayed({ startTour() }, 250)
        }
        tourRow.text = rowText(getString(R.string.tour_again), getString(R.string.tour_again_desc))
        tintRow(tourRow, Ui.GREY)
        body.addView(tourRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 8))

        shareRow.text = rowText(getString(R.string.share_action), getString(R.string.share_desc))
        tintRow(shareRow, Ui.GREY)
        body.addView(shareRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        body.addView(Ui.spacer(this, 14))
        body.addView(Ui.body(this, getString(R.string.widget_hint)).apply {
            textSize = 12f
            setTextColor(Ui.DIM)
        })

        return ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
    }

    /**
     * A setting's name and state on the first line, what it actually does on
     * the second.
     *
     * The rows used to read "Burst shield · on" and nothing else, which tells
     * someone who already knows what it is that it is on, and tells everybody
     * else nothing at all. Built as spans rather than as two views so the rows
     * stay plain TextViews and the code that sets their state is unchanged.
     */
    private fun rowText(state: String, description: String): CharSequence {
        val text = android.text.SpannableString(state + "\n" + description)
        val from = state.length + 1
        val flag = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        text.setSpan(android.text.style.RelativeSizeSpan(0.8f), from, text.length, flag)
        text.setSpan(
            android.text.style.StyleSpan(Typeface.NORMAL), from, text.length, flag
        )
        text.setSpan(
            android.text.style.ForegroundColorSpan(Ui.DIM), from, text.length, flag
        )
        return text
    }

    /**
     * A settings row, with the icon carried as a compound drawable rather than
     * as a separate view.
     *
     * Keeping the row a single TextView matters: every place that updates a
     * setting's state does it by assigning .text, and turning these into
     * containers would have meant rewriting all of them to reach inside.
     *
     * The text moves off centre at the same time. Centred text was fine while
     * the rows were one line; with an icon on the left it has to line up, or
     * the icon reads as floating next to the pill instead of belonging to it.
     */
    private fun settingRow(icon: Int, action: () -> Unit) = TextView(this).apply {
        Ui.lift(this, 16, 5)
        textSize = 14f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setTextColor(Ui.GREY)
        setPadding(d(18), d(16), d(18), d(16))
        background = Ui.softPill(this@MainActivity)
        setLineSpacing(d(4).toFloat(), 1f)
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawablePadding = d(16)
        setOnClickListener { tick(); action() }
    }

    /** Keeps a row's icon the same colour as its text. */
    private fun tintRow(row: TextView, colour: Int) {
        row.setTextColor(colour)
        row.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(colour)
    }

    // ------------------------------------------------------------- onboarding

    private fun showOnboarding() {
        if (onboarding != null) return
        val view = Onboarding(this) { dismissOnboarding() }
        stack.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        onboarding = view
    }

    private fun dismissOnboarding() {
        onboarding?.let { stack.removeView(it) }
        onboarding = null
        // The welcome flow covers the pages rather than replacing them, and
        // whatever was underneath is still where it was left. Landing on the
        // statistics after setup showed a screen of zeroes to somebody who had
        // just been told the app works.
        showPage(0)
        paint()
        // Posted, not called: the pages were laid out under the welcome flow,
        // but the tour measures where things actually are, and the strip of
        // watched apps has not been filled yet at this instant.
        if (!Stats.toured) root.postDelayed({ startTour() }, 700)
    }

    /**
     * The guided tour of the real screen.
     *
     * The welcome flow can say what the app does but not where anything is —
     * it is covering the screen it would have to point at. This runs once
     * afterwards, on the live layout, and every target is a real view so the
     * spotlight lands correctly whatever the screen size or the language.
     */
    fun startTour() {
        if (page != 0) showPage(0)
        Stats.rememberToured()
        val steps = listOfNotNull(
            Tour.Step(power, getString(R.string.tour1_title), getString(R.string.tour1_body)),
            Tour.Step(stateLabel, getString(R.string.tour2_title), getString(R.string.tour2_body)),
            Tour.Step(statsRow, getString(R.string.tour3_title), getString(R.string.tour3_body)),
            Tour.Step(troubleCard, getString(R.string.tour4_title), getString(R.string.tour4_body)),
            tabs.getOrNull(1)?.let {
                Tour.Step(it, getString(R.string.tour5_title), getString(R.string.tour5_body))
            },
            tabs.getOrNull(2)?.let {
                Tour.Step(it, getString(R.string.tour6_title), getString(R.string.tour6_body))
            },
            Tour.Step(gear, getString(R.string.tour7_title), getString(R.string.tour7_body)),
        )
        val tour = Tour(this, steps) { buzz() }
        stack.addView(
            tour,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Called by the last onboarding page. */
    fun startFromOnboarding() {
        if (!Stats.running) toggleFiltering()
    }


    /**
     * Horizontal swipe between Home, Stats and Apps.
     *
     * Hand-rolled rather than pulling in ViewPager2: the app has no
     * dependencies and three static pages do not justify the first one. The
     * detector only claims a gesture that is clearly horizontal, so the
     * vertical scrolling inside Stats and Apps still works.
     */
    private lateinit var swipeDetector: GestureDetector

    /**
     * Horizontal swipe between Home, Stats and Apps.
     *
     * Fed from dispatchTouchEvent rather than a touch listener on the pages:
     * the power button and the lists consume their own events, so a listener
     * on the parent never sees the gesture at all.
     *
     * Hand-rolled rather than pulling in ViewPager2 — the app has no
     * dependencies and three static pages do not justify the first one.
     */
    private fun attachSwipe() {
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                if (page == 3) return false
                val dx = e2.x - (e1?.x ?: return false)
                val dy = e2.y - e1.y
                // Clearly horizontal, or the vertical scrolling in Stats and
                // Apps would fight the page change.
                if (kotlin.math.abs(dx) < d(60)) return false
                if (kotlin.math.abs(dx) < kotlin.math.abs(dy) * 1.5f) return false
                val next = if (dx < 0) page + 1 else page - 1
                if (next !in 0..2) return false
                tick()
                showPage(next, slideFrom = if (dx < 0) 1 else -1)
                return true
            }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::swipeDetector.isInitialized) swipeDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    // ------------------------------------------------------------------ acts

    /**
     * Relaunched from the notification while the activity was already alive.
     * Without this the report would only ever open on a cold start.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == ACTION_REPORT) root.post { showReport() }
        if (intent?.action == ACTION_SESSION_CARD) root.post { shareSession(intent) }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt(KEY_PAGE, page)
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted something in Settings and come back.
        Ui.animating = true
        stack.invalidate()
        // Une verification a l'ouverture : on ouvre AdZero bien plus souvent
        // qu'on ne rallume la protection, et c'est ce qui fait qu'un domaine
        // pousse sur GitHub arrive en minutes plutot qu'en jours.
        Remote.refresh(this)
        if (Milestones.pending > 0) root.postDelayed({ celebrate() }, 600)
        // The counters kept moving while the app was away, so nothing on the
        // statistics page can be assumed still current. Forgetting what was
        // last drawn makes the next paint rebuild it for real.
        drawn.clear()
        Learning.invalidateUnseen()
        healIfStopped()
        onboarding?.refresh()
        checkPrivateDns()
        Bubble.hide()
        ticker.post(tick)
    }

    override fun onPause() {
        super.onPause()
        // Stop repainting the moment we leave: these views draw every frame.
        Ui.animating = false
        ticker.removeCallbacks(tick)
        // Was four stores listed by hand, and the shield's notebook — added
        // months later — was never added to the list.
        Persist.saveAll()
        applyAppFilterIfNeeded()
        if (Stats.bubbleWanted && Stats.running && Bubble.allowed(this)) Bubble.show(this)
    }

    private fun toggleFiltering() {
        buzz()
        if (Stats.running) {
            startService(
                Intent(this, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_STOP)
            )
            return
        }
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, 1) else startFiltering()
    }

    /** A short double tap of haptics: the switch should be felt, not just seen. */
    /** The firmer one, for a decision rather than a navigation. */
    private fun buzz() = Ui.buzz(this)

    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == 1 && result == RESULT_OK) startFiltering()
    }

    /**
     * The notification permission is a dialog, not a settings page, so the
     * activity never stops and onResume does not fire. Without this the card
     * would still say "grant" after the user had just granted it.
     */
    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        results: IntArray,
    ) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 2) {
            onboarding?.refresh()
            paintSetup()
        }
    }

    private fun startFiltering() {
        Stats.error = null
        Stats.reset()
        val i = Intent(this, SilenceVpnService::class.java)
            .setAction(SilenceVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
    }

    /** The on-screen alert needs the "display over other apps" permission. */
    private fun toggleBanner() {
        if (Stats.bannerWanted && Banner.allowed(this)) {
            Stats.rememberBanner(false)
            Banner.clear()
            return
        }
        if (!Banner.allowed(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        Stats.rememberBanner(true)
    }

    private fun toggleBubble() {
        if (Stats.bubbleWanted) {
            Stats.rememberBubble(false)
            Bubble.hide()
            return
        }
        if (!Bubble.allowed(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        Stats.rememberBubble(true)
    }



    /**
     * Lists only what is actually missing, each with a one-tap button. When
     * nothing is missing the card disappears entirely — a permanent settings
     * list would just be clutter.
     */
    private fun paintSetup() {
        val missing = Permissions.missing(this)
        if (missing.isEmpty()) {
            setupCard.visibility = View.GONE
            setupDone.visibility = View.VISIBLE
            return
        }
        setupCard.visibility = View.VISIBLE
        setupDone.visibility = View.GONE
        if (setupList.childCount == missing.size && setupList.tag == missing.hashCode()) return
        setupList.tag = missing.hashCode()
        setupList.removeAllViews()

        for (item in missing) {
            val row = Ui.card(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            block.addView(TextView(this).apply {
                text = getString(Permissions.titleOf(item))
                setTextColor(Ui.TEXT)
                textSize = 14f
                typeface = Ui.BOLD
            })
            block.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = getString(Permissions.detailOf(item))
                setTextColor(Ui.GREY)
                textSize = 11f
            })
            if (Permissions.restricted(item)) {
                block.addView(TextView(this).apply {
                    typeface = Ui.REGULAR
                    text = getString(R.string.setup_restricted)
                    setTextColor(Ui.LIME_A)
                    textSize = 11f
                    setPadding(0, d(6), 0, 0)
                })
            }
            row.addView(block, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = getString(R.string.setup_grant)
                setTextColor(Ui.BG_TOP)
                textSize = 11f
                typeface = Ui.BOLD
                setPadding(d(16), d(9), d(16), d(9))
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
                setOnClickListener { buzz(); request(item) }
                // The description takes every remaining pixel of the row, so
                // without a margin the button ends up flush against the text.
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginStart = d(16)
            })
            setupList.addView(
                row,
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { bottomMargin = d(8) }
            )
        }
    }

    private fun request(item: Permissions.Item) {
        // Notifications are a runtime permission: a system dialog, not a
        // settings page. The welcome flow already handled that; here the
        // button fell through the null intent and did nothing at all.
        if (item == Permissions.Item.NOTICES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
            }
            return
        }
        val intent = Permissions.intentFor(this, item) ?: return
        if (item == Permissions.Item.VPN) startActivityForResult(intent, 1)
        else startActivity(intent)
    }

    // ------------------------------------------------------------------ apps

    private fun buildApps(): ScrollView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, d(8), 0, d(16))
        }
        // A single, unambiguous banner at the top: are we protecting
        // everything, or only a chosen few? The old wording made the user
        // infer it from an empty list.
        appsHeader = TextView(this).apply {
            textSize = 14f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(d(16), d(15), d(16), d(15))
            setOnClickListener {
                tick()
                if (!AppFilter.protectsEverything()) AppFilter.protectEverythingAgain()
                paintApps()
            }
        }
        body.addView(appsHeader, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 14))
        searchField = EditText(this).apply {
            // The font sweep matched text views only, and this is the one
            // field in the app that is not one.
            typeface = Ui.REGULAR
            hint = getString(R.string.apps_search)
            setHintTextColor(Ui.DIM)
            setTextColor(Ui.TEXT)
            textSize = 15f
            background = Ui.softPill(this@MainActivity)
            setPadding(d(18), d(13), d(18), d(13))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) = paintApps()
                override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
            })
        }
        body.addView(searchField, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 14))
        appsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(buildAppFilters(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        body.addView(Ui.spacer(this, 14))
        body.addView(appsList)
        return ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
    }

    /**
     * Four ways to cut two hundred rows down to the handful being looked for.
     *
     * The list was one long alphabet with the noisy apps floated to the top,
     * which is the right default and useless for the two real questions people
     * arrive with: what did I turn off, and where are my games.
     */
    private enum class AppFilterView { ALL, GAMES, WITH_ADS, UNPROTECTED }

    private var appView = AppFilterView.ALL

    private fun buildAppFilters(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        appFilterChips = AppFilterView.values().map { view ->
            TextView(this).apply {
                text = getString(
                    when (view) {
                        AppFilterView.ALL -> R.string.filter_all
                        AppFilterView.GAMES -> R.string.filter_games
                        AppFilterView.WITH_ADS -> R.string.filter_with_ads
                        AppFilterView.UNPROTECTED -> R.string.filter_unprotected
                    }
                )
                textSize = 12f
                typeface = Ui.BOLD
                gravity = Gravity.CENTER
                setPadding(d(12), d(9), d(12), d(9))
                isClickable = true
                setOnClickListener {
                    tick()
                    appView = view
                    paintApps()
                }
            }.also { chip ->
                row.addView(
                    chip,
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        .apply { if (view != AppFilterView.ALL) marginStart = d(6) }
                )
            }
        }
        return row
    }

    private fun paintAppFilters() {
        for ((index, chip) in appFilterChips.withIndex()) {
            val selected = AppFilterView.values()[index] == appView
            chip.background =
                if (selected) Ui.gradientPill(this, Ui.LIME_A, Ui.LIME_B)
                else Ui.softPill(this)
            chip.setTextColor(if (selected) Ui.BG_TOP else Ui.GREY)
        }
    }

    /** The banner at the top of the tab: how many apps are left alone. */
    private fun paintAppsHeader() {
        val all = AppFilter.protectsEverything()
        val label = if (all) getString(R.string.apps_master_on)
        else getString(R.string.apps_excluded, AppFilter.excludedApps().size) +
                "  ·  " + getString(R.string.apps_reset)
        val colour = if (all) Ui.LIME_A else Ui.TEXT

        // Whole shield when nothing is excluded, struck through when something
        // is: the same pair the state badge uses, so the two agree on what a
        // shield means anywhere in the app.
        //
        // Carried inside the text rather than as a compound drawable. Android
        // pins compound drawables to the edges of the view, so on a full-width
        // banner with centred text the icon stayed at the far left with the
        // whole remaining width between it and the words.
        val mark = getDrawable(if (all) R.drawable.ic_shield_check else R.drawable.ic_shield_off)
            ?.apply {
                setBounds(0, 0, d(17), d(17))
                setTint(colour)
            }
        appsHeader.text = if (mark == null) label else
            android.text.SpannableString("   $label").apply {
                setSpan(
                    Ui.CenteredIcon(mark),
                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        appsHeader.setTextColor(colour)
        appsHeader.background = Ui.softPill(this)
        appsHeader.gravity = Gravity.CENTER
        appsHeader.setCompoundDrawablesRelative(null, null, null, null)
    }

    private fun paintApps() {
        paintAppsHeader()

        val entries = AppsCatalog.cached()
        if (entries == null) {
            appsList.removeAllViews()
            // Reading a couple of hundred app icons takes a moment on a phone
            // with a full home screen. A line of text alone read as an empty
            // list rather than as work in progress.
            appsList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(10), 0, d(10))
                addView(ProgressBar(this@MainActivity).apply {
                    isIndeterminate = true
                    indeterminateTintList =
                        android.content.res.ColorStateList.valueOf(Ui.LIME_A)
                    layoutParams = LinearLayout.LayoutParams(d(20), d(20))
                        .apply { marginEnd = d(14) }
                })
                addView(Ui.body(this@MainActivity, getString(R.string.apps_loading)))
            })
            // Icons for a couple of hundred apps drop frames if read on the
            // main thread, so the catalogue is built once in the background.
            AppsCatalog.load(this) { if (page == 2) paintApps() }
            return
        }

        appsList.removeAllViews()

        val excludedSet = AppFilter.excludedApps()

        paintAppFilters()

        // Apps already caught showing ads come first: those are the ones
        // anyone opening this screen is actually looking for.
        val noisy = Leaderboard.ranking(99).map { it.app }.toSet()

        val query = searchField.text.toString().trim().lowercase()
        val visible = entries
            .filter { query.isEmpty() || it.label.lowercase().contains(query) }
            .filter {
                when (appView) {
                    AppFilterView.ALL -> true
                    AppFilterView.GAMES -> it.isGame
                    AppFilterView.WITH_ADS -> it.pkg in noisy
                    AppFilterView.UNPROTECTED -> it.pkg in excludedSet
                }
            }

        if (visible.isEmpty()) {
            appsList.addView(Ui.body(this, getString(R.string.filter_empty)))
            return
        }

        // The two-section split is the default view's doing. Once a filter has
        // narrowed the list to one kind of thing, splitting it again by another
        // kind just adds headings to a short list.
        val split = appView == AppFilterView.ALL && query.isEmpty()
        val (withAds, others) =
            if (split) visible.partition { it.pkg in noisy } else emptyList<AppsCatalog.Entry>() to visible

        // Two hundred rows built in one pass drop frames on the way in. The
        // apps that show ads come first and land immediately; the long tail is
        // appended in small batches between frames, so the tab opens instantly
        // and fills in while you look at it.
        appsBatch++
        val batchId = appsBatch

        if (withAds.isNotEmpty()) {
            appsList.addView(Ui.sectionLabel(this, getString(R.string.apps_with_ads)))
            appsList.addView(Ui.spacer(this, 8))
            addAppRows(withAds, excludedSet)
            appsList.addView(Ui.spacer(this, 18))
        }
        if (others.isEmpty()) return
        if (split) {
            appsList.addView(Ui.sectionLabel(this, getString(R.string.apps_others)))
            appsList.addView(Ui.spacer(this, 8))
        }
        appendInBatches(others, excludedSet, 0, batchId)
    }

    private fun appendInBatches(
        rest: List<AppsCatalog.Entry>,
        excludedSet: Set<String>,
        from: Int,
        batchId: Int,
    ) {
        // A newer repaint cancels this one, otherwise a search keystroke would
        // race with the tail of the previous list.
        if (batchId != appsBatch || page != 2) return
        val to = minOf(from + 18, rest.size)
        addAppRows(rest.subList(from, to), excludedSet)
        if (to < rest.size) {
            appsList.post { appendInBatches(rest, excludedSet, to, batchId) }
        }
    }

    private fun addAppRows(entries: List<AppsCatalog.Entry>, excludedSet: Set<String>) {
        for (entry in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(d(12), d(9), d(12), d(9))
            }

            row.addView(ImageView(this).apply {
                setImageDrawable(entry.icon)
                Ui.circleClip(this)
                layoutParams = LinearLayout.LayoutParams(d(38), d(38))
                    .apply { marginEnd = d(14) }
            })
            row.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = entry.label
                textSize = 15f
                maxLines = 1
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                setTextColor(Ui.DIM)
                textSize = 11f
                typeface = Ui.BOLD
            })

            paintAppRow(row, entry.pkg in excludedSet)
            // Repaints this row and nothing else. It used to rebuild the whole
            // list, which threw the scroll position back to the top — so
            // switching two apps off in a row meant hunting for the second one
            // again.
            row.setOnClickListener {
                buzz()
                AppFilter.toggle(entry.pkg)
                paintAppRow(row, AppFilter.isExcluded(entry.pkg))
                paintAppsHeader()
            }

            appsList.addView(
                row,
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = d(4) }
            )
        }
    }

    /**
     * Dresses one row for its state.
     *
     * "Off" means left alone, not protected: protection is the default and this
     * list is where somebody takes it away.
     */
    private fun paintAppRow(row: LinearLayout, off: Boolean) {
        row.background = if (off) Ui.softPill(this) else null
        (row.getChildAt(0) as ImageView).alpha = if (off) 0.35f else 1f
        (row.getChildAt(1) as TextView).setTextColor(if (off) Ui.DIM else Ui.TEXT)
        (row.getChildAt(2) as TextView).text =
            if (off) getString(R.string.apps_off_badge) else ""
    }

    // ----------------------------------------------------------------- pause


    // ----------------------------------------------------------------- paint

    /**
     * Starts the tunnel again if it died while nobody was looking.
     *
     * Android stops background services under memory or battery pressure, and
     * some manufacturers do it aggressively. When that happens the app still
     * remembers it was protecting, the tunnel is gone, and the person carries
     * on believing they are covered. Since the VPN consent is already granted
     * at this point, bringing it back needs no dialog and no permission — so
     * there is no reason to ask rather than simply fix it.
     */
    private fun healIfStopped() {
        if (!Stats.running || SilenceVpnService.alive) return
        if (android.net.VpnService.prepare(this) != null) return
        val intent = Intent(this, SilenceVpnService::class.java)
            .setAction(SilenceVpnService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (_: Exception) {
            // Android forbids starting a foreground service from the
            // background in some states; the user can still use the button.
        }
    }

    /**
     * Whether another app has been in the foreground in the last few minutes.
     *
     * Only those hold stale addresses, so telling somebody to restart their
     * games when the phone has been idle is noise. Answers false when usage
     * access was never granted: guessing wrong in the direction of saying
     * nothing is the cheaper mistake.
     */
    private fun somethingIsOpen(): Boolean {
        if (!Attribution.usageAccessGranted(this)) return false
        return try {
            val usage = getSystemService(android.app.usage.UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usage.queryEvents(now - 5 * 60_000, now)
            val event = android.app.usage.UsageEvents.Event()
            val home = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) continue
                val pkg = event.packageName ?: continue
                if (pkg == packageName || pkg == home) continue
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun checkPrivateDns() {
        val mode = Settings.Global.getString(contentResolver, "private_dns_mode")
        val blocking = mode != null && mode != "off" && mode != "opportunistic"
        Stats.privateDnsDetected = blocking
        if (blocking) warningText.text = getString(R.string.warn_private_dns, mode)
    }

    private fun paint() {
        // Both, deliberately. The remembered flag survives a process restart so
        // the screen does not blink off while the service comes back; the live
        // one cannot outlive the tunnel. Requiring both means the app stops
        // claiming to protect somebody after an install killed the service.
        paintNews()
        val on = Stats.running && SilenceVpnService.alive

        stateLabel.text = getString(if (on) R.string.state_on else R.string.state_off)
        stateLabel.setTextColor(if (on) Ui.LIME_A else Ui.GREY)
        stateLabel.background = Ui.softPill(this)
        // A struck-through shield when off: the state is readable from the
        // shape alone, before the word is read and whatever the colour.
        //
        // Sized and bounded by hand rather than left at its intrinsic 24dp.
        // Against eleven-point small caps a full-size icon towers over the
        // word, and it stretches the pill to the icon's height, which is what
        // left the text sitting at the top of it.
        val shield = getDrawable(
            if (on) R.drawable.ic_shield_check else R.drawable.ic_shield_off
        )?.apply { setBounds(0, 0, d(15), d(15)) }
        stateLabel.setCompoundDrawablesRelative(shield, null, null, null)
        stateLabel.compoundDrawablePadding = d(8)
        stateLabel.compoundDrawableTintList =
            android.content.res.ColorStateList.valueOf(if (on) Ui.LIME_A else Ui.GREY)

        power.active = on
        aurora.active = on
        hint.text = getString(if (on) R.string.tap_to_stop else R.string.tap_to_start)

        val up = Stats.uptimeSeconds()
        timer.text = String.format("%02d:%02d:%02d", up / 3600, (up % 3600) / 60, up % 60)
        timer.setTextColor(if (on) Ui.TEXT else Ui.DIM)

        val ads = Leaderboard.totalAttempts()
        adsValue.text = ads.toString()
        timeValue.text = formatDuration(ads * Leaderboard.SECONDS_PER_AD)
        trackerValue.text = Stats.trackers.get().toString()

        val caught = Leaderboard.ranking(7).map { it.app }
        fillIconStrip(homeStrip, caught, 36, tappable = true)
        homeStripLabel.visibility = homeStrip.visibility
        // Browsers are the surprise: people install this for games and then
        // notice the web is quiet too. Say it, but only once it is true of
        // their own phone.
        val browser = caught.any { it in BROWSERS }
        homeStripLabel.text = getString(R.string.stat_apps) +
                if (browser) "  ·  " + getString(R.string.home_browser) else ""
        homeStripLabel.setTextColor(if (browser) Ui.GREY else Ui.DIM)

        // INVISIBLE, not GONE. These two only make sense while protection is
        // running, but removing them from the layout made everything above
        // jump up the moment it started, which read as a stutter rather than
        // as a screen changing state.
        // Always offered, unlike the two above. Somebody who doubts whether
        // protection is really on needs the test most when the app is claiming
        // everything is fine, and a diagnostic you can only reach when things
        // look healthy is not a diagnostic.


        if (page == 3) {
            paintSetup()
            paintLanguages()
            paintWarning()
            paintToggles()
        }
        if (page == 1) {
            advancedToggle.text =
                getString(if (advancedOpen) R.string.advanced_hide else R.string.advanced_show)
            advancedBox.visibility = if (advancedOpen) View.VISIBLE else View.GONE
            historyView.days = History.lastDays()
            historyView.invalidate()
            val best = History.bestDay()
            val streak = History.streak()
            bestLine.text = if (best == 0) "" else
                getString(R.string.best_day, best) + "   ·   " + getString(R.string.streak_days, streak)

            // Megabytes are the cost of an ad that nobody talks about, and the
            // one that lands with someone on a small data plan. An estimate,
            // and labelled as one: a video ad runs two to four megabytes, a
            // banner a fraction of that, so the average is deliberately low.
            val ads = Leaderboard.totalAttempts()
            dataLine.text = if (ads == 0) "" else
                getString(R.string.data_saved, formatData(ads * 2.0))

            // A range, never a single figure. What an ad pays the app that
            // showed it swings by a factor of four depending on the format,
            // the country and the time of year, and a precise-looking total
            // would be a made-up one.
            moneyLine.text = if (ads == 0) "" else getString(
                R.string.money_saved,
                formatMoney(ads * DOLLARS_PER_AD_LOW),
                formatMoney(ads * DOLLARS_PER_AD_HIGH)
            )
            // "Nothing yet" everywhere, or the page proper — never both.
            val blank = Leaderboard.totalAttempts() == 0 && History.lastDays().all { it.count == 0 }
            statsEmpty.visibility = if (blank) View.VISIBLE else View.GONE
            statsContent.visibility = if (blank) View.GONE else View.VISIBLE

            paintLeaderboard()
            paintShield()
            paintCandidates()
            paintCustom()
            paintDomains()
        }
    }

    /**
     * What one avoided ad costs the networks, low and high.
     *
     * These are per-impression, derived from published European eCPM ranges
     * for mobile games: roughly three to twelve dollars per thousand, depending
     * mostly on whether the slot was a rewarded video or a banner. The low end
     * is deliberately the floor rather than the average.
     */
    private val DOLLARS_PER_AD_LOW = 0.003
    private val DOLLARS_PER_AD_HIGH = 0.012

    /**
     * Cents while it is small, whole dollars once it stops being.
     *
     * Dollars because that is the currency ad networks actually quote in:
     * every published rate for this is a dollar figure, and converting it to
     * something else would add a made-up exchange rate on top of an estimate.
     */
    private fun formatMoney(dollars: Double): String = when {
        dollars < 1 -> String.format("$%.2f", dollars)
        dollars < 100 -> String.format("$%.1f", dollars)
        else -> String.format("$%.0f", dollars)
    }

    private fun explainMoney() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.money_title)
            typeface = Ui.BOLD
            setTextColor(Ui.TEXT)
            textSize = 20f
        })
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(R.string.money_explained)))
        sheet(box)
    }

    /** Megabytes below a gigabyte, gigabytes above, one decimal either way. */
    private fun formatData(megabytes: Double): String =
        if (megabytes >= 1024)
            String.format("%.1f", megabytes / 1024) + " " + getString(R.string.unit_gb)
        else String.format("%.0f", megabytes) + " " + getString(R.string.unit_mb)

    private fun formatDuration(seconds: Int): String = when {
        seconds < 60 -> getString(R.string.duration_s, seconds)
        seconds < 3600 -> getString(R.string.duration_m, seconds / 60)
        else -> getString(R.string.duration_hm, seconds / 3600, (seconds % 3600) / 60)
    }

    private fun paintWarning() {
        val error = Stats.error
        when {
            error != null -> {
                warningText.text = error
                warningCard.visibility = View.VISIBLE
            }
            Stats.privateDnsDetected -> warningCard.visibility = View.VISIBLE
            else -> warningCard.visibility = View.GONE
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    /** The ad networks one app talks to, as a card to slot under its row. */
    private fun networksPanel(app: String): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.sectionLabel(this, getString(R.string.detail_networks)))
        card.addView(Ui.spacer(this, 8))
        val networks = Learning.networksOf(app)
        if (networks.isEmpty()) {
            card.addView(Ui.body(this, getString(R.string.detail_none)).apply { textSize = 12f })
        }
        for (n in networks) {
            val who = Explain.cardFor(n)
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, d(6), 0, d(6))
                // Same rule as everywhere else: a hostname is a thing you can
                // look up, not a thing you can read.
                isClickable = true
                setOnClickListener { buzz(); explainHost(n) }
            }
            line.addView(TextView(this).apply {
                text = if (who.owner.isNotEmpty()) who.owner
                else Explain.kindLabel(this@MainActivity, who.kind)
                setTextColor(Ui.LIME_A)
                textSize = 13f
                typeface = Ui.BOLD
            })
            line.addView(TextView(this).apply {
                text = n
                setTextColor(Ui.DIM)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            })
            card.addView(line)
        }
        return card
    }

    /** Amene [target] en haut de la page des statistiques. */
    private fun scrollTo(target: View) {
        var y = 0
        var view: View? = target
        // On remonte les parents jusqu'au ScrollView en cumulant les
        // decalages : la ligne est enfouie de plusieurs niveaux, et son top
        // seul ne dit rien de sa position dans la page.
        while (view != null && view !== statsPage) {
            y += view.top
            view = view.parent as? View
        }
        statsPage.smoothScrollTo(0, (y - d(12)).coerceAtLeast(0))
    }

    private fun paintLeaderboard() {
        if (unchanged("leaderboard", Leaderboard.ranking().joinToString {
                it.app + it.attempts
            } + expandedApp)) return
        // Le panneau vit dans la liste qu'on vide : sa reference ne vaut plus
        // rien. expandedApp, lui, survit — c'est une intention de
        // l'utilisateur, pas un morceau de vue.
        expandedCard = null
        val rows = Leaderboard.ranking()
        pauseAnimation(leaderboardList)
        leaderboardList.removeAllViews()
        if (rows.isEmpty()) {
            // Sans l'acces aux statistiques d'usage, chaque requete arrive
            // anonyme : AdZero bloque toujours, mais ne peut nommer personne.
            // Dire "lance un jeu" a quelqu'un qui vient d'en lancer un est la
            // pire reponse possible — elle accuse et elle n'aide pas.
            if (!Attribution.usageAccessGranted(this)) {
                leaderboardList.addView(Ui.body(this, getString(R.string.leaderboard_no_usage)))
                leaderboardList.addView(Ui.spacer(this, 12))
                leaderboardList.addView(TextView(this).apply {
                    text = getString(R.string.leaderboard_grant)
                    setTextColor(Ui.BG_TOP)
                    textSize = 13f
                    typeface = Ui.BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, d(13), 0, d(13))
                    background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
                    setOnClickListener {
                        buzz()
                        // On ouvre le reglage ; c'est l'utilisateur qui accorde.
                        try {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                return
            }
            leaderboardList.addView(Ui.body(this, getString(R.string.leaderboard_empty)))
            return
        }
        val max = rows.first().attempts.coerceAtLeast(1)
        for (row in rows) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(9), 0, d(9))
            }
            // The icon is what makes the list readable at a glance: a name
            // alone forces you to read every row.
            line.addView(ImageView(this).apply {
                setImageDrawable(AppsCatalog.iconFor(this@MainActivity, row.app))
                Ui.circleClip(this)
                layoutParams = LinearLayout.LayoutParams(d(34), d(34))
                    .apply { marginEnd = d(12) }
            })

            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            block.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = appLabel(row.app)
                setTextColor(Ui.TEXT)
                textSize = 15f
            })
            block.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = getString(R.string.leaderboard_line, row.attempts)
                setTextColor(Ui.GREY)
                textSize = 12f
            })
            line.addView(block, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            // Square root: one app can be ten times the rest, and a linear bar
            // would squash everything else into a single pixel.
            val share = kotlin.math.sqrt(row.attempts.toDouble() / max)
            line.addView(View(this).apply {
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
                layoutParams = LinearLayout.LayoutParams(d((18 + 74 * share).toInt()), d(6))
                    .apply { marginStart = d(12) }
            })
            // Tapping a row reveals which ad servers that app talks to.
            line.setOnClickListener {
                buzz()
                val opening = expandedApp != row.app
                // Take down whatever was open, wherever it was, then put the
                // new panel directly under the row that was tapped. Repainting
                // the whole list instead meant every row was destroyed and
                // rebuilt — including the name of the app being opened, which
                // is why it blinked out and back.
                expandedCard?.let { leaderboardList.removeView(it) }
                expandedCard = null
                expandedApp = if (opening) row.app else null
                if (opening) {
                    val panel = networksPanel(row.app)
                    expandedCard = panel
                    leaderboardList.addView(
                        panel,
                        leaderboardList.indexOfChild(line) + 1,
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                            .apply { bottomMargin = d(10) }
                    )
                }
            }
            leaderboardList.addView(line)

            // Reinsere le panneau sous sa ligne. Sans ca, deplier une app puis
            // laisser le compteur bouger la refermait toute seule — et arriver
            // depuis l'accueil n'ouvrait jamais rien.
            if (expandedApp == row.app) {
                val panel = networksPanel(row.app)
                expandedCard = panel
                leaderboardList.addView(
                    panel,
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        .apply { bottomMargin = d(10) }
                )
                if (scrollToExpanded) {
                    scrollToExpanded = false
                    // Apres la passe de mesure : avant, la ligne n'a pas encore
                    // de position et on defilerait vers zero.
                    statsPage.post { scrollTo(line) }
                }
            }
        }
    }

    private fun paintCandidates() {
        val candidates = Learning.candidates()
        if (unchanged("candidates", candidates.joinToString { it.first + it.second })) return
        if (candidates.isEmpty()) {
            val granted = Attribution.usageAccessGranted(this)
            candidateHint.text =
                getString(if (granted) R.string.learned_empty else R.string.learned_need_usage)
            candidateHint.setOnClickListener {
                if (!granted) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            pauseAnimation(candidateList)
        candidateList.removeAllViews()
            return
        }
        candidateHint.text = getString(R.string.learned_hint)
        candidateHint.setOnClickListener(null)

        pauseAnimation(candidateList)
        candidateList.removeAllViews()
        for ((domain, appCount) in candidates) {
            val card = Ui.card(this).apply { orientation = LinearLayout.VERTICAL }
            val identity = Explain.cardFor(domain)

            // Lead with what it is, in words, exactly as the ad report does.
            // A hostname on its own is not something anyone can decide about.
            card.addView(TextView(this).apply {
                text = if (identity.owner.isNotEmpty()) identity.owner
                else Explain.kindLabel(this@MainActivity, identity.kind)
                setTextColor(Ui.TEXT)
                textSize = 15f
                typeface = Ui.BOLD
            })
            val witnesses = Learning.witnesses(domain)
                .map { appLabel(it) }.distinct().take(2).joinToString(", ")
            card.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = getString(R.string.learned_line, appCount, witnesses)
                setTextColor(Ui.GREY)
                textSize = 12f
            })
            card.addView(TextView(this).apply {
                text = domain
                setTextColor(Ui.DIM)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                isClickable = true
                setOnClickListener { buzz(); explainHost(domain) }
            })

            // Both answers, side by side. Offering only "block" made the list
            // grow for ever, because turning a suggestion down was impossible.
            card.addView(Ui.spacer(this@MainActivity, 12))
            val answers = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            answers.addView(TextView(this).apply {
                text = getString(R.string.action_block)
                setTextColor(Ui.BG_TOP)
                textSize = 12f
                typeface = Ui.BOLD
                gravity = Gravity.CENTER
                setPadding(d(14), d(10), d(14), d(10))
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
                setOnClickListener {
                    buzz()
                    AdNetworks.add(domain)
                    paintCandidates()
                    paintCustom()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            answers.addView(TextView(this).apply {
                text = getString(R.string.learned_ignore)
                setTextColor(Ui.GREY)
                textSize = 12f
                typeface = Ui.BOLD
                gravity = Gravity.CENTER
                setPadding(d(14), d(10), d(14), d(10))
                background = Ui.softPill(this@MainActivity)
                setOnClickListener {
                    tick()
                    Learning.ignore(domain)
                    Learning.invalidateUnseen()
                    // Removes this card only. Repainting the section meant
                    // rescanning the learning before anything moved on screen.
                    candidateList.removeView(card)
                    drawn.remove("candidates")
                    if (candidateList.childCount == 0) paintCandidates()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = d(10) })
            card.addView(answers, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            candidateList.addView(
                card,
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = d(8) }
            )
        }
    }

    /** Blocking used to be a one-way door. A heuristic can be wrong; so can a tap. */
    private fun paintCustom() {
        if (unchanged("custom", AdNetworks.customDomains().joinToString())) return
        val domains = AdNetworks.customDomains()
        pauseAnimation(customList)
        customList.removeAllViews()
        if (domains.isEmpty()) {
            customList.addView(Ui.body(this, getString(R.string.custom_empty))
                .apply { textSize = 12f })
            return
        }
        for (domain in domains) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(9), 0, d(9))
                setOnClickListener {
                    AdNetworks.remove(domain)
                    paintCustom()
                    paintCandidates()
                }
            }
            line.addView(TextView(this).apply {
                text = domain
                setTextColor(Ui.TEXT)
                textSize = 13f
                typeface = Typeface.MONOSPACE
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            line.addView(TextView(this).apply {
                text = getString(R.string.action_unblock)
                setTextColor(Ui.GREY)
                textSize = 11f
                typeface = Ui.BOLD
            })
            customList.addView(line)
        }
    }

    /**
     * What a server is, in words.
     *
     * A hostname on its own invites the worst reading: the first person
     * outside the project to use the app saw unity3d.com in the list and
     * concluded their game engine was spying on them. It was half right, and
     * the half that mattered was wrong.
     */
    /**
     * Le journal des cinq dernieres minutes, avec un coupable designe.
     *
     * Un seul ecran pour deux questions opposees. "Une pub est passee" cherche
     * ce qu'AdZero a laisse passer ; "un jeu ne marche plus" cherche ce qu'il
     * a fait taire. Meme donnee, meme presentation, score inverse — et une
     * seule action par ligne, celle qui a du sens dans ce sens-la.
     *
     * Le premier de la liste est mis en avant plutot que simplement premier :
     * une liste ou tout se ressemble redemande a l'utilisateur de faire le tri
     * qu'on venait de faire pour lui.
     */
    /** Rouvre le choix de l'app, meme quand une seule est candidate. */
    private fun showCulpritPicker(broken: Boolean) {
        forcePicker = true
        showCulprit(broken)
        forcePicker = false
    }

    private var forcePicker = false

    private fun showCulprit(broken: Boolean, app: String? = null) {
        // Les candidates : celles qu'on a fait taire pour un jeu casse, celles
        // qui ont parle pour une pub passee. Jamais un service du systeme.
        // Deux sources, et il faut les deux. Le journal ne couvre que cinq
        // minutes : une partie jouee il y a un quart d'heure en est deja
        // sortie, alors que c'est precisement le jeu auquel on pense. Le
        // classement, lui, est cumulatif — il garde la memoire des apps qui
        // servent des pubs, meme quand elles se sont tues depuis.
        //
        // Les recentes d'abord : ce sont les seules pour lesquelles il reste
        // quelque chose a analyser. Les autres sont proposees quand meme, et
        // l'ecran dira honnetement qu'il n'a rien vu.
        val recent = (if (broken) Recent.silencedApps(6) else Recent.activeApps(6))
        val known = Leaderboard.ranking(8).map { it.app }
        // Puis les apps vues dans la semaine : le jeu d'hier soir est celui
        // auquel on pense, et il n'a plus aucune requete dans le journal.
        val choices = (recent + known + Recent.playedApps(12))
            .filterNot { Shield.isSystemService(it) || it == "?" }
            .distinct()
            .take(8)
        val target = app ?: choices.firstOrNull()

        // L'utilisateur sait de quelle app il parle et nous non : plusieurs ont
        // pu etre actives pendant qu'il jouait. On devine, mais on laisse
        // toujours corriger.
        if (app == null && (choices.size > 1 || forcePicker) && choices.isNotEmpty()) {
            val pick = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(24), d(22), d(24), d(12))
            }
            pick.addView(TextView(this).apply {
                text = getString(R.string.culprit_pick_app)
                setTextColor(Ui.TEXT)
                textSize = 19f
                typeface = Ui.BOLD
            })
            pick.addView(Ui.spacer(this, 14))
            for (pkg in choices) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Ui.softPill(this@MainActivity)
                    setPadding(d(12), d(10), d(12), d(10))
                    isClickable = true
                    setOnClickListener {
                        buzz(); currentSheet?.dismiss(); showCulprit(broken, pkg)
                    }
                }
                AppsCatalog.iconFor(this, pkg)?.let { icon ->
                    row.addView(ImageView(this).apply {
                        setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
                        layoutParams = LinearLayout.LayoutParams(d(28), d(28))
                            .apply { marginEnd = d(12) }
                        Ui.circleClip(this)
                    })
                }
                row.addView(TextView(this).apply {
                    text = appLabel(pkg)
                    setTextColor(Ui.TEXT)
                    textSize = 15f
                    typeface = Ui.REGULAR
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                pick.addView(row)
                pick.addView(Ui.spacer(this, 6))
            }
            sheet(pick)
            return
        }

        val rows = when {
            target == null -> emptyList()
            broken -> Recent.breakers(target)
            else -> Recent.suspects(target)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(12))
        }
        box.addView(TextView(this).apply {
            text = getString(
                if (broken) R.string.culprit_broken_title else R.string.culprit_ad_title
            )
            setTextColor(Ui.TEXT)
            textSize = 19f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 6))
        box.addView(Ui.body(this, getString(
            if (broken) R.string.culprit_broken_intro else R.string.culprit_ad_intro
        )).apply { textSize = 13f })

        if (target != null) {
            box.addView(Ui.spacer(this, 10))
            box.addView(TextView(this).apply {
                // Cliquable, et il le montre : c'est une supposition, pas un
                // constat, et se tromper d'app rend tout l'ecran inutile.
                text = appLabel(target) + "  \u25BE"
                setTextColor(Ui.LIME_A)
                textSize = 13f
                typeface = Ui.BOLD
                setPadding(d(10), d(7), d(10), d(7))
                background = Ui.softPill(this@MainActivity)
                isClickable = true
                setOnClickListener {
                    buzz(); currentSheet?.dismiss(); showCulpritPicker(broken)
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        box.addView(Ui.spacer(this, 16))

        if (rows.isEmpty()) {
            box.addView(Ui.body(this, getString(R.string.culprit_none)))
            sheet(box)
            return
        }

        for ((index, suspect) in rows.withIndex()) {
            box.addView(culpritRow(suspect, broken, index == 0))
            box.addView(Ui.spacer(this, 8))
        }
        sheet(box)
    }

    /** Une ligne du journal : le domaine, pourquoi il est suspect, et le geste. */
    private fun culpritRow(
        suspect: Recent.Suspect, broken: Boolean, leading: Boolean,
    ): LinearLayout {
        val card = Explain.cardFor(suspect.domain)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.softPill(this@MainActivity)
            setPadding(d(14), d(12), d(14), d(12))
        }
        if (leading) {
            box.addView(TextView(this).apply {
                text = getString(R.string.culprit_likely)
                setTextColor(Ui.LIME_A)
                textSize = 10f
                letterSpacing = 0.16f
                typeface = Ui.BOLD
            })
            box.addView(Ui.spacer(this@MainActivity, 6))
        }
        box.addView(TextView(this).apply {
            text = suspect.domain
            setTextColor(Ui.TEXT)
            textSize = 14f
            typeface = Typeface.MONOSPACE
        })
        val who = if (card.owner.isNotEmpty()) card.owner
        else Explain.kindLabel(this, card.kind)
        box.addView(TextView(this).apply {
            text = who + "  ·  " + getString(R.string.culprit_seen, suspect.hits)
            setTextColor(Ui.GREY)
            textSize = 12f
            setPadding(0, d(3), 0, 0)
        })
        for (reason in suspect.reasons.take(2)) {
            box.addView(TextView(this).apply {
                text = if (reason.arg != null) getString(reason.text, reason.arg)
                else getString(reason.text)
                setTextColor(Ui.DIM)
                textSize = 11f
                setPadding(0, d(4), 0, 0)
            })
        }
        box.addView(Ui.spacer(this, 10))
        box.addView(TextView(this).apply {
            text = getString(if (broken) R.string.culprit_allow else R.string.culprit_block)
            setTextColor(Ui.BG_TOP)
            textSize = 12f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(10), 0, d(10))
            background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
            setOnClickListener {
                buzz()
                if (broken) AdNetworks.allow(suspect.domain)
                else AdNetworks.add(suspect.domain)
                drawn.clear()
                paint()
                // Le panneau se referme : le geste est fait, et laisser la
                // liste ouverte invite a relacher un domaine de plus "au cas
                // ou", ce qui est exactement comment on casse la protection.
                currentSheet?.dismiss()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return box
    }

    private fun explainHost(host: String) {
        val card = Explain.cardFor(host)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = host
            setTextColor(Ui.TEXT)
            textSize = 16f
            typeface = Typeface.MONOSPACE
        })
        if (card.owner.isNotEmpty()) {
            box.addView(Ui.spacer(this@MainActivity, 6))
            box.addView(TextView(this).apply {
                text = card.owner
                setTextColor(Ui.TEXT)
                textSize = 20f
                typeface = Ui.BOLD
            })
        }
        val level = Explain.levelOf(card.kind)
        val colour = Explain.levelColour(level)

        // Three segments, the way a food-scanning app does it: the answer is
        // the colour, and you have already got it before reading a word.
        box.addView(Ui.spacer(this, 16))
        val gauge = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..2) {
            val lit = i <= level.ordinal
            gauge.addView(View(this).apply {
                background = Ui.buttonBackground(this@MainActivity, if (lit) colour else Ui.IDLE_B)
                    .apply { cornerRadius = d(3).toFloat() }
                layoutParams = LinearLayout.LayoutParams(0, d(6), 1f)
                    .apply { if (i > 0) marginStart = d(6) }
            })
        }
        box.addView(gauge)

        box.addView(Ui.spacer(this, 12))
        box.addView(TextView(this).apply {
            text = Explain.levelLabel(this@MainActivity, level)
            setTextColor(colour)
            textSize = 17f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 4))
        box.addView(Ui.body(this, Explain.levelText(this, level)).apply { setTextColor(Ui.TEXT) })

        box.addView(Ui.spacer(this, 18))
        box.addView(TextView(this).apply {
            text = Explain.kindLabel(this@MainActivity, card.kind).uppercase()
            setTextColor(Ui.DIM)
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 8))
        box.addView(Ui.body(this, Explain.kindText(this, card.kind)))
        // Who owns it, on paper. The rest of the card explains behaviour; this
        // is the part that makes the answer checkable by somebody who doubts
        // it — a legal name and a head office can be looked up.
        Companies.forHost(host)?.let { firm ->
            box.addView(Ui.spacer(this, 18))
            box.addView(TextView(this).apply {
                text = getString(R.string.firm_section).uppercase()
                setTextColor(Ui.DIM)
                textSize = 11f
                letterSpacing = 0.16f
                typeface = Ui.BOLD
            })
            box.addView(Ui.spacer(this, 8))
            box.addView(TextView(this).apply {
                text = firm.legal
                setTextColor(Ui.TEXT)
                textSize = 15f
                typeface = Ui.BOLD
            })
            box.addView(firmLine(getString(R.string.firm_home), firm.home))
            firm.listing?.let { box.addView(firmLine(getString(R.string.firm_listed), it)) }
            firm.parent?.let { box.addView(firmLine(getString(R.string.firm_parent), it)) }
        }

        box.addView(Ui.spacer(this, 16))
        box.addView(TextView(this).apply {
            text = getString(
                if (card.blocked) R.string.explain_blocked else R.string.explain_allowed
            )
            setTextColor(if (card.blocked) Ui.LIME_A else Ui.GREY)
            textSize = 13f
            typeface = Ui.BOLD
        })
        sheet(box)
    }

    /** One "label — value" line of the company block. */
    private fun firmLine(label: String, value: String) = TextView(this).apply {
        typeface = Ui.REGULAR
        text = android.text.SpannableString("$label  $value").apply {
            setSpan(
                android.text.style.ForegroundColorSpan(Ui.DIM),
                0, label.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        setTextColor(Ui.GREY)
        textSize = 13f
        setPadding(0, d(4), 0, 0)
    }

    private fun explainNumber() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(24), d(22), d(24), d(8))
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.ads_caption)
            setTextColor(Ui.TEXT)
            textSize = 20f
            typeface = Ui.BOLD
        })
        box.addView(Ui.spacer(this, 12))
        box.addView(Ui.body(this, getString(R.string.ads_explained)))
        sheet(box)
    }

    private var currentSheet: android.app.Dialog? = null

    private fun sheet(content: View) {
        val cap = (resources.displayMetrics.heightPixels * 0.70f).toInt()
        val holder = object : LinearLayout(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                super.onMeasure(
                    widthSpec,
                    MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
                )
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.PANEL_TOP, Ui.PANEL_BOTTOM)
            ).apply {
                cornerRadius = d(20).toFloat()
                setStroke(d(1), Ui.BORDER)
            }
            // Le contenu defile, le bouton de fermeture non.
            //
            // Aucune de ces popups n'etait defilante : tant qu'elles tenaient
            // en trois lignes personne ne s'en apercevait, mais des qu'une
            // liste s'y installe, le bas devient inatteignable — et c'est
            // precisement au bas d'une liste de suspects que se trouve ce
            // qu'on cherchait.
            //
            // Plafonnee a 70 % de l'ecran : au-dela, le panneau couvre tout et
            // on ne sait plus qu'on peut le fermer.
            addView(
                ScrollView(this@MainActivity).apply {
                    addView(content)
                    isVerticalScrollBarEnabled = false
                    // Sans ca, un ScrollView dans un Dialog s'etire a la
                    // hauteur de son contenu et annule tout l'interet.
                    isFillViewport = false
                },
                LinearLayout.LayoutParams(
                    MATCH_PARENT, WRAP_CONTENT
                ).apply {
                    weight = 1f
                }
            )
        }
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        holder.addView(TextView(this).apply {
            text = getString(R.string.explain_close)
            setTextColor(Ui.LIME_A)
            textSize = 14f
            typeface = Ui.BOLD
            gravity = Gravity.CENTER
            setPadding(0, d(14), 0, d(18))
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        dialog.setContentView(holder)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WRAP_CONTENT
            )
        }
        dialog.show()
        currentSheet = dialog
    }

    /**
     * What the shield silenced on its own judgement, and a way to undo it.
     *
     * The shield blocks names no list ever vetted. That is its whole value, and
     * also the one thing that could make it break a game, so what it decided
     * has to be visible and reversible in one tap.
     */
    private fun paintShield() {
        if (unchanged("shield", Shield.catches().joinToString { it.root + it.rule })) return
        pauseAnimation(shieldList)
        shieldList.removeAllViews()
        val caught = Shield.catches()
        if (caught.isEmpty()) {
            shieldList.addView(Ui.body(this, getString(R.string.shield_empty)))
            return
        }
        shieldList.addView(Ui.body(this, getString(R.string.shield_caught_hint)).apply {
            textSize = 12f
            setTextColor(Ui.DIM)
        })
        shieldList.addView(Ui.spacer(this, 8))
        for (entry in caught.take(10)) {
            val domain = entry.root
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(8), 0, d(8))
            }
            val label = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                setOnClickListener { buzz(); explainHost(domain) }
            }
            label.addView(TextView(this).apply {
                text = domain
                setTextColor(Ui.TEXT)
                textSize = 13f
                typeface = Typeface.MONOSPACE
            })
            // Which rule caught it. The two fail differently, so a list that
            // mixes them silently is much harder to act on when a game breaks.
            label.addView(TextView(this).apply {
                typeface = Ui.REGULAR
                text = getString(
                    if (entry.rule == Shield.Rule.SHAPE) R.string.shield_by_shape
                    else R.string.shield_by_name
                )
                setTextColor(Ui.DIM)
                textSize = 11f
            })
            row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            val button = TextView(this).apply {
                text = getString(R.string.broken_unblock)
                setTextColor(Ui.GREY)
                textSize = 11f
                typeface = Ui.BOLD
                setPadding(d(14), d(8), d(14), d(8))
                background = Ui.softPill(this@MainActivity)
            }
            button.setOnClickListener {
                buzz()
                AdNetworks.allow(domain)
                button.text = getString(R.string.broken_unblocked)
                button.setTextColor(Ui.DIM)
                button.isClickable = false
            }
            row.addView(button)
            shieldList.addView(row)
        }
    }

    private fun paintDomains() {
        val ranking = Stats.ranking()
        if (unchanged("domains", ranking.joinToString { it.first + it.second })) return
        pauseAnimation(domainList)
        domainList.removeAllViews()
        if (ranking.isEmpty()) {
            domainList.addView(Ui.body(this, getString(R.string.domains_empty)))
            return
        }
        val max = ranking.first().second.coerceAtLeast(1)
        for ((domain, n) in ranking) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, d(7), 0, d(7))
                isClickable = true
                setOnClickListener { buzz(); explainHost(domain) }
            }
            line.addView(TextView(this).apply {
                text = domain
                setTextColor(Ui.TEXT)
                textSize = 13f
                typeface = Typeface.MONOSPACE
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            val share = kotlin.math.sqrt(n.toDouble() / max)
            line.addView(View(this).apply {
                background = Ui.gradientPill(this@MainActivity, Ui.LIME_A, Ui.LIME_B)
                layoutParams = LinearLayout.LayoutParams(d((14 + 56 * share).toInt()), d(5))
                    .apply { marginStart = d(10) }
            })
            line.addView(TextView(this).apply {
                text = "  $n"
                setTextColor(Ui.GREY)
                textSize = 12f
                typeface = Typeface.MONOSPACE
            })
            domainList.addView(line)
        }
    }
}
