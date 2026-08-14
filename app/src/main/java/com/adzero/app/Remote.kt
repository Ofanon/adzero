package com.adzero.app

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * La liste des regies, tenue a jour sans mettre a jour l'app.
 *
 * Sans ca, AdZero se degrade tout seul. Une regie change de domaine — Voodoo
 * l'a fait cette semaine — et il faut recompiler, republier, attendre la revue
 * du store, puis esperer que les gens installent. Autant dire que la liste
 * gelee dans l'APK vieillit a partir du jour de sa sortie.
 *
 * Un fichier sur GitHub suffit : pas de serveur, pas de compte, pas de quota,
 * et rien qui expire. Ajouter un domaine devient une pull request que
 * n'importe qui peut proposer et qu'Oscar valide d'un clic.
 *
 * Trois regles de securite, parce qu'une liste telechargee est du code qu'on
 * execute sans le lire :
 *
 *  - elle ne peut qu'**ajouter**. Rien de ce qu'elle contient ne peut lever un
 *    blocage ni desactiver quoi que ce soit ;
 *  - toute entree qui toucherait un domaine essentiel est refusee sur place,
 *    en comparant a la liste de [Guard] — celle-la meme qui verifie que les
 *    banques et la messagerie passent toujours. Un depot compromis ne peut
 *    donc pas couper Internet a qui que ce soit ;
 *  - un echec de telechargement ne change rien du tout. La liste integree
 *    continue de travailler seule, ce qu'elle sait deja faire.
 */
object Remote {

    private const val URL_LIST =
        "https://raw.githubusercontent.com/Ofanon/adzero/main/blocklist.txt"

    private const val URL_VERSION =
        "https://raw.githubusercontent.com/Ofanon/adzero/main/version.txt"

    /** Une fois par jour : les regies n'apparaissent pas a l'heure. */
    private const val EVERY_MS = 24 * 60 * 60_000L

    /** Une liste plausible fait quelques centaines de lignes, pas un million. */
    private const val MAX_LINES = 5_000

    private var file: File? = null
    private var stampFile: File? = null

    /**
     * Quand la liste a ete rafraichie pour la derniere fois.
     *
     * Affichee dans les reglages : une fonctionnalite qui travaille en silence
     * ressemble a une fonctionnalite qui ne marche pas. "Mise a jour il y a
     * deux heures" coute une ligne et repond a la seule question qu'on se pose
     * devant un interrupteur allume.
     */
    @Volatile private var updatedAt = 0L

    fun lastUpdate(): Long = updatedAt

    /**
     * La derniere version publiee, si elle est plus recente que celle-ci.
     *
     * Le nom et l'adresse, pas le numero : c'est ce que la banniere affiche et
     * ce qu'elle ouvre. Null quand on est a jour, ce qui est le cas courant.
     */
    class Update(val name: String, val url: String)

    @Volatile private var update: Update? = null

    fun available(): Update? = update

    /** Combien la derniere mise a jour a ajoute de serveurs. */
    @Volatile private var added = 0

    fun added(): Int = added

    private fun checkVersion(ctx: Context) {
        try {
            val conn = (URL(URL_VERSION).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "AdZero")
            }
            if (conn.responseCode != 200) return
            val fields = HashMap<String, String>()
            conn.inputStream.bufferedReader().useLines { seq ->
                for (raw in seq.take(20)) {
                    val line = raw.trim().substringBefore('#').trim()
                    val i = line.indexOf('=')
                    if (i > 0) fields[line.take(i).trim()] = line.substring(i + 1).trim()
                }
            }
            val code = fields["code"]?.toIntOrNull() ?: return
            val name = fields["name"] ?: return
            val url = fields["url"] ?: return
            // Seules les adresses du depot d'AdZero sont suivies : une banniere
            // qui ouvre n'importe quelle URL est une porte d'entree, pas une
            // fonctionnalite.
            if (!url.startsWith("https://github.com/Ofanon/adzero/")) return

            val mine = try {
                val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
            } catch (_: Exception) {
                return
            }
            update = if (code > mine) Update(name, url) else null
        } catch (_: Exception) {
        }
    }

    fun init(ctx: Context) {
        if (file != null) return
        val dir = ctx.applicationContext.filesDir
        file = File(dir, "remote_networks.txt")
        stampFile = File(dir, "remote_stamp.txt")
        load()
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            AdNetworks.setRemote(f.readLines().mapNotNull { clean(it) })
            updatedAt = stampFile
                ?.takeIf { it.exists() }
                ?.readText()?.trim()?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        }
    }

    /**
     * Telecharge si la derniere tentative date de plus d'un jour.
     *
     * Sur son propre fil : c'est du reseau, et il est appele depuis le
     * demarrage du service.
     */
    fun refresh(ctx: Context) {
        if (!Stats.remoteList) return
        val stamp = stampFile ?: return
        val last = try {
            stamp.readText().trim().toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (System.currentTimeMillis() - last < EVERY_MS) return

        Thread({
            try {
                val conn = (URL(URL_LIST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "text/plain")
                    // Aucun identifiant, aucun cookie, rien de l'utilisateur :
                    // c'est un fichier public qu'on va chercher, exactement
                    // comme un navigateur le ferait.
                    setRequestProperty("User-Agent", "AdZero")
                }
                if (conn.responseCode != 200) return@Thread
                val lines = conn.inputStream.bufferedReader().useLines { seq ->
                    seq.take(MAX_LINES).mapNotNull { clean(it) }.toList()
                }
                // Une liste vide est un depot casse, pas une instruction de
                // tout debloquer : on garde ce qu'on avait.
                if (lines.isEmpty()) return@Thread
                val before = AdNetworks.remoteCount()

                added = (lines.size - before).coerceAtLeast(0)
                AdNetworks.setRemote(lines)
                file?.writeText(lines.joinToString("\n", postfix = "\n"))
                updatedAt = System.currentTimeMillis()
                stamp.writeText(updatedAt.toString())
                checkVersion(ctx)
            } catch (_: Exception) {
                // Pas de reseau, pas de GitHub, pas de probleme : la liste
                // integree et l'apprentissage local continuent seuls.
            }
        }, "adzero-list").start()
    }

    /**
     * Valide une ligne, ou la rejette.
     *
     * Le refus le plus important est le dernier : un marqueur qui toucherait
     * un domaine essentiel ne rentre pas, quoi qu'il arrive. C'est ce qui fait
     * qu'un depot compromis ne peut pas couper la banque de quelqu'un.
     */
    private fun clean(raw: String): String? {
        val line = raw.trim().lowercase().substringBefore('#').trim()
        if (line.length < 4 || line.length > 80) return null
        // Un marqueur est un fragment de nom d'hote : lettres, chiffres,
        // points et tirets. Tout le reste est une erreur ou pire.
        if (!line.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        if (Guard.wouldBreakEssentials(line)) return null
        return line
    }
}
