package com.adzero.app

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile: start and stop without opening the app.
 * This is the "one tap" the whole idea started from.
 */
class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Stats.init(this)
        paint()
    }

    override fun onClick() {
        super.onClick()

        if (Stats.running) {
            startService(
                Intent(this, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_STOP)
            )
            paint()
            return
        }

        // Without prior VPN consent nothing can be started from a tile:
        // Android requires the user to see the request.
        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }

        val i = Intent(this, SilenceVpnService::class.java)
            .setAction(SilenceVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
        paint()
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    private fun paint() {
        val tile: Tile = qsTile ?: return
        val on = Stats.running
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }
}
