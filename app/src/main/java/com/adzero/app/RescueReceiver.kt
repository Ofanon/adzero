package com.adzero.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Les boutons de l'alerte "cette app ne marche pas avec AdZero".
 *
 * Ils agissent sans ouvrir AdZero, et c'est tout l'interet : la personne qui
 * recoit ce message ne cherchait pas un reglage, elle voulait que l'app de sa
 * voiture remarche. Lui demander d'ouvrir un bloqueur de publicites, de
 * trouver la liste des apps et d'y decocher la bonne ligne, c'est la garantie
 * qu'elle desinstallera AdZero a la place.
 *
 * Deux boutons, parce qu'il y a deux sortes de detection et qu'on ne peut pas
 * savoir a l'avance laquelle une app utilise :
 *
 *  - certaines regardent par ou passe leur trafic. Les sortir du tunnel suffit,
 *    et la protection continue partout ailleurs. C'est le cas favorable ;
 *  - d'autres cherchent l'interface tun0, qu'Android montre a tout le monde.
 *    Celles-la voient AdZero meme exclues — l'app Toyota s'est revelee etre de
 *    cette famille — et rien ne peut les convaincre tant que le tunnel existe.
 *    La seule reponse honnete est de couper, brievement.
 *
 * La pause deja presente dans AdZero ne convient pas au second cas : elle
 * laisse le tunnel debout et se contente de tout laisser passer, donc tun0
 * reste visible et l'app refuse toujours. Il faut reellement arreter le
 * service, puis le rallumer tout seul — quelqu'un qui coupe sa protection pour
 * depanner une app ne pensera pas a la remettre.
 */
class RescueReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXCLUDE = "com.adzero.app.EXCLUDE_APP"
        const val ACTION_STEP_ASIDE = "com.adzero.app.STEP_ASIDE"
        const val ACTION_RESUME = "com.adzero.app.RESUME_PROTECTION"
        const val EXTRA_PKG = "pkg"

        /** Assez pour ouvrir une app et faire ce qu'on avait a y faire. */
        const val ASIDE_MINUTES = 15

        private const val ID_ASIDE = 4300
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EXCLUDE -> exclude(ctx, intent.getStringExtra(EXTRA_PKG) ?: return)
            ACTION_STEP_ASIDE -> stepAside(ctx)
            ACTION_RESUME -> resume(ctx)
        }
    }

    private fun exclude(ctx: Context, pkg: String) {
        AppFilter.init(ctx)
        if (!AppFilter.isExcluded(pkg)) AppFilter.toggle(pkg)
        ctx.getSystemService(NotificationManager::class.java)?.cancel(pkg.hashCode())
        // La liste des apps est gravee dans le tunnel a sa creation : elle ne
        // change qu'au redemarrage. Sans ca, l'exclusion serait enregistree et
        // sans effet jusqu'a la prochaine extinction.
        if (Stats.running) restart(ctx)
    }

    /**
     * Coupe vraiment, et programme le retour.
     *
     * L'alarme est volontairement inexacte : quelques minutes de derive sur le
     * retour de la protection ne se voient pas, alors qu'une alarme exacte
     * demande une permission que le systeme surveille et qu'il faudrait
     * justifier.
     */
    private fun stepAside(ctx: Context) {
        ctx.startService(
            Intent(ctx, SilenceVpnService::class.java)
                .setAction(SilenceVpnService.ACTION_STOP)
        )

        val back = PendingIntent.getBroadcast(
            ctx, 43,
            Intent(ctx, RescueReceiver::class.java).setAction(ACTION_RESUME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = System.currentTimeMillis() + ASIDE_MINUTES * 60_000L
        try {
            ctx.getSystemService(AlarmManager::class.java)
                ?.set(AlarmManager.RTC_WAKEUP, at, back)
        } catch (_: Exception) {
        }

        // Un ecran sans protection doit le dire. Silencieuse et permanente : ce
        // n'est pas une nouvelle, c'est un etat.
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            ID_ASIDE,
            Notification.Builder(ctx, "adzero.incompatible")
                .setContentTitle(ctx.getString(R.string.aside_title, ASIDE_MINUTES))
                .setContentText(ctx.getString(R.string.aside_body))
                .setSmallIcon(R.drawable.ic_mark)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(
                        null, ctx.getString(R.string.aside_now),
                        PendingIntent.getBroadcast(
                            ctx, 44,
                            Intent(ctx, RescueReceiver::class.java).setAction(ACTION_RESUME),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
                .build()
        )
    }

    private fun resume(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_ASIDE)
        restart(ctx)
    }

    private fun restart(ctx: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val i = Intent(ctx, SilenceVpnService::class.java)
                .setAction(SilenceVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i)
            else ctx.startService(i)
        }, 400)
    }
}
