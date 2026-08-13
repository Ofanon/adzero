package com.adzero.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.net.VpnService
import android.provider.Settings

/**
 * What AdZero still needs, and how to ask for it.
 *
 * Each permission is optional on its own — the app works without the alerts
 * and without usage access — so the setup card lists what is missing and lets
 * the user grant it in one tap, rather than blocking anything behind a wall.
 */
object Permissions {

    enum class Item { VPN, NOTICES, BATTERY, ALERTS, USAGE }

    /**
     * Whether Android has agreed to stop putting AdZero to sleep.
     *
     * Left alone, the system suspends background work to save power, and on
     * several makes of phone it stops the tunnel outright after a few hours.
     * Protection then ends without a word, which is the failure nobody
     * notices. Asking for the exemption is the standard remedy for a VPN app,
     * and the reason this one asks for it at setup rather than never.
     */
    fun batteryExempt(ctx: Context): Boolean {
        val power = ctx.getSystemService(android.os.PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * Notifications carry the ad report, so refusing them removes a feature
     * rather than just some noise. Asked for in the setup, before the ongoing
     * notice is ever posted — a permission requested out of the blue, weeks
     * later, is a permission that gets denied.
     */
    fun noticesGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    fun missing(ctx: Context): List<Item> {
        val out = ArrayList<Item>(3)
        if (VpnService.prepare(ctx) != null) out.add(Item.VPN)
        if (!noticesGranted(ctx)) out.add(Item.NOTICES)
        if (!batteryExempt(ctx)) out.add(Item.BATTERY)
        if (!Banner.allowed(ctx)) out.add(Item.ALERTS)
        if (!Attribution.usageAccessGranted(ctx)) out.add(Item.USAGE)
        return out
    }

    /**
     * Whether Android hides this permission behind "restricted settings".
     *
     * Since Android 13 a phone refuses to let an app installed from outside a
     * store take the two permissions most useful to malware — drawing over
     * other apps, and reading usage. The switch is there but greyed out, with
     * nothing on screen explaining why, and the way through is three taps into
     * a menu nobody opens.
     *
     * AdZero will never be on a store, so this is every user, every time.
     */
    fun restricted(item: Item): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (item == Item.ALERTS || item == Item.USAGE)

    fun titleOf(item: Item): Int = when (item) {
        Item.VPN -> R.string.setup_vpn
        Item.NOTICES -> R.string.setup_notices
        Item.BATTERY -> R.string.setup_battery
        Item.ALERTS -> R.string.setup_alerts
        Item.USAGE -> R.string.setup_usage
    }

    fun detailOf(item: Item): Int = when (item) {
        Item.VPN -> R.string.setup_vpn_why
        Item.NOTICES -> R.string.setup_notices_why
        Item.BATTERY -> R.string.setup_battery_why
        Item.ALERTS -> R.string.setup_alerts_why
        Item.USAGE -> R.string.setup_usage_why
    }

    /** The intent that opens the right settings page. Null for the two that
     *  do not use one: the VPN consent goes through startActivityForResult,
     *  and notifications through the runtime permission dialog. */
    fun intentFor(ctx: Context, item: Item): Intent? = when (item) {
        Item.VPN -> VpnService.prepare(ctx)
        Item.NOTICES -> null
        @android.annotation.SuppressLint("BatteryLife")
        Item.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        )
        Item.ALERTS -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )
        Item.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
}
