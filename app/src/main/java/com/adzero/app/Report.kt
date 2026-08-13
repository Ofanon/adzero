package com.adzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Blocking an ad that got through, without leaving the game.
 *
 * The first version of this opened the app. That is the wrong shape: the ad
 * appears in the middle of a level, and anything that takes you out of the
 * game either does not get used or costs you the round. So the whole exchange
 * happens in the notification shade — the verdict, the reason, and the button.
 *
 * The app is still there for anyone who wants to see the other candidates, but
 * it is the second option now, not the only one.
 */
object Report {

    const val CHANNEL = "adzero_report"
    const val ID = 2

    /** Remembered between the notification being posted and its button being hit. */
    @Volatile private var offered: String? = null

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                ctx.getString(R.string.report_channel),
                // The user asked for this one by tapping, so it may interrupt.
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /**
     * Answers a report request: names the likeliest culprit and offers to
     * block it. Posted even when nothing was found, because silence after a
     * deliberate tap reads as a broken app.
     */
    fun offer(ctx: Context) {
        channel(ctx)
        val manager = ctx.getSystemService(NotificationManager::class.java)
        val suspect = Recent.suspects(Recent.busiestApp(), max = 1).firstOrNull()

        if (suspect == null) {
            manager.notify(ID, plain(ctx, ctx.getString(R.string.report_title),
                ctx.getString(R.string.report_empty)))
            return
        }

        offered = suspect.domain
        val card = Explain.cardFor(suspect.domain)
        val verdict = ctx.getString(
            when {
                // A domain we can name is a domain the user should not be
                // guessing about. Unity's own servers turn up in this list all
                // the time, and blocking them breaks the game.
                card.kind == Explain.Kind.ENGINE -> R.string.verdict_known_safe
                suspect.confident -> R.string.verdict_high
                suspect.plausible -> R.string.verdict_medium
                else -> R.string.verdict_low
            }
        )
        val reason = ctx.getString(suspect.reasons.first().text, suspect.reasons.first().arg)

        val notice = Notification.Builder(ctx, CHANNEL)
            .setContentTitle(verdict)
            .setContentText(reason)
            .setStyle(Notification.BigTextStyle().bigText("$reason\n\n${suspect.domain}"))
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .addAction(
                Notification.Action.Builder(
                    null, ctx.getString(R.string.report_block),
                    service(ctx, SilenceVpnService.ACTION_BLOCK_OFFERED, 20)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null, ctx.getString(R.string.report_others), openApp(ctx)
                ).build()
            )
            .build()
        manager.notify(ID, notice)
    }

    /** Blocks what was offered, and says so in place of the question. */
    fun blockOffered(ctx: Context) {
        val domain = offered ?: return
        AdNetworks.add(domain)
        offered = null
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.notify(ID, plain(
            ctx,
            ctx.getString(R.string.report_done_title),
            ctx.getString(R.string.report_done_body, domain)
        ))
    }

    private fun plain(ctx: Context, title: String, body: String): Notification =
        Notification.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 21,
        Intent(ctx, MainActivity::class.java)
            .setAction(MainActivity.ACTION_REPORT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun service(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            ctx, code,
            Intent(ctx, SilenceVpnService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
