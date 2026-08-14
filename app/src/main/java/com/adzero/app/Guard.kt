package com.adzero.app

/**
 * The things that must never be blocked, checked against what the app would
 * actually do to them right now.
 *
 * Every list in this project matches by substring, which is what makes it
 * short and readable — and what makes it able to catch far more than intended.
 * A marker for one ad network can swallow a bank; releasing one domain in the
 * repair flow can release a whole family, because "unity3d.com" allowed as an
 * exception also allows "unityads.unity3d.com".
 *
 * None of that is visible by reading the lists. It is only visible by asking
 * the question the other way round: here are forty addresses a phone must be
 * able to reach — what does AdZero say about them today?
 *
 * These are not the infrastructure exceptions. Testing the lists against their
 * own exception list would only prove they agree with themselves. These are
 * ordinary services picked from outside the project entirely, which is what
 * makes a failure here mean something.
 */
object Guard {

    private val MUST_PASS = listOf(
        // The phone itself
        "google.com", "gstatic.com", "googleapis.com", "play.googleapis.com",
        "android.com", "clients3.google.com", "connectivitycheck.gstatic.com",

        // Talking to people
        "whatsapp.net", "web.whatsapp.com", "signal.org", "discord.com",
        "telegram.org", "messenger.com",

        // Money and administration, where a false positive is worst
        "paypal.com", "revolut.com", "stripe.com", "bnpparibas.net",
        "credit-agricole.fr", "labanquepostale.fr", "impots.gouv.fr",
        "ameli.fr", "service-public.fr",

        // Everyday things
        "wikipedia.org", "github.com", "mozilla.org", "duckduckgo.com",
        "apple.com", "icloud.com", "microsoft.com", "office.com", "live.com",
        "sncf-connect.com", "doctolib.fr", "leboncoin.fr", "amazon.fr",

        // Delivery networks whose loss breaks half the web
        "cloudflare.com", "cloudfront.net", "akamai.net", "fastly.net",
        "ytimg.com", "googlevideo.com",
    )

    class Failure(val host: String, val kind: AdNetworks.Kind)

    /** Anything AdZero would currently silence that it must not. Empty is good. */
    /**
     * Ce marqueur casserait-il quelque chose d'essentiel ?
     *
     * Demande pour chaque ligne d'une liste telechargee, avant de l'accepter.
     * C'est ce qui fait qu'un depot compromis — ou une pull request malveillante
     * — ne peut pas couper la banque, la messagerie ou les impots de quelqu'un :
     * la ligne est refusee sur le telephone, pas ailleurs.
     */
    fun wouldBreakEssentials(marker: String): Boolean =
        MUST_PASS.any { marker in it || it in marker }

    fun check(): List<Failure> = MUST_PASS.mapNotNull { host ->
        val kind = AdNetworks.classify(host)
        if (kind == AdNetworks.Kind.NONE) null else Failure(host, kind)
    }

    val size: Int get() = MUST_PASS.size
}
