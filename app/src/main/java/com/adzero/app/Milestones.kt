package com.adzero.app

import android.content.Context
import java.io.File

/**
 * Les paliers : cent pubs, mille, dix mille.
 *
 * Un compteur qui monte n'est pas un evenement. Il passe de 998 a 1 002 sans
 * que rien ne se produise, et le millieme blocage — qui est pourtant une
 * quantite de temps considerable rendue a quelqu'un — se noie entre les deux.
 *
 * Un palier decoupe cette courbe continue en moments. C'est ce qui donne une
 * raison de reparler de l'app a quelqu'un, et c'est la seule fois ou une app
 * gratuite sans compte peut se permettre de demander de l'attention.
 *
 * La celebration attend l'ouverture de l'app plutot que d'interrompre la
 * partie. Le rapport de fin de partie occupe deja ce moment-la, et deux
 * notifications qui se suivent transforment une bonne nouvelle en agacement.
 */
object Milestones {

    /**
     * Les seuils, en publicites bloquees.
     *
     * Espaces de plus en plus large : les premiers doivent arriver vite pour
     * que quelque chose se passe le premier jour, les suivants doivent rester
     * rares pour continuer a valoir quelque chose. Un palier tous les cent
     * blocages cesserait d'etre un evenement au bout d'une semaine.
     */
    private val STEPS = intArrayOf(
        100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000
    )

    /**
     * Tous les paliers, franchis ou non.
     *
     * L'ecran des trophees montre aussi ceux qui restent : une liste ou tout
     * est deja obtenu n'a plus rien a promettre, et voir le prochain est la
     * moitie de l'interet.
     */
    fun steps(): IntArray = STEPS.copyOf()

    fun isReached(step: Int): Boolean = synchronized(reached) { step in reached }

    private val reached = mutableSetOf<Int>()
    private var file: File? = null

    /** Un palier franchi mais pas encore montre. */
    @Volatile
    var pending: Int = 0
        private set

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "milestones.txt")
        file = f
        if (f.exists()) try {
            f.forEachLine { line ->
                line.trim().toIntOrNull()?.let { synchronized(reached) { reached.add(it) } }
            }
        } catch (_: Exception) {
        }

        // Rattrapage silencieux au premier lancement apres la mise a jour :
        // quelqu'un qui a deja bloque trois mille pubs ne doit pas recevoir
        // huit celebrations d'affilee pour des paliers franchis il y a des
        // semaines. On les marque atteints sans rien annoncer.
        synchronized(reached) {
            if (reached.isEmpty()) {
                val total = Leaderboard.totalAttempts()
                val done = STEPS.filter { it <= total }
                if (done.isNotEmpty()) {
                    reached.addAll(done)
                    save()
                }
            }
        }
    }

    /**
     * Appele quand le total change. Retient le plus haut palier franchi.
     *
     * Volontairement silencieux : c'est [pending] que l'interface consulte a
     * l'ouverture. Rien ne s'affiche pendant qu'on joue.
     */
    fun check(total: Int) {
        // Appelee depuis le fil du tunnel, alors qu'init() peut encore lire le
        // fichier depuis un autre. Un HashSet modifie pendant qu'on le parcourt
        // ne se plaint pas : il rend des resultats faux, ce qui ici voudrait
        // dire un palier annonce deux fois ou jamais.
        synchronized(reached) {
            for (step in STEPS) {
                if (total >= step && reached.add(step)) {
                    pending = maxOf(pending, step)
                    save()
                }
            }
        }
    }

    /** Consomme le palier en attente : il ne sera plus annonce. */
    fun take(): Int {
        val step = pending
        pending = 0
        return step
    }

    /** Le prochain seuil, pour montrer le chemin qu'il reste. */
    fun next(total: Int): Int = STEPS.firstOrNull { it > total } ?: 0

    private fun save() {
        val f = file ?: return
        try {
            f.writeText(reached.joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
        }
    }
}
