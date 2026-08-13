package com.adzero.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

/**
 * Brings protection back after a restart.
 *
 * Without this, rebooting silently leaves the phone unprotected — and nobody
 * thinks to check. We only restart if protection was on when the phone went
 * down, so it never turns itself on against the user's wishes.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        // Any of the boot variants. Testing only for BOOT_COMPLETED meant
        // protection never returned on the phones whose makers send their own
        // fast-boot action instead — and nobody would ever connect the two.
        if (intent.action !in BOOT_ACTIONS) return

        Stats.init(ctx)
        if (!Stats.running) return
        // Consent survives reboots; if it somehow does not, we cannot ask for
        // it from a receiver, so we simply stay off.
        if (VpnService.prepare(ctx) != null) {
            Stats.markRunning(false)
            return
        }

        val start = Intent(ctx, SilenceVpnService::class.java)
            .setAction(SilenceVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(start)
        else ctx.startService(start)
    }
}
