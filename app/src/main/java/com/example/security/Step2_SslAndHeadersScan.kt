package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException

// ============================================================
// ÉTAPE 2 : SSL/TLS + EN-TÊTES DE SÉCURITÉ
// Entrée  : CmsDetectionResult (sortie de l'étape 1)
// Sortie  : SslHeadersReport (entrée de l'étape 3 : cookies)
// ============================================================

enum class Niveau { CONFORME, AVERTISSEMENT, CRITIQUE, INDETERMINE }

data class Constat(
    val titre: String,
    val niveau: Niveau,
    val details: String
)

data class SslHeadersReport(
    val domain: String,
    val cmsContexte: DetectedCms,       // reçu de l'étape 1, utile pour contextualiser le rapport final
    val constatsSsl: List<Constat>,
    val constatsHeaders: List<Constat>,
    // on transmet aussi les en-têtes bruts : l'étape 3 (cookies) en a besoin
    val rawHeaders: Map<String, String>,
    // Set-Cookie est gardé à part, car OkHttp peut renvoyer PLUSIEURS valeurs pour ce même
    // en-tête (un par cookie), et chaque valeur contient elle-même des ";" pour ses attributs
    // (Secure, HttpOnly...). Les fusionner dans rawHeaders rendrait le découpage ambigu.
    val setCookieHeaders: List<String>
)

private val headersASecuriteObligatoires = listOf(
    "Strict-Transport-Security",
    "Content-Security-Policy",
    "X-Content-Type-Options",
    "X-Frame-Options",
    "Referrer-Policy"
)

suspend fun scannerSslEtHeaders(
    domain: String,
    cmsDetecte: CmsDetectionResult // vient directement de l'étape 1
): SslHeadersReport = withContext(Dispatchers.IO) {

    val constatsSsl = mutableListOf<Constat>()
    val constatsHeaders = mutableListOf<Constat>()
    var rawHeaders: Map<String, String> = emptyMap()
    var setCookieHeaders: List<String> = emptyList()

    // --- Partie SSL/TLS ---
    try {
        val url = java.net.URL("https://$domain")
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 4000
        connection.connect()

        val cert = connection.serverCertificates.firstOrNull() as? X509Certificate
        if (cert != null) {
            val expireDans = (cert.notAfter.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
            constatsSsl += when {
                expireDans < 0 -> Constat("Certificat SSL", Niveau.CRITIQUE, "Certificat expiré")
                expireDans < 15 -> Constat("Certificat SSL", Niveau.AVERTISSEMENT, "Expire dans $expireDans jour(s)")
                else -> Constat("Certificat SSL", Niveau.CONFORME, "Valide, expire dans $expireDans jours")
            }
            constatsSsl += Constat("Émetteur certificat", Niveau.CONFORME, cert.issuerX500Principal.name)
        }

        constatsSsl += Constat("Protocole TLS", Niveau.CONFORME, connection.cipherSuite)
        connection.disconnect()
    } catch (e: SSLPeerUnverifiedException) {
        constatsSsl += Constat("Certificat SSL", Niveau.CRITIQUE, "Certificat invalide ou non reconnu par une autorité de confiance : ${e.localizedMessage}")
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constatsSsl += Constat(
            "Connexion SSL",
            if (diagnostic.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.INDETERMINE,
            diagnostic.explicationJustifiee
        )
    }

    // --- Partie en-têtes de sécurité ---
    // Toujours via OkHttp : contrairement à HttpsURLConnection, response.headers.values("Set-Cookie")
    // conserve proprement chaque cookie séparément, sans les fusionner de façon ambiguë.
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        val response = client.newCall(Request.Builder().url("https://$domain").get().build()).execute()
        rawHeaders = response.headers.names()
            .associateWith { name -> response.headers.values(name).joinToString(" | ") }
        setCookieHeaders = response.headers.values("Set-Cookie")
        response.close()
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constatsHeaders += Constat(
            "Récupération des en-têtes",
            if (diagnostic.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.INDETERMINE,
            diagnostic.explicationJustifiee
        )
    }

    for (headerName in headersASecuriteObligatoires) {
        val present = rawHeaders.keys.any { it.equals(headerName, ignoreCase = true) }
        constatsHeaders += if (present) {
            Constat(headerName, Niveau.CONFORME, "En-tête présent")
        } else {
            Constat(headerName, Niveau.AVERTISSEMENT, "En-tête absent de la réponse")
        }
    }

    // Contextualisation selon le CMS détecté à l'étape 1 :
    // certains CMS ont des recommandations spécifiques d'en-têtes
    if (cmsDetecte.cms == DetectedCms.WORDPRESS) {
        val xPoweredBy = rawHeaders.entries.firstOrNull { it.key.equals("X-Powered-By", true) }
        if (xPoweredBy != null) {
            constatsHeaders += Constat(
                "Fuite d'information",
                Niveau.AVERTISSEMENT,
                "En-tête X-Powered-By expose la version PHP (recommandé de le masquer pour WordPress)"
            )
        }
    }

    SslHeadersReport(
        domain = domain,
        cmsContexte = cmsDetecte.cms,
        constatsSsl = constatsSsl,
        constatsHeaders = constatsHeaders,
        rawHeaders = rawHeaders,
        setCookieHeaders = setCookieHeaders
    )
}
