package com.adzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Le rapport de fin de partie.
 *
 * Tout le reste de l'app attend qu'on vienne la consulter. Un compteur ne dit
 * rien tant que personne ne l'ouvre, et personne n'ouvre un bloqueur de
 * publicites : quand il fait son travail, il ne se passe rien, et c'est
 * precisement le probleme — un service invisible finit desinstalle.
 *
 * Celui-ci parle tout seul, une fois, au seul moment ou le chiffre a du sens :
 * quand on ferme le jeu. "Vingt-trois pubs bloquees pendant tes quarante-deux
 * minutes" arrive quand on se souvient encore de la partie, pas trois jours
 * plus tard dans un onglet de statistiques.
 *
 * Trois garde-fous, parce qu'une notification de trop est une notification
 * desactivee pour toujours :
 *
 *  - une partie ne compte qu'a partir de [MIN_ADS] publicites bloquees, sinon
 *    l'app se felicite de n'avoir rien fait ;
 *  - et de [MIN_MINUTES] minutes, sinon ouvrir un jeu par erreur en declenche
 *    une ;
 *  - une seule par heure et par app, pour que reprendre une partie apres une
 *    pause ne produise pas une deuxieme notification identique.
 */
object Session {

    /**
     * Son propre canal, et c'est necessaire.
     *
     * Le canal du tunnel est en IMPORTANCE_LOW parce qu'une notification
     * permanente qui sonnerait a chaque demarrage serait insupportable. Mais
     * un rapport de fin de partie herite alors de la meme discretion : il
     * atterrit dans les "silencieuses", sans son ni banniere, c'est-a-dire la
     * ou personne ne le voit.
     *
     * Android fige l'importance d'un canal a sa creation : impossible de la
     * relever ensuite. Il en faut donc un second, et un seul reglage Android
     * peut alors couper les rapports sans toucher a la notification du VPN.
     */
    private const val CHANNEL = "adzero.session"
    private const val ID = 4102

    /** En dessous, il n'y a rien a annoncer. */
    private const val MIN_ADS = 3
    private const val MIN_MINUTES = 2

    /**
     * Une partie s'arrete quand l'app se tait, pas quand elle passe en arriere
     * plan : un jeu qui charge une pub peut rester silencieux dix secondes, et
     * couper la session la-dessus aurait decoupe une partie en morceaux.
     */
    private const val QUIET_MS = 90_000L

    private const val COOLDOWN_MS = 60 * 60_000L

    private var app: String? = null
    private var startedAt = 0L
    private var lastSeenAt = 0L
    private var ads = 0
    private val lastReport = HashMap<String, Long>()

    /**
     * Appele pour chaque requete, avec l'app qui l'a posee.
     *
     * C'est la boucle du tunnel qui alimente ceci, donc le suivi ne coute
     * aucun reveil : on sait qu'une app est vivante parce qu'elle parle.
     */
    @Synchronized
    fun note(ctx: Context, who: String, blockedAd: Boolean) {
        if (who == "?" || who == "com.adzero.app") return
        if (Shield.isSystemService(who)) return

        val now = System.currentTimeMillis()

        // L'app a change, ou l'ancienne s'est tue assez longtemps : la partie
        // precedente est finie.
        if (app != null && (app != who || now - lastSeenAt > QUIET_MS)) {
            close(ctx, now)
        }

        if (app == null) {
            app = who
            startedAt = now
            ads = 0
        }
        lastSeenAt = now
        if (blockedAd) ads++
    }

    /**
     * Ferme une partie restee ouverte.
     *
     * Appele quand la protection s'arrete : sans ca, une partie en cours au
     * moment ou l'utilisateur coupe le VPN ne serait jamais racontee.
     */
    @Synchronized
    fun flush(ctx: Context) = close(ctx, System.currentTimeMillis())

    private fun close(ctx: Context, now: Long) {
        val who = app ?: return
        val minutes = ((lastSeenAt - startedAt) / 60_000L).toInt()
        val blocked = ads
        app = null
        ads = 0

        if (blocked < MIN_ADS || minutes < MIN_MINUTES) return
        if (now - (lastReport[who] ?: 0L) < COOLDOWN_MS) return
        // Une app qui sert ses propres pubs ne peut pas etre nettoyee par
        // AdZero : lui attribuer un score serait mentir sur ce qu'il a fait.
        if (FirstParty.hopeless(who)) return
        lastReport[who] = now

        notify(ctx, who, minutes, blocked)
    }

    private fun notify(ctx: Context, who: String, minutes: Int, blocked: Int) {
        if (!Stats.sessionReports) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    ctx.getString(R.string.channel_session),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = ctx.getString(R.string.channel_session_body)
                    setShowBadge(true)
                }
            )
        }
        val label = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(who, 0)).toString()
        } catch (_: Exception) {
            who
        }

        // Le temps gagne est la seule facon de rendre un compteur concret. Un
        // nombre de pubs ne veut rien dire ; des minutes, si.
        val saved = blocked * Leaderboard.SECONDS_PER_AD / 60
        val body = ctx.getString(R.string.session_body, blocked, minutes) +
                if (saved >= 1) "\n" + ctx.getString(R.string.session_saved, saved) else ""

        val open = PendingIntent.getActivity(
            ctx, 41,
            Intent(ctx, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SESSION_CARD)
                .putExtra(MainActivity.EXTRA_SESSION_APP, who)
                .putExtra(MainActivity.EXTRA_SESSION_ADS, blocked)
                .putExtra(MainActivity.EXTRA_SESSION_MINUTES, minutes)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            ID,
            Notification.Builder(ctx, CHANNEL)
                .setContentTitle(ctx.getString(R.string.session_title, label))
                .setContentText(body.replace("\n", "  ·  "))
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_mark)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(
                    Notification.Action.Builder(
                        null, ctx.getString(R.string.session_share), open
                    ).build()
                )
                .build()
        )
    }
}
