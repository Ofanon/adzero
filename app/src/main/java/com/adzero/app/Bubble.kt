package com.adzero.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import kotlin.math.abs

/**
 * Drives the bubble drawn on top of other apps.
 *
 * Two window sizes: collapsed it takes about the room of a coin, expanded it
 * shows the latest domains. Drag to move it, tap to switch between the two.
 */
object Bubble {

    private var view: BubbleView? = null
    private var wm: WindowManager? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastWake = 0L

    private const val COLLAPSED = 108   // dp
    private const val EXPANDED_W = 260
    private const val EXPANDED_H = 200

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)

    fun visible(): Boolean = view != null

    fun show(ctx: Context) {
        if (!allowed(ctx) || view != null) return
        main.post {
            if (view != null) return@post
            val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = BubbleView(ctx)
            val params = layout(ctx, COLLAPSED, COLLAPSED)
            params.gravity = Gravity.TOP or Gravity.START
            params.x = Ui.dp(ctx, 12)
            params.y = Ui.dp(ctx, 160)

            attachGestures(ctx, v, params, manager)
            manager.addView(v, params)

            wm = manager
            view = v

            // A game can trigger dozens of silences per second: throttle the
            // wake-ups, otherwise we flood the main thread for nothing.
            Stats.onSilenced = {
                val t = System.currentTimeMillis()
                if (t - lastWake > 120) {
                    lastWake = t
                    main.post { refresh() }
                }
            }
        }
    }

    fun hide() {
        main.post {
            Stats.onSilenced = null
            view?.let { v -> try { wm?.removeView(v) } catch (_: Exception) {} }
            view = null
            wm = null
        }
    }

    private fun refresh() {
        val v = view ?: return
        v.count = Stats.silenced.get()
        v.domains = Stats.ranking(5)
        v.pulse()
    }

    private fun layout(ctx: Context, w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            Ui.dp(ctx, w), Ui.dp(ctx, h), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun attachGestures(
        ctx: Context,
        v: BubbleView,
        params: WindowManager.LayoutParams,
        manager: WindowManager,
    ) {
        var startX = 0
        var startY = 0
        var fingerX = 0f
        var fingerY = 0f
        var dragged = false

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    fingerX = e.rawX; fingerY = e.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - fingerX).toInt()
                    val dy = (e.rawY - fingerY).toInt()
                    // Below this threshold it is a tap, not a drag.
                    if (abs(dx) > 12 || abs(dy) > 12) dragged = true
                    if (dragged) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try { manager.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) toggle(ctx, v, params, manager)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle(
        ctx: Context,
        v: BubbleView,
        params: WindowManager.LayoutParams,
        manager: WindowManager,
    ) {
        v.expanded = !v.expanded
        v.domains = Stats.ranking(5)
        v.count = Stats.silenced.get()
        params.width = Ui.dp(ctx, if (v.expanded) EXPANDED_W else COLLAPSED)
        params.height = Ui.dp(ctx, if (v.expanded) EXPANDED_H else COLLAPSED)
        try { manager.updateViewLayout(v, params) } catch (_: Exception) {}
        v.invalidate()
    }
}
