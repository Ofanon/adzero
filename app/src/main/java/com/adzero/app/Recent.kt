package com.adzero.app

import android.content.Context
import java.io.File

/**
 * The last half-minute of DNS, kept in memory so a user can point at an ad.
 *
 * The blocklist and the learning both work forwards: they decide before the ad
 * appears. Neither can help with the ads that still get through, because those
 * come from domains registered last week and thrown away next month — a single
 * game uses them, so they never reach the "seen in three apps" threshold, and
 * by the time they would, the domain is dead and another has replaced it.
 *
 * The person holding the phone knows something the app cannot work out on its
 * own: that an ad just appeared. This is the buffer that lets them say so. Ten
 * seconds before a report is a very small haystack.
 */
object Recent {

    /**
     * How far back a report can reach.
     *
     * This was twelve seconds, which assumed the user reports the instant the
     * ad appears. Nobody does: you watch the thing, close it, play on, and only
     * then think to say something. Five minutes covers that, at the cost of a
     * much larger haystack — which is why [weigh] now scores recency instead of
     * relying on the window to do the filtering.
     */
    /**
     * Trois heures, pas cinq minutes.
     *
     * Personne ne signale une pub pendant la partie : on joue, on ferme, et on
     * y pense apres. Une fenetre de cinq minutes n'attrapait donc que les gens
     * qui pensaient a revenir immediatement, c'est-a-dire personne.
     *
     * Ce que ca coutait avant : impossible, l'anneau gardait chaque requete.
     * Depuis que les traces sont repliees par (app, domaine), trois heures
     * tiennent dans moins de place que cinq minutes n'en prenaient.
     */
    const val WINDOW_MS = 3 * 60 * 60_000L

    /**
     * Which app was on screen is a question about *now*, not about the last
     * five minutes: over that long the user may well have changed apps twice.
     */
    private const val FOREGROUND_MS = 90_000L

    /** A ceiling, not a target: a game in a burst can fire hundreds a minute. */
    /** Traces distinctes, pas requetes : une partie en produit quelques dizaines. */
    private const val CAPACITY = 4000

    /**
     * Ce qu'une app a demande, replie.
     *
     * [hits] compte les requetes, [at] retient la derniere. C'est tout ce que
     * le scoring lit — il n'a jamais eu besoin de l'horodatage de chacune.
     */
    class Hit(val host: String, val app: String, var at: Long, var blocked: Boolean) {
        var hits: Int = 1
    }

    private val ring = LinkedHashMap<String, Hit>()
    private var file: File? = null
    @Volatile private var dirty = false

    private fun key(app: String, host: String) = app + "|" + Stats.rootOf(host)

    fun note(host: String, app: String, blocked: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(ring) {
            val k = key(app, host)
            val seen = ring.remove(k)
            if (seen != null) {
                seen.hits++
                seen.at = now
                seen.blocked = blocked
                ring[k] = seen          // remis en queue : le plus recent en dernier
            } else {
                ring[k] = Hit(Stats.rootOf(host), app, now, blocked)
            }
            dirty = true
            while (ring.size > CAPACITY) ring.remove(ring.keys.first())
            val cutoff = now - WINDOW_MS
            val stale = ring.entries.takeWhile { it.value.at < cutoff }.map { it.key }
            for (k2 in stale) ring.remove(k2)
        }
    }

    /**
     * Recharge le journal au demarrage.
     *
     * Sans ca, fermer l'app effacait la seule chose qui permettait d'expliquer
     * ce qui venait de se passer — et l'app se ferme precisement quand on lance
     * un jeu.
     */
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "recent.txt")
        file = f
        if (!f.exists()) return
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        try {
            f.forEachLine { line ->
                val parts = line.split("|")
                if (parts.size == 5) {
                    val at = parts[3].toLongOrNull() ?: return@forEachLine
                    if (at < cutoff) return@forEachLine
                    val hit = Hit(parts[1], parts[0], at, parts[4] == "1")
                    hit.hits = parts[2].toIntOrNull() ?: 1
                    synchronized(ring) { ring[parts[0] + "|" + parts[1]] = hit }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun save() {
        val f = file ?: return
        if (!dirty) return
        try {
            val copy = synchronized(ring) { ring.values.toList() }
            f.bufferedWriter().use { w ->
                for (h in copy) {
                    // Un domaine ne contient jamais de barre verticale, ni un
                    // nom de paquet : le separateur est sur.
                    w.write(h.app + "|" + h.host + "|" + h.hits + "|" + h.at +
                            "|" + (if (h.blocked) "1" else "0") + "\n")
                }
            }
            dirty = false
        } catch (_: Exception) {
        }
    }

    /** The app that resolved the most names recently: the one on screen. */
    /**
     * Les apps qui ont parle recemment, celle qui a le plus parle en tete.
     *
     * Les services du systeme en sont exclus. Ils emettent en permanence, donc
     * ils gagnent tous les classements par volume — et ils n'ont jamais montre
     * de publicite a personne. Les proposer comme coupable, c'est designer le
     * temoin le plus bruyant de la piece.
     */
    fun activeApps(max: Int = 8): List<String> {
        val now = System.currentTimeMillis()
        val counts = HashMap<String, Int>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > WINDOW_MS) continue
                if (h.app == "?" || h.app == "com.adzero.app") continue
                if (Shield.isSystemService(h.app)) continue
                counts[h.app] = (counts[h.app] ?: 0) + h.hits
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    fun busiestApp(): String? {
        val now = System.currentTimeMillis()
        val counts = HashMap<String, Int>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > FOREGROUND_MS) continue
                if (h.app == "?" || h.app == "com.adzero.app") continue
                counts[h.app] = (counts[h.app] ?: 0) + h.hits
            }
        }
        return counts.maxByOrNull { it.value }?.key
    }

    /**
     * One plain-language reason to suspect a domain, ready to show.
     *
     * A hostname is not a decision aid. Asked to choose between unity3d.com and
     * a random string, someone who does not work in this knows neither, and the
     * safe move is to tap nothing — which makes the whole feature useless. What
     * they can judge is the reasoning: "seen next to an ad in two of your
     * games" is something anyone can weigh.
     */
    class Reason(val text: Int, val arg: Any? = null)

    class Suspect(
        val domain: String,
        val app: String,
        val score: Int,
        val hits: Int,
        val reasons: List<Reason>,
    ) {
        /** High enough to lead with, or only worth listing underneath. */
        val confident: Boolean get() = score >= 60
        val plausible: Boolean get() = score >= 30
    }

    /**
     * What most likely served the ad the user just saw.
     *
     * Only domains we let through can be to blame — a silenced one produced
     * nothing. Beyond that the ranking is deliberately crude, because the user
     * makes the final call and a wrong guess costs them one extra glance.
     */
    fun suspects(app: String?, max: Int = 6): List<Suspect> {
        val now = System.currentTimeMillis()
        val byDomain = LinkedHashMap<String, MutableList<Hit>>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > WINDOW_MS) continue
                if (h.blocked) continue
                if (h.app == "com.adzero.app") continue
                if (app != null && h.app != app) continue
                // Accusing a CDN of serving the ad is how a blocker ends up
                // breaking the game it was meant to clean up.
                if (Learning.isInfrastructure(Stats.rootOf(h.host))) continue
                byDomain.getOrPut(h.host) { mutableListOf() }.add(h)
            }
        }
        return byDomain.entries
            .map { (domain, hits) -> weigh(domain, hits) }
            .sortedByDescending { it.score }
            .take(max)
    }

    /**
     * Scores a domain and records why, in the same pass.
     *
     * The score and the reasons must never drift apart: a verdict the app
     * cannot justify is a verdict nobody can act on.
     */
    private fun weigh(domain: String, hits: List<Hit>): Suspect {
        // Les traces sont repliees : une ligne par app, avec son compteur.
        // Compter les lignes reviendrait a compter les apps, pas les requetes.
        val seen = hits.sumOf { it.hits }
        var s = 0
        val why = mutableListOf<Reason>()

        // Already seen sitting next to a known ad network, in this app or
        // another. The strongest signal we have, and it is free.
        val near = Learning.adProximity(domain)
        if (near > 0) {
            s += near * 25
            why.add(Reason(R.string.reason_near_ads, near))
        }

        // A domain nobody has heard of, on a TLD that costs a euro. Legitimate
        // companies do not serve their product from .top or .xyz.
        val tld = domain.substringAfterLast('.')
        if (tld in CHEAP_TLDS) {
            s += 40
            why.add(Reason(R.string.reason_cheap_tld, tld))
        }

        // Ad words hiding in plain sight. Matched on whole words only:
        // "ad" as a substring also hits gradle, download and roadmap.
        val word = domain.split('.', '-', '_').firstOrNull { it in AD_WORDS }
        if (word != null) {
            s += 30
            why.add(Reason(R.string.reason_ad_word, word))
        }

        // A name that reads like a keyboard mash rather than a word: the
        // signature of a domain generated in bulk. Vowel-starved and long.
        val name = domain.substringBeforeLast('.')
        val vowels = name.count { it in "aeiouy" }
        if (name.length >= 8 && vowels * 4 < name.length) {
            s += 20
            why.add(Reason(R.string.reason_random))
        }

        // Contacted once or twice rather than steadily. Over five minutes this
        // separates cleanly: an app's own backend chatters the whole time, an
        // ad server is called when there is an ad to fetch and never again.
        if (seen <= 2) {
            s += 20
            why.add(Reason(R.string.reason_once))
        }

        // Recency, which the twelve-second window used to enforce for free.
        // An ad reported now was served in the last minute or two, not four
        // minutes ago, so freshness has to be scored rather than assumed.
        val age = (System.currentTimeMillis() - hits.maxOf { it.at }) / 1000
        if (age < 60) s += 30 else if (age < 180) s += 15

        if (why.isEmpty()) why.add(Reason(R.string.reason_nothing))
        return Suspect(domain, hits.first().app, s, seen, why)
    }

    private val CHEAP_TLDS = setOf(
        "top", "xyz", "site", "work", "click", "link", "live", "online",
        "shop", "store", "fun", "icu", "cyou", "buzz", "rest", "monster",
    )

    private val AD_WORDS = listOf(
        "ad", "ads", "adv", "banner", "click", "track", "pixel", "promo",
        "offer", "sponsor", "campaign", "impression", "monet",
    )

    /** Apps AdZero has silenced something for lately, busiest first. */
    /**
     * Ce qui a ete bloque pour cette app et lui manque probablement.
     *
     * Le miroir de [suspects]. La ou celui-ci cherche un serveur de pub qui
     * est passe, celui-ci cherche un serveur ordinaire qu'on a fait taire :
     * meme fenetre, meme regroupement par domaine, score inverse.
     *
     * Le signal le plus fort est la repetition. Une app dont une requete reste
     * sans reponse reessaie, encore et encore — alors qu'un SDK publicitaire
     * qu'on a coupe abandonne et passe au suivant. Un domaine bloque et
     * redemande dix fois est presque toujours celui qui manque.
     */
    fun breakers(app: String, max: Int = 8): List<Suspect> {
        val now = System.currentTimeMillis()
        val byDomain = LinkedHashMap<String, MutableList<Hit>>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > WINDOW_MS) continue
                if (!h.blocked) continue
                if (h.app != app) continue
                byDomain.getOrPut(h.host) { mutableListOf() }.add(h)
            }
        }
        return byDomain.entries.map { (domain, hits) ->
            val seen = hits.sumOf { it.hits }
            var score = 0
            val why = mutableListOf<Reason>()

            if (seen >= 3) {
                score += minOf(seen, 12) * 6
                why.add(Reason(R.string.reason_retried, seen))
            }
            if (Learning.isInfrastructure(domain)) {
                score += 45
                why.add(Reason(R.string.reason_shared))
            }
            val card = Explain.cardFor(domain)
            if (card.owner.isEmpty() && card.kind == Explain.Kind.UNKNOWN) {
                score += 30
                why.add(Reason(R.string.reason_not_an_ad_network))
            }
            // Une regie reconnue n'est presque jamais ce qui casse un jeu, et
            // la relacher est la facon dont la protection se demonte en douce.
            if (card.owner.isNotEmpty() && card.kind != Explain.Kind.ENGINE) score -= 50
            if (looksLikeAnAd(domain)) score -= 40
            if (card.kind == Explain.Kind.ENGINE) {
                score += 40
                why.add(Reason(R.string.reason_engine))
            }
            if (why.isEmpty()) why.add(Reason(R.string.reason_nothing))
            Suspect(domain, app, score, seen, why)
        }.sortedByDescending { it.score }.take(max)
    }

    fun silencedApps(max: Int = 5): List<String> {
        val now = System.currentTimeMillis()
        val counts = LinkedHashMap<String, Int>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > WINDOW_MS || !h.blocked) continue
                if (h.app == "?" || h.app == "com.adzero.app") continue
                counts[h.app] = (counts[h.app] ?: 0) + h.hits
            }
        }
        return counts.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    /**
     * What was silenced for one app, most recent first.
     *
     * The other half of the report: when a game stops working it is almost
     * always because one of these was a domain it genuinely needed.
     */
    fun silencedFor(app: String, max: Int = 8): List<String> {
        val now = System.currentTimeMillis()
        val seen = LinkedHashMap<String, Long>()
        synchronized(ring) {
            for (h in ring.values) {
                if (now - h.at > WINDOW_MS || !h.blocked || h.app != app) continue
                val root = Stats.rootOf(h.host)
                seen[root] = maxOf(seen[root] ?: 0L, h.at)
            }
        }
        return seen.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    /**
     * Whether a domain name alone gives it away.
     *
     * Same evidence the ad report ranks with, minus everything that depends on
     * timing. The shield uses it as a second opinion when it is judging an app
     * whose ordinary traffic it has never seen.
     */
    fun looksLikeAnAd(root: String): Boolean {
        if (Learning.adProximity(root) > 0) return true
        if (root.substringAfterLast('.') in CHEAP_TLDS) return true
        if (root.split('.', '-', '_').any { it in AD_WORDS }) return true
        val name = root.substringBeforeLast('.')
        val vowels = name.count { it in "aeiouy" }
        return name.length >= 8 && vowels * 4 < name.length
    }

    /**
     * How many times this app asked for that name recently.
     *
     * Steady traffic is the signature of something an app depends on; a server
     * touched once, mid-burst, is the signature of an ad.
     */
    fun timesSeen(app: String, root: String): Int {
        val now = System.currentTimeMillis()
        return synchronized(ring) {
            // Le nombre de requetes, pas de traces : c'est bien la
            // repetition qui distingue une dependance d'une pub.
            ring.values
                .filter { now - it.at <= WINDOW_MS && it.app == app && it.host == root }
                .sumOf { it.hits }
        }
    }

    fun clear() = synchronized(ring) { ring.clear(); dirty = true }
}
