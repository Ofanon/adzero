package com.adzero.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: the ad count, and one button to turn protection on or off.
 *
 * Better than a floating bubble for the same job — it is there when you want
 * it, gone when you don't, and it needs no permission to draw over other apps.
 */
class AdZeroWidget : AppWidgetProvider() {

    companion object {
        /** Called by the service whenever the state or the count changes. */
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, AdZeroWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) manager.updateAppWidget(id, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            Stats.init(ctx)
            Leaderboard.init(ctx)
            val on = Stats.running

            // Every word comes from here, resolved against the language chosen
            // inside AdZero. The layout carries no text at all: a widget is
            // inflated by the launcher, which would resolve a @string with its
            // own configuration — the system language, whatever the app is set
            // to. That is why the widget stayed in one language.
            val words = Locales.wrap(ctx)

            val ads = Leaderboard.totalAttempts()
            val seconds = ads * Leaderboard.SECONDS_PER_AD

            return RemoteViews(ctx.packageName, R.layout.widget_adzero).apply {
                setTextViewText(R.id.widget_count, ads.toString())
                // What the count bought. Formatted here rather than in the
                // layout because a widget cannot compute anything itself.
                setTextViewText(R.id.widget_label, words.getString(R.string.stat_ads))
                setTextViewText(R.id.widget_time, duration(seconds))
                setTextViewText(
                    R.id.widget_time_label, words.getString(R.string.stat_time)
                )
                setTextViewText(R.id.widget_data, data(ads * 2.0))
                setTextViewText(
                    R.id.widget_data_label, words.getString(R.string.card_data)
                )
                setTextViewText(
                    R.id.widget_button,
                    words.getString(if (on) R.string.state_on else R.string.state_off)
                )
                setInt(
                    R.id.widget_button, "setBackgroundResource",
                    if (on) R.drawable.widget_pill_on else R.drawable.widget_pill_off
                )
                setTextColor(
                    R.id.widget_button,
                    if (on) 0xFF0A0C08.toInt() else 0xFF8A9382.toInt()
                )
                setOnClickPendingIntent(R.id.widget_button, togglePending(ctx))
                // Tapping the number opens the app.
                setOnClickPendingIntent(
                    R.id.widget_count,
                    PendingIntent.getActivity(
                        ctx, 1, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        private fun duration(seconds: Int): String = when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h"
        }

        private fun data(megabytes: Double): String =
            if (megabytes >= 1024) String.format("%.1f Go", megabytes / 1024)
            else String.format("%.0f Mo", megabytes)

        private fun togglePending(ctx: Context): PendingIntent {
            // Aimed at a receiver nothing else on the phone can reach. See
            // ToggleReceiver for why that is not the same as unreachable.
            val intent = Intent(ctx, ToggleReceiver::class.java)
                .setAction(ToggleReceiver.ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(ctx))
    }
}
