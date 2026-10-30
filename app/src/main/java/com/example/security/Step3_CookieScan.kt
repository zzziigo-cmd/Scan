package com.example.security

// ============================================================
// ÉTAPE 3 : SÉCURITÉ DES COOKIES
// Entrée  : SslHeadersReport (sortie de l'étape 2, contient rawHeaders)
// Sortie  : CookieReport (entrée de l'étape 4)
// ============================================================

data class CookieAnalyse(
    val nom: String,
    val valeurBrute: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val sameSite: String?, // "Strict", "Lax", "None", ou null si absent
    val constats: List<Constat>
)

data class CookieReport(
    val domain: String,
    val cmsContexte: DetectedCms,        // propagé depuis l'étape 1, via l'étape 2
    val sslHeadersPrecedent: SslHeadersReport, // on garde tout l'historique de la chaîne pour le rapport final
    val cookies: List<CookieAnalyse>,
    val constatsGlobaux: List<Constat>
)

fun scannerCookies(
    rapportPrecedent: SslHeadersReport // vient directement de l'étape 2
): CookieReport {

    // Chaque élément de setCookieHeaders est déjà un cookie individuel et complet
    // (nom=valeur; Secure; HttpOnly; ...), capturé proprement à l'étape 2 via
    // response.headers.values("Set-Cookie") — pas de découpage ambigu à faire ici.
    val cookies = rapportPrecedent.setCookieHeaders.map { raw -> analyserUnCookie(raw) }

    val constatsGlobaux = mutableListOf<Constat>()
    if (cookies.isEmpty()) {
        constatsGlobaux += Constat(
            "Cookies",
            Niveau.INDETERMINE,
            "Aucun cookie détecté sur la réponse d'accueil (peut être normal selon le site)"
        )
    } else {
        val sansSecure = cookies.count { !it.secure }
        val sansHttpOnly = cookies.count { !it.httpOnly }
        if (sansSecure > 0) {
            constatsGlobaux += Constat(
                "Attribut Secure manquant",
                Niveau.AVERTISSEMENT,
                "$sansSecure cookie(s) sans l'attribut Secure (transmissible en HTTP non chiffré)"
            )
        }
        if (sansHttpOnly > 0) {
            constatsGlobaux += Constat(
                "Attribut HttpOnly manquant",
                Niveau.AVERTISSEMENT,
                "$sansHttpOnly cookie(s) accessibles via JavaScript (risque XSS accru)"
            )
        }
    }

    // Contextualisation CMS, propagée depuis l'étape 1
    if (rapportPrecedent.cmsContexte == DetectedCms.LARAVEL) {
        val sessionCookie = cookies.firstOrNull { it.nom.contains("laravel_session", ignoreCase = true) }
        if (sessionCookie != null && !sessionCookie.secure) {
            constatsGlobaux += Constat(
                "Session Laravel non sécurisée",
                Niveau.CRITIQUE,
                "laravel_session sans Secure — risque de vol de session sur réseau non chiffré"
            )
        }
    }

    return CookieReport(
        domain = rapportPrecedent.domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        sslHeadersPrecedent = rapportPrecedent,
        cookies = cookies,
        constatsGlobaux = constatsGlobaux
    )
}

private fun analyserUnCookie(raw: String): CookieAnalyse {
    val parts = raw.split(";").map { it.trim() }
    val nomValeur = parts.firstOrNull()?.split("=", limit = 2) ?: listOf("inconnu", "")
    val nom = nomValeur.getOrElse(0) { "inconnu" }
    val valeur = nomValeur.getOrElse(1) { "" }

    val attributs = parts.drop(1).map { it.lowercase() }
    val secure = attributs.any { it == "secure" }
    val httpOnly = attributs.any { it == "httponly" }
    val sameSite = attributs.firstOrNull { it.startsWith("samesite") }
        ?.substringAfter("=", missingDelimiterValue = "lax")
        ?.replaceFirstChar { it.uppercase() }

    val constats = mutableListOf<Constat>()
    if (!secure) constats += Constat("Secure", Niveau.AVERTISSEMENT, "Absent sur le cookie \"$nom\"")
    if (!httpOnly) constats += Constat("HttpOnly", Niveau.AVERTISSEMENT, "Absent sur le cookie \"$nom\"")
    if (sameSite == null) constats += Constat("SameSite", Niveau.AVERTISSEMENT, "Non défini sur le cookie \"$nom\"")

    return CookieAnalyse(
        nom = nom,
        valeurBrute = valeur,
        secure = secure,
        httpOnly = httpOnly,
        sameSite = sameSite,
        constats = constats
    )
}
