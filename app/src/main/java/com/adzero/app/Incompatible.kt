package com.adzero.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Les apps qui refusent de fonctionner tant qu'un VPN existe.
 *
 * Elles ne sont pas bloquees par AdZero : elles voient qu'un tunnel est actif
 * et s'arretent d'elles-memes, souvent avant la moindre requete. Aucune liste
 * de domaines n'y peut rien, et les deux ecrans de depannage non plus — ils
 * supposent qu'on soupconne deja AdZero et qu'on l'ouvre.
 *
 * Or c'est precisement ce que la personne concernee ne fera pas. Le grand-pere
 * d'Oscar a vu l'app de sa voiture cesser de marcher ; pour lui, c'est la
 * voiture qui est cassee. Personne ne fait le lien avec un bloqueur de
 * publicites installe la semaine d'avant, et l'app finit desinstallee pour une
 * panne qu'elle savait expliquer.
 *
 * D'ou l'ordre choisi : prevenir a l'allumage de la protection, avant que quoi
 * que ce soit casse, plutot que d'attendre une plainte. Le message dit quelle
 * app, pourquoi, et l'exclut d'un seul appui — sans ouvrir AdZero.
 */
object Incompatible {

    private const val CHANNEL = "adzero.incompatible"

    /**
     * Ce qui est connu au moment de la compilation, et c'est peu.
     *
     * Deviner d'autres noms de paquets serait pire que de n'en avoir aucun :
     * une entree erronee ne se declenche jamais et personne ne s'en apercoit.
     * La liste grandit donc par [setRemote], au fur et a mesure des retours,
     * sans attendre une mise a jour de l'app.
     */
    private val SEED = listOf("com.toyota.oneapp")

    private val remote = mutableSetOf<String>()

    /** Une seule alerte par app : la deuxieme serait du harcelement. */
    private val warned = mutableSetOf<String>()
    private var warnedFile: File? = null

    fun init(ctx: Context) {
        if (warnedFile != null) return
        val f = File(ctx.applicationContext.filesDir, "incompatible_warned.txt")
        warnedFile = f
        try {
            if (f.exists()) synchronized(warned) { warned.addAll(f.readLines().filter { it.isNotBlank() }) }
        } catch (_: Exception) {
        }
    }

    fun setRemote(list: List<String>) = synchronized(remote) {
        remote.clear()
        remote.addAll(list)
    }

    /**
     * Compare sur le prefixe, parce qu'une meme app porte un nom par pays :
     * l'app Toyota europeenne est com.toyota.oneapp.eu, l'australienne
     * com.au.toyota.oneapp. Le prefixe attrape les variantes sans qu'il faille
     * les enumerer.
     */
    fun matches(pkg: String): Boolean {
        val all = SEED + synchronized(remote) { remote.toList() }
        return all.any { pkg == it || pkg.startsWith("$it.") }
    }

    /**
     * Passe en revue ce qui est installe, et n'alerte que sur du concret.
     *
     * Appele au demarrage du tunnel, sur son propre fil : le PackageManager
     * repond en dizaines de millisecondes et rien ici ne doit retarder
     * l'etablissement de la protection.
     */
    fun scan(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            try {
                val pm = app.packageManager
                val launchable = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                ).map { it.activityInfo.packageName }.distinct()

                for (pkg in launchable) {
                    if (!matches(pkg)) continue
                    // Deja exclue : elle marche, il n'y a rien a signaler.
                    if (AppFilter.isExcluded(pkg)) continue
                    if (synchronized(warned) { !warned.add(pkg) }) continue
                    saveWarned()
                    warn(app, pkg)
                }
            } catch (_: Exception) {
            }
        }, "adzero-incompatible").start()
    }

    private fun saveWarned() {
        try {
            warnedFile?.writeText(synchronized(warned) { warned.joinToString("\n") })
        } catch (_: Exception) {
        }
    }

    private fun warn(ctx: Context, pkg: String) {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    ctx.getString(R.string.channel_incompatible),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = ctx.getString(R.string.channel_incompatible_body) }
            )
        }
        val label = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }

        val exclude = PendingIntent.getBroadcast(
            ctx, pkg.hashCode(),
            Intent(ctx, ExcludeReceiver::class.java)
                .setAction(ExcludeReceiver.ACTION_EXCLUDE)
                .putExtra(ExcludeReceiver.EXTRA_PKG, pkg),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            pkg.hashCode(),
            Notification.Builder(ctx, CHANNEL)
                .setContentTitle(ctx.getString(R.string.incompatible_title, label))
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(ctx.getString(R.string.incompatible_body, label))
                )
                .setContentText(ctx.getString(R.string.incompatible_body, label))
                .setSmallIcon(R.drawable.ic_mark)
                .setAutoCancel(true)
                .addAction(
                    Notification.Action.Builder(
                        null, ctx.getString(R.string.incompatible_exclude), exclude
                    ).build()
                )
                .build()
        )
    }
}
