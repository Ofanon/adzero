package com.adzero.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'ordre dans lequel les sources de blocage se departagent.
 *
 * Il y en a quatre maintenant — les autorisations de l'utilisateur, les
 * traceurs, la liste integree, la liste telechargee — et leur ordre est une
 * decision, pas un detail d'implementation : une liste distante qui passerait
 * devant un choix explicite de l'utilisateur reprendrait la main sur son
 * telephone.
 *
 * Ces tests fixent cet ordre pour qu'il ne derive pas silencieusement.
 */
class AdNetworksTest {

    @Test
    fun `une regie de la liste integree est bloquee`() {
        assertEquals(AdNetworks.Kind.AD, AdNetworks.classify("ads.applovin.com"))
        assertEquals(AdNetworks.Kind.AD, AdNetworks.classify("a.mintegral.com"))
    }

    @Test
    fun `un traceur est distingue d une pub`() {
        // Compte a part dans les statistiques : un traceur ne montre rien,
        // ce qui est precisement ce qui le rend pire qu'une banniere.
        assertEquals(AdNetworks.Kind.TRACKER, AdNetworks.classify("t.appsflyer.com"))
    }

    @Test
    fun `un domaine ordinaire passe`() {
        assertEquals(AdNetworks.Kind.NONE, AdNetworks.classify("wikipedia.org"))
        assertEquals(AdNetworks.Kind.NONE, AdNetworks.classify("cdn.monjeu.fr"))
    }

    @Test
    fun `la liste telechargee bloque ce qu elle ajoute`() {
        AdNetworks.setRemote(listOf("regie-inventee.example"))
        try {
            assertEquals(
                AdNetworks.Kind.AD,
                AdNetworks.classify("api.regie-inventee.example")
            )
        } finally {
            AdNetworks.setRemote(emptyList())
        }
    }

    @Test
    fun `la liste telechargee ne peut pas passer devant l utilisateur`() {
        // Le cas qui compte : l'utilisateur a explicitement relache un domaine
        // pour reparer son jeu. Aucune liste distante ne doit pouvoir le
        // rebloquer dans son dos.
        AdNetworks.allow("monjeu-cdn.example")
        AdNetworks.setRemote(listOf("monjeu-cdn.example"))
        try {
            assertEquals(
                AdNetworks.Kind.NONE,
                AdNetworks.classify("assets.monjeu-cdn.example")
            )
        } finally {
            AdNetworks.setRemote(emptyList())
            AdNetworks.blockAgain("monjeu-cdn.example")
        }
    }

    @Test
    fun `une autorisation de l utilisateur bat meme la liste integree`() {
        AdNetworks.allow("applovin")
        try {
            assertEquals(AdNetworks.Kind.NONE, AdNetworks.classify("ads.applovin.com"))
        } finally {
            AdNetworks.blockAgain("applovin")
        }
        // Et le blocage revient une fois l'autorisation retiree.
        assertEquals(AdNetworks.Kind.AD, AdNetworks.classify("ads.applovin.com"))
    }
}
