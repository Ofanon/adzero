package com.adzero.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telecharge la nouvelle version et la remet a l'installeur du systeme.
 *
 * Une app installee a la main n'a aucun mecanisme de mise a jour. Envoyer les
 * gens sur une page web marche, mais chaque etape en perd : ouvrir le
 * navigateur, trouver le fichier, le retrouver dans les telechargements,
 * l'ouvrir. Ici, un appui, et l'ecran d'installation d'Android s'affiche.
 *
 * Il n'y a rien de silencieux la-dedans, et c'est voulu : Android ne permet pas
 * d'installer sans que l'utilisateur voie et confirme, et c'est tres bien.
 *
 * DEUX GARDE-FOUS
 *
 * L'adresse doit appartenir au depot d'AdZero. Un fichier distant qui pourrait
 * designer n'importe quelle URL a telecharger et installer serait une porte
 * d'entree, pas une fonctionnalite.
 *
 * Et surtout : la signature du fichier telecharge est comparee a celle de l'app
 * en cours **avant** de le proposer. Android refuserait de toute facon une mise
 * a jour signee par une autre cle, mais l'interet est ailleurs — sans cette
 * verification, AdZero pourrait presenter a l'utilisateur l'installation d'une
 * app entierement differente. Un telechargement dont la signature ne correspond
 * pas est efface sans etre montre.
 */
object Updater {

    private const val NAME = "AdZero-update.apk"

    /** Ce qui se passe, pour que l'interface puisse le dire. */
    enum class State { DOWNLOADING, READY, FAILED, WRONG_SIGNATURE }

    /**
     * Telecharge [url], verifie, puis ouvre l'installeur.
     *
     * Sur son propre fil : c'est du reseau et un fichier d'un mega et demi.
     * [onState] est rappele sur ce fil, pas sur celui de l'interface.
     */
    fun fetchAndInstall(ctx: Context, url: String, onState: (State) -> Unit) {
        if (!url.startsWith("https://github.com/Ofanon/adzero/")) {
            onState(State.FAILED)
            return
        }
        onState(State.DOWNLOADING)
        Thread({
            val app = ctx.applicationContext
            val file = File(ApkProvider.shareDir(app), NAME)
            try {
                var target = URL(url)
                var conn = open(target)
                // GitHub renvoie une redirection vers son stockage de fichiers.
                // HttpURLConnection ne suit pas une redirection qui change de
                // protocole ou d'hote, donc on la suit a la main.
                var hops = 0
                while (conn.responseCode in 301..308 && hops++ < 5) {
                    val next = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    target = URL(target, next)
                    conn = open(target)
                }
                if (conn.responseCode != 200) {
                    onState(State.FAILED)
                    return@Thread
                }
                conn.inputStream.use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }

                if (!sameSignature(app, file.absolutePath)) {
                    // Efface sans jamais le montrer : proposer l'installation
                    // d'un fichier dont on ne reconnait pas la signature est
                    // exactement ce qu'on ne doit pas faire.
                    file.delete()
                    onState(State.WRONG_SIGNATURE)
                    return@Thread
                }

                onState(State.READY)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(ApkProvider.uriFor(file), ApkProvider.APK_MIME)
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )
            } catch (_: Exception) {
                file.delete()
                onState(State.FAILED)
            }
        }, "adzero-update").start()
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "AdZero")
        }

    /**
     * Le fichier telecharge est-il signe avec la meme cle que l'app installee ?
     *
     * C'est la seule question qui compte : elle prouve que le fichier vient de
     * la personne qui a publie la version deja presente, et de personne d'autre.
     */
    private fun sameSignature(ctx: Context, path: String): Boolean = try {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flag = PackageManager.GET_SIGNING_CERTIFICATES
            val theirs = pm.getPackageArchiveInfo(path, flag)?.signingInfo
            val mine = pm.getPackageInfo(ctx.packageName, flag).signingInfo
            val a = theirs?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val b = mine?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            a != null && a.isNotEmpty() && a == b
        } else {
            @Suppress("DEPRECATION") val flag = PackageManager.GET_SIGNATURES
            @Suppress("DEPRECATION")
            val a = pm.getPackageArchiveInfo(path, flag)?.signatures
                ?.map { it.toCharsString() }?.toSet()
            @Suppress("DEPRECATION")
            val b = pm.getPackageInfo(ctx.packageName, flag).signatures
                ?.map { it.toCharsString() }?.toSet()
            a != null && a.isNotEmpty() && a == b
        }
    } catch (_: Exception) {
        false
    }
}
