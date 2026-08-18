package com.adzero.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Le bouton "Exclure" de la notification.
 *
 * Il fait le travail sans ouvrir AdZero, et c'est tout l'interet : la personne
 * qui recoit ce message ne cherchait pas un reglage, elle voulait juste que
 * l'app de sa voiture remarche. Lui demander d'ouvrir un bloqueur de
 * publicites, de trouver la liste des apps et d'y decocher la bonne ligne,
 * c'est la garantie qu'elle desinstallera AdZero a la place.
 *
 * Non exporte. Un recepteur exporte aurait laisse n'importe quelle app du
 * telephone se retirer elle-meme du tunnel en diffusant cette action — soit
 * exactement ce qu'un SDK publicitaire voudrait faire. La notification porte
 * un PendingIntent construit par AdZero, qui vaut son identite, donc fermer la
 * porte ne coute rien au bouton.
 */
class ExcludeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXCLUDE = "com.adzero.app.EXCLUDE_APP"
        const val EXTRA_PKG = "pkg"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_EXCLUDE) return
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return

        AppFilter.init(ctx)
        if (!AppFilter.isExcluded(pkg)) AppFilter.toggle(pkg)

        ctx.getSystemService(NotificationManager::class.java)?.cancel(pkg.hashCode())

        // La liste des apps est gravee dans le tunnel a sa creation : elle ne
        // change qu'au redemarrage. Sans ca, l'exclusion serait enregistree et
        // sans effet jusqu'a la prochaine extinction — donc l'app resterait
        // cassee, apres un message promettant le contraire.
        if (Stats.running) {
            ctx.startService(
                Intent(ctx, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_STOP)
            )
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val i = Intent(ctx, SilenceVpnService::class.java)
                    .setAction(SilenceVpnService.ACTION_START)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(i)
                else ctx.startService(i)
            }, 400)
        }
    }
}
