package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ============================================================
// ÉTAPE 4 : COMPORTEMENT PHP
// Entrée  : CookieReport (sortie de l'étape 3)
// Sortie  : PhpBehaviorReport (entrée de l'étape 5 : rapport final)
//
// Principe : on n'exécute cette étape QUE si le site tourne probablement
// en PHP — sinon on la saute proprement (logique contextuelle, pas de
// test hors sujet comme discuté plus tôt).
// ============================================================

data class PhpBehaviorReport(
    val domain: String,
    val cmsContexte: DetectedCms,
    val cookieReportPrecedent: CookieReport,
    val phpDetecte: Boolean,
    val versionExposee: String?,
    val constats: List<Constat>
)

private val client = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .build()

suspend fun analyserComportementPhp(
    rapportPrecedent: CookieReport // vient directement de l'étape 3
): PhpBehaviorReport = withContext(Dispatchers.IO) {

    val headers = rapportPrecedent.sslHeadersPrecedent.rawHeaders
    val constats = mutableListOf<Constat>()
    var phpDetecte = false
    var versionExposee: String? = null

    // --- 1. Détection PHP via en-têtes (signal passif, zéro requête supplémentaire) ---
    val xPoweredBy = headers.entries.firstOrNull { it.key.equals("X-Powered-By", ignoreCase = true) }?.value
    if (xPoweredBy != null && xPoweredBy.contains("PHP", ignoreCase = true)) {
        phpDetecte = true
        val versionMatch = Regex("""PHP/([\d.]+)""").find(xPoweredBy)
        versionExposee = versionMatch?.groupValues?.get(1)
        constats += if (versionExposee != null) {
            Constat(
                "Version PHP exposée",
                Niveau.AVERTISSEMENT,
                "PHP $versionExposee visible dans X-Powered-By — facilite le ciblage de failles connues pour cette version"
            )
        } else {
            Constat("PHP détecté", Niveau.CONFORME, "Présence confirmée mais version masquée")
        }
    }

    // Indice secondaire : cookie PHPSESSID = PHP très probable même sans X-Powered-By
    if (!phpDetecte) {
        val hasPhpSession = rapportPrecedent.cookies.any { it.nom.equals("PHPSESSID", ignoreCase = true) }
        if (hasPhpSession) {
            phpDetecte = true
            constats += Constat("PHP détecté", Niveau.CONFORME, "Déduit du cookie PHPSESSID (X-Powered-By masqué)")
        }
    }

    // Si PHP non détecté du tout, on s'arrête ici — pas de test hors contexte
    if (!phpDetecte) {
        return@withContext PhpBehaviorReport(
            domain = rapportPrecedent.domain,
            cmsContexte = rapportPrecedent.cmsContexte,
            cookieReportPrecedent = rapportPrecedent,
            phpDetecte = false,
            versionExposee = null,
            constats = listOf(Constat("Analyse PHP", Niveau.CONFORME, "Site non identifié comme PHP — tests PHP ignorés"))
        )
    }

    // --- 2. Test passif : affichage d'erreurs verbeuses (display_errors) ---
    // On sollicite un paramètre volontairement malformé sur l'URL existante (pas de chemin caché testé,
    // juste la page d'accueil avec un paramètre bidon) pour voir si PHP affiche une stack trace.
    try {
        val probeUrl = "https://${rapportPrecedent.domain}/?debug_probe[]=1"
        val response = client.newCall(Request.Builder().url(probeUrl).get().build()).execute()
        val body = response.body?.string() ?: ""
        response.close()

        val indicesErreurVerbose = listOf(
            "Fatal error", "Warning: ", "Notice: ", "Deprecated: ",
            "on line", "Stack trace:", "<b>Fatal error</b>"
        )
        val match = indicesErreurVerbose.firstOrNull { body.contains(it) }
        if (match != null) {
            constats += Constat(
                "Erreurs PHP verbeuses",
                Niveau.CRITIQUE,
                "Le serveur affiche des messages d'erreur PHP détaillés (indice : \"$match\") — expose chemins serveur et structure du code. À corriger : display_errors = Off en production"
            )
        } else {
            constats += Constat("Erreurs PHP verbeuses", Niveau.CONFORME, "Aucun message d'erreur détaillé détecté sur la sonde")
        }
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constats += Constat(
            "Erreurs PHP verbeuses",
            if (diagnostic.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.INDETERMINE,
            "Sonde non concluante — ${diagnostic.explicationJustifiee}"
        )
    }

    // --- 3. Contextualisation CMS (propagée depuis l'étape 1) ---
    if (rapportPrecedent.cmsContexte == DetectedCms.WORDPRESS && versionExposee != null) {
        constats += Constat(
            "Priorité élevée",
            Niveau.AVERTISSEMENT,
            "Version PHP exposée sur un site WordPress : combinée à une version de plugin vulnérable, le risque de ciblage augmente"
        )
    }

    PhpBehaviorReport(
        domain = rapportPrecedent.domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        cookieReportPrecedent = rapportPrecedent,
        phpDetecte = true,
        versionExposee = versionExposee,
        constats = constats
    )
}
