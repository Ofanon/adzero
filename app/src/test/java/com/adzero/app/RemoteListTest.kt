package com.adzero.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La validation de la liste telechargee.
 *
 * C'est le seul endroit du projet ou un bug serait grave plutot qu'agacant :
 * cette fonction est ce qui separe "un fichier public sur GitHub" de "du code
 * qu'on execute sans le lire". Si elle laisse passer une ligne qui touche un
 * domaine essentiel, un depot compromis — ou une pull request malveillante
 * validee trop vite un soir de fatigue — coupe la banque de quelqu'un.
 *
 * Ces tests tournent sur la machine, sans telephone et sans Android : ils ne
 * touchent que de la logique pure. C'est aussi pour ca qu'ils sont rapides
 * assez pour etre lances a chaque changement.
 */
class RemoteListTest {

    // ------------------------------------------------------ ce qui doit passer

    @Test
    fun `un marqueur ordinaire est accepte`() {
        assertEquals("applovin", Remote.clean("applovin"))
        assertEquals("voodoo-tech.io", Remote.clean("voodoo-tech.io"))
    }

    @Test
    fun `espaces et majuscules sont normalises`() {
        assertEquals("applovin", Remote.clean("  APPLOVIN  "))
    }

    @Test
    fun `un commentaire en fin de ligne est retire`() {
        assertEquals("mintegral", Remote.clean("mintegral # vu dans MyHotel"))
    }

    // ------------------------------------------------- ce qui doit etre refuse

    @Test
    fun `une ligne vide ou un commentaire ne donne rien`() {
        assertNull(Remote.clean(""))
        assertNull(Remote.clean("   "))
        assertNull(Remote.clean("# juste un commentaire"))
    }

    @Test
    fun `un marqueur trop court est refuse`() {
        // "ad" apparait dans des milliers de domaines legitimes.
        assertNull(Remote.clean("ad"))
        assertNull(Remote.clean("io"))
    }

    @Test
    fun `un caractere qui n a rien a faire dans un nom d hote est refuse`() {
        assertNull(Remote.clean("applovin/../etc"))
        assertNull(Remote.clean("app lovin"))
        assertNull(Remote.clean("applovin;rm"))
        assertNull(Remote.clean("*.applovin.com"))
    }

    @Test
    fun `une ligne demesuree est refusee`() {
        assertNull(Remote.clean("a".repeat(200)))
    }

    // ------------------------------------------------------ le garde-fou reel
    //
    // Les cas qui comptent vraiment : une liste qui contiendrait ces lignes
    // couperait des services dont les gens dependent.

    @Test
    fun `un domaine essentiel ne peut jamais entrer dans la liste`() {
        for (danger in listOf(
            "google.com",
            "gstatic.com",
            "googleapis.com",
            "android.com",
            "whatsapp.net",
            "whatsapp.com",
        )) {
            assertNull("aurait du etre refuse : $danger", Remote.clean(danger))
        }
    }

    @Test
    fun `un fragment qui toucherait un domaine essentiel est refuse aussi`() {
        // Le piege : ce ne sont pas des domaines entiers, mais ils sont
        // contenus dans un domaine essentiel, donc ils le bloqueraient.
        assertNull(Remote.clean("google"))
        assertNull(Remote.clean("gstatic"))
    }

    @Test
    fun `Guard reconnait la menace dans les deux sens`() {
        // Le marqueur contient le domaine essentiel...
        assert(Guard.wouldBreakEssentials("www.google.com.evil.net"))
        // ...ou le domaine essentiel contient le marqueur.
        assert(Guard.wouldBreakEssentials("google"))
        // Et une vraie regie ne declenche rien.
        assert(!Guard.wouldBreakEssentials("applovin"))
        assert(!Guard.wouldBreakEssentials("voodoo-tech.io"))
    }
}
