package com.example.security

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

// ============================================================
// MOTEUR DE DIAGNOSTIC DE BLOCAGE
//
// Un scan qui échoue n'est pas une absence de résultat : c'est souvent
// un résultat en soi (le site/serveur a réagi pour se protéger). Ce
// module transforme chaque échec en explication concrète et justifiée,
// au lieu d'un "erreur" ou "indéterminé" générique qui n'apprend rien
// au lecteur du rapport.
// ============================================================

enum class CauseBlocage {
    PARE_FEU_RESEAU,        // connexion refusée/filtrée au niveau TCP
    TIMEOUT,                 // pas de réponse dans le délai (souvent un pare-feu qui "drop" silencieusement)
    WAF_APPLICATIF,           // bloqué au niveau HTTP par un pare-feu applicatif (Cloudflare, Sucuri, ModSecurity...)
    AUTHENTIFICATION_REQUISE, // 401 : protection par identifiants déjà active
    ACCES_INTERDIT,           // 403 : règle de blocage explicite, pas forcément un WAF
    RATE_LIMITING,            // 429 : trop de requêtes, protection anti-bruteforce probable
    SERVICE_INDISPONIBLE,     // 503 : peut indiquer un WAF en mode "challenge" ou une vraie panne
    SSL_BLOQUE,               // échec de négociation SSL — souvent un WAF qui intercepte le trafic
    DOMAINE_INTROUVABLE,      // DNS : domaine mal orthographié, non résolu, ou n'existe pas
    INCONNUE
}

data class DiagnosticBlocage(
    val cause: CauseBlocage,
    val explicationJustifiee: String,
    val estUnBonSigneSecurite: Boolean // true si le blocage prouve qu'une protection existe (ex: WAF, auth)
)

/**
 * Diagnostique une exception réseau et produit une explication justifiée,
 * au lieu de se contenter du message brut de l'exception.
 */
fun diagnostiquerExceptionReseau(e: Exception): DiagnosticBlocage = when (e) {
    is SocketTimeoutException -> DiagnosticBlocage(
        cause = CauseBlocage.TIMEOUT,
        explicationJustifiee = "Aucune réponse reçue dans le délai imparti. Dans la majorité des cas, ce n'est pas une panne : un pare-feu (ou un WAF comme Cloudflare) absorbe silencieusement la requête (\"drop\") plutôt que de répondre explicitement, pour ne pas révéler sa présence.",
        estUnBonSigneSecurite = true
    )
    is ConnectException -> DiagnosticBlocage(
        cause = CauseBlocage.PARE_FEU_RESEAU,
        explicationJustifiee = "Connexion refusée au niveau réseau (TCP). Le port est soit fermé sur le serveur, soit explicitement bloqué par un pare-feu réseau avant même d'atteindre l'application.",
        estUnBonSigneSecurite = true
    )
    is SSLHandshakeException -> DiagnosticBlocage(
        cause = CauseBlocage.SSL_BLOQUE,
        explicationJustifiee = "La négociation SSL/TLS a échoué avant même l'envoi de la requête. Signe fréquent d'un pare-feu/proxy qui intercepte et filtre le trafic chiffré en amont du serveur réel.",
        estUnBonSigneSecurite = true
    )
    is SSLException -> DiagnosticBlocage(
        cause = CauseBlocage.SSL_BLOQUE,
        explicationJustifiee = "Erreur SSL générique pendant la connexion — peut indiquer une interception par un équipement de sécurité intermédiaire (proxy, WAF) ou une mauvaise configuration du certificat.",
        estUnBonSigneSecurite = true
    )
    is UnknownHostException -> DiagnosticBlocage(
        cause = CauseBlocage.DOMAINE_INTROUVABLE,
        explicationJustifiee = "Le nom de domaine n'a pas pu être résolu par le DNS. Vérifiez l'orthographe du domaine saisi, ou que le domaine est bien actif et pointe vers un serveur.",
        estUnBonSigneSecurite = false
    )
    else -> DiagnosticBlocage(
        cause = CauseBlocage.INCONNUE,
        explicationJustifiee = "Échec de connexion non classifié : ${e.javaClass.simpleName} — ${e.localizedMessage ?: "aucun détail disponible"}.",
        estUnBonSigneSecurite = false
    )
}

/**
 * Diagnostique un code HTTP de blocage (pas une exception réseau, mais une
 * réponse HTTP qui indique explicitement un refus).
 */
fun diagnostiquerCodeHttp(code: Int, headers: Map<String, String> = emptyMap()): DiagnosticBlocage {
    // Indices de WAF connus dans les en-têtes (sans viser l'exhaustivité, juste les plus courants)
    val serveurHeader = headers.entries.firstOrNull { it.key.equals("Server", true) }?.value.orEmpty()
    val indiceWaf = when {
        serveurHeader.contains("cloudflare", true) -> "Cloudflare"
        serveurHeader.contains("sucuri", true) -> "Sucuri"
        headers.keys.any { it.equals("X-Sucuri-ID", true) } -> "Sucuri"
        headers.keys.any { it.equals("cf-ray", true) } -> "Cloudflare"
        else -> null
    }
    val suffixeWaf = if (indiceWaf != null) " Indice détecté : le trafic semble passer par $indiceWaf." else ""

    return when (code) {
        401 -> DiagnosticBlocage(
            cause = CauseBlocage.AUTHENTIFICATION_REQUISE,
            explicationJustifiee = "Le serveur répond HTTP 401 : une authentification est explicitement exigée avant d'accéder à cette ressource. C'est une protection active, pas une erreur de scan.",
            estUnBonSigneSecurite = true
        )
        403 -> DiagnosticBlocage(
            cause = CauseBlocage.ACCES_INTERDIT,
            explicationJustifiee = "Le serveur répond HTTP 403 : l'accès est explicitement refusé.$suffixeWaf Ceci peut venir d'une règle de pare-feu applicatif, d'une restriction par IP, ou d'une permission serveur.",
            estUnBonSigneSecurite = true
        )
        429 -> DiagnosticBlocage(
            cause = CauseBlocage.RATE_LIMITING,
            explicationJustifiee = "Le serveur répond HTTP 429 (trop de requêtes) : une limitation de débit est active, probablement pour se protéger contre des scans ou tentatives de connexion automatisées. C'est une bonne pratique de sécurité de la cible.",
            estUnBonSigneSecurite = true
        )
        503 -> DiagnosticBlocage(
            cause = CauseBlocage.SERVICE_INDISPONIBLE,
            explicationJustifiee = "Le serveur répond HTTP 503.$suffixeWaf Ce code peut signifier une vraie indisponibilité temporaire, mais est aussi utilisé par certains WAF pour un \"challenge\" anti-bot (page d'attente/vérification) avant de laisser passer une requête légitime.",
            estUnBonSigneSecurite = indiceWaf != null
        )
        else -> DiagnosticBlocage(
            cause = CauseBlocage.INCONNUE,
            explicationJustifiee = "Réponse HTTP $code non classifiée comme blocage connu.$suffixeWaf",
            estUnBonSigneSecurite = false
        )
    }
}
