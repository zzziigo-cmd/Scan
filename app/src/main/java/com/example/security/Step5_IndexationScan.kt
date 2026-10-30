package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ============================================================
// ÉTAPE 5 : CHEMINS D'INDEXATION
// Entrée  : PhpBehaviorReport (sortie de l'étape 4)
// Sortie  : IndexationReport (entrée de l'étape 6 : rapport final)
//
// Portée volontairement limitée à des fichiers STANDARD et PUBLICS par
// convention du web (robots.txt, sitemap.xml) + test de directory listing
// sur les dossiers que robots.txt lui-même révèle. On ne devine pas de
// chemins au hasard : on lit ce que le site déclare publiquement.
// ============================================================

data class CheminDisallow(val chemin: String, val sourceCommentaire: String?)

data class IndexationReport(
    val domain: String,
    val cmsContexte: DetectedCms,
    val phpReportPrecedent: PhpBehaviorReport,
    val robotsPresent: Boolean,
    val sitemapPresent: Boolean,
    val cheminsDisallowSensibles: List<CheminDisallow>,
    val dossiersAvecListingOuvert: List<String>,
    val constats: List<Constat>
)

private val client = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .build()

// Mots-clés qui, s'ils apparaissent dans une ligne Disallow, méritent attention
// (ce n'est pas une faille en soi — c'est un INDICE à vérifier manuellement)
private val motsClesSensibles = listOf(
    "admin", "backup", "config", "private", "secret", ".git", ".env", "db", "sql", "test", "staging"
)

suspend fun analyserIndexation(
    rapportPrecedent: PhpBehaviorReport // vient directement de l'étape 4
): IndexationReport = withContext(Dispatchers.IO) {

    val domain = rapportPrecedent.domain
    val constats = mutableListOf<Constat>()

    // --- 1. robots.txt ---
    var robotsPresent = false
    val disallowSensibles = mutableListOf<CheminDisallow>()
    var robotsBody = ""
    var diagnosticRobots: DiagnosticBlocage? = null
    try {
        val resp = client.newCall(Request.Builder().url("https://$domain/robots.txt").get().build()).execute()
        if (resp.code == 200) {
            robotsPresent = true
            robotsBody = resp.body?.string() ?: ""
        } else if (resp.code in listOf(401, 403, 429, 503)) {
            diagnosticRobots = diagnostiquerCodeHttp(resp.code, resp.headers.toMultimap().mapValues { it.value.joinToString(" | ") })
        }
        resp.close()
    } catch (e: Exception) {
        diagnosticRobots = diagnostiquerExceptionReseau(e)
    }

    if (robotsPresent) {
        constats += Constat("robots.txt", Niveau.CONFORME, "Présent et lisible")
        robotsBody.lineSequence()
            .filter { it.trim().startsWith("Disallow:", ignoreCase = true) }
            .forEach { line ->
                val chemin = line.substringAfter(":").trim()
                if (chemin.isNotEmpty() && motsClesSensibles.any { chemin.contains(it, ignoreCase = true) }) {
                    disallowSensibles += CheminDisallow(chemin, "trouvé dans robots.txt")
                }
            }
        if (disallowSensibles.isNotEmpty()) {
            constats += Constat(
                "Chemins sensibles révélés par robots.txt",
                Niveau.AVERTISSEMENT,
                "${disallowSensibles.size} chemin(s) exclus de l'indexation mais révélés publiquement : " +
                    disallowSensibles.joinToString(", ") { it.chemin } +
                    " — robots.txt n'empêche pas l'accès direct, seulement l'indexation par les moteurs"
            )
        }
    } else if (diagnosticRobots != null) {
        constats += Constat(
            "robots.txt",
            if (diagnosticRobots.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.INDETERMINE,
            "Non accessible — ${diagnosticRobots.explicationJustifiee}"
        )
    } else {
        constats += Constat("robots.txt", Niveau.INDETERMINE, "Absent (HTTP 404) — pas une faille, mais aucune politique d'indexation déclarée")
    }

    // --- 2. sitemap.xml (souvent référencé dans robots.txt, sinon chemin standard) ---
    val sitemapUrlDeclare = robotsBody.lineSequence()
        .firstOrNull { it.trim().startsWith("Sitemap:", ignoreCase = true) }
        ?.substringAfter(":", "")
        ?.trim()

    val sitemapUrl = sitemapUrlDeclare?.takeIf { it.isNotEmpty() } ?: "https://$domain/sitemap.xml"
    var sitemapPresent = false
    var diagnosticSitemap: DiagnosticBlocage? = null
    try {
        val resp = client.newCall(Request.Builder().url(sitemapUrl).get().build()).execute()
        sitemapPresent = resp.code == 200
        if (!sitemapPresent && resp.code in listOf(401, 403, 429, 503)) {
            diagnosticSitemap = diagnostiquerCodeHttp(resp.code, resp.headers.toMultimap().mapValues { it.value.joinToString(" | ") })
        }
        resp.close()
    } catch (e: Exception) {
        diagnosticSitemap = diagnostiquerExceptionReseau(e)
    }

    constats += when {
        sitemapPresent -> Constat("sitemap.xml", Niveau.CONFORME, "Présent à $sitemapUrl")
        diagnosticSitemap != null -> Constat(
            "sitemap.xml",
            if (diagnosticSitemap.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.INDETERMINE,
            "Non accessible — ${diagnosticSitemap.explicationJustifiee}"
        )
        else -> Constat("sitemap.xml", Niveau.INDETERMINE, "Non trouvé (HTTP 404) — SEO sous-optimal, pas un risque de sécurité")
    }

    // --- 3. Directory listing : on teste UNIQUEMENT les dossiers que robots.txt a lui-même révélés,
    //        jamais une liste devinée à l'aveugle (cohérent avec le principe du pipeline) ---
    val dossiersOuverts = mutableListOf<String>()
    val dossiersBloques = mutableListOf<Pair<String, DiagnosticBlocage>>()
    for (entry in disallowSensibles) {
        val cheminDossier = if (entry.chemin.endsWith("/")) entry.chemin else "$entry.chemin/".let { entry.chemin + "/" }
        try {
            val resp = client.newCall(Request.Builder().url("https://$domain$cheminDossier").get().build()).execute()
            val code = resp.code
            val body = if (code == 200) resp.body?.string() ?: "" else ""
            val headers = resp.headers.toMultimap().mapValues { it.value.joinToString(" | ") }
            resp.close()

            val ressembleAUnListing = code == 200 &&
                (body.contains("Index of", ignoreCase = true) || body.contains("<title>Index of", ignoreCase = true))
            if (ressembleAUnListing) {
                dossiersOuverts += cheminDossier
            } else if (code in listOf(401, 403, 429, 503)) {
                dossiersBloques += cheminDossier to diagnostiquerCodeHttp(code, headers)
            }
        } catch (e: Exception) {
            dossiersBloques += cheminDossier to diagnostiquerExceptionReseau(e)
        }
    }

    if (dossiersOuverts.isNotEmpty()) {
        constats += Constat(
            "Directory listing ouvert",
            Niveau.CRITIQUE,
            "Listing de fichiers accessible publiquement sur : ${dossiersOuverts.joinToString(", ")}"
        )
    }
    if (dossiersBloques.isNotEmpty()) {
        val tousBonSigne = dossiersBloques.all { it.second.estUnBonSigneSecurite }
        constats += Constat(
            "Dossiers protégés",
            if (tousBonSigne) Niveau.CONFORME else Niveau.INDETERMINE,
            dossiersBloques.joinToString("; ") { (chemin, diag) -> "$chemin : ${diag.explicationJustifiee}" }
        )
    }

    IndexationReport(
        domain = domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        phpReportPrecedent = rapportPrecedent,
        robotsPresent = robotsPresent,
        sitemapPresent = sitemapPresent,
        cheminsDisallowSensibles = disallowSensibles,
        dossiersAvecListingOuvert = dossiersOuverts,
        constats = constats
    )
}
