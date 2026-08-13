package com.adzero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

/**
 * The widget's on/off button, kept out of reach of everything else on the phone.
 *
 * This used to live in [AdZeroWidget], which has to be exported — the system
 * sends APPWIDGET_UPDATE to it from outside the app, and a widget that refuses
 * that never draws. But an exported receiver accepts its other actions from
 * outside too, and anything installed on the device could aim a broadcast at
 * com.adzero.app.WIDGET_TOGGLE and switch protection off. Verified: a broadcast
 * sent from a shell, with no permission of any kind, toggled the service.
 *
 * That is the single thing an adversary here would want, and the adversary is
 * not hypothetical — the ad SDKs AdZero silences run inside other apps on the
 * same phone.
 *
 * A separate receiver can be exported="false" without breaking the widget: the
 * launcher does not send this broadcast itself, it fires a PendingIntent that
 * AdZero created, and a PendingIntent is delivered with the identity of the app
 * that made it. So the button still works, and only the button does.
 */
class ToggleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.adzero.app.WIDGET_TOGGLE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return

        Stats.init(ctx)
        if (Stats.running) {
            ctx.startService(
                Intent(ctx, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_STOP)
            )
        } else if (VpnService.prepare(ctx) != null) {
            // Consent has never been given: only the app can ask for it.
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            val start = Intent(ctx, SilenceVpnService::class.java)
                .setAction(SilenceVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(start)
            else ctx.startService(start)
        }
        AdZeroWidget.refresh(ctx)
    }
}
