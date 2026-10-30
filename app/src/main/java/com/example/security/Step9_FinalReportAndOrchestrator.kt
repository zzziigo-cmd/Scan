package com.example.security

// ============================================================
// ÉTAPE 9 : RAPPORT FINAL
// Entrée  : AdminPageReport (qui contient, via la chaîne de "precedent",
//           TOUT l'historique des 8 étapes précédentes)
// Sortie  : RapportGlobal — équivalent du dashboard (score + répartition)
// ============================================================

data class CategorieScore(
    val nom: String,
    val conformes: Int,
    val avertissements: Int,
    val critiques: Int
) {
    val total get() = conformes + avertissements + critiques
}

data class RapportGlobal(
    val domain: String,
    val cmsDetecte: DetectedCms,
    val scoreSur100: Int,
    val totalConstats: Int,
    val totalCritiques: Int,
    val totalAvertissements: Int,
    val totalConformes: Int,
    val categories: List<CategorieScore>,
    val tousLesConstats: List<Pair<String, Constat>> // (nom de l'étape, constat)
)

fun genererRapportFinal(adminReport: AdminPageReport): RapportGlobal {
    // On remonte toute la chaîne depuis la fin — chaque étape garde une référence à la précédente
    val serverReport = adminReport.serverReportPrecedent
    val mysqlReport = serverReport.mysqlReportPrecedent
    val indexationReport = mysqlReport.indexationReportPrecedent
    val phpReport = indexationReport.phpReportPrecedent
    val cookieReport = phpReport.cookieReportPrecedent
    val sslHeadersReport = cookieReport.sslHeadersPrecedent
    // cmsReport n'est pas stocké comme "precedent" (c'est la toute première étape),
    // on le retrouve via cmsContexte propagé partout dans la chaîne

    val parEtape: List<Pair<String, List<Constat>>> = listOf(
        "SSL / TLS" to sslHeadersReport.constatsSsl,
        "En-têtes de sécurité" to sslHeadersReport.constatsHeaders,
        "Cookies" to cookieReport.constatsGlobaux,
        "Comportement PHP" to phpReport.constats,
        "Indexation" to indexationReport.constats,
        "Exposition MySQL" to mysqlReport.constats,
        "Serveur & Réseau" to serverReport.constats,
        "Page d'administration" to adminReport.constats
    )

    val tousLesConstats = parEtape.flatMap { (nomEtape, constats) -> constats.map { nomEtape to it } }

    val totalCritiques = tousLesConstats.count { it.second.niveau == Niveau.CRITIQUE }
    val totalAvertissements = tousLesConstats.count { it.second.niveau == Niveau.AVERTISSEMENT }
    val totalConformes = tousLesConstats.count { it.second.niveau == Niveau.CONFORME }
    val total = tousLesConstats.size

    // Scoring simple et transparent : chaque critique pèse plus qu'un avertissement.
    // Formule volontairement explicable (pas de boîte noire) pour la soutenance.
    val penalite = (totalCritiques * 10) + (totalAvertissements * 4)
    val scoreSur100 = (100 - penalite).coerceIn(0, 100)

    val categories = parEtape.map { (nom, constats) ->
        CategorieScore(
            nom = nom,
            conformes = constats.count { it.niveau == Niveau.CONFORME },
            avertissements = constats.count { it.niveau == Niveau.AVERTISSEMENT },
            critiques = constats.count { it.niveau == Niveau.CRITIQUE }
        )
    }

    return RapportGlobal(
        domain = adminReport.domain,
        cmsDetecte = adminReport.cmsContexte,
        scoreSur100 = scoreSur100,
        totalConstats = total,
        totalCritiques = totalCritiques,
        totalAvertissements = totalAvertissements,
        totalConformes = totalConformes,
        categories = categories,
        tousLesConstats = tousLesConstats
    )
}

// ============================================================
// ORCHESTRATEUR — enchaîne les 9 étapes dans l'ordre, chacune
// nourrissant la suivante avec sa propre sortie typée.
// ============================================================

sealed class PipelineResult {
    data class Success(val rapport: RapportGlobal) : PipelineResult()
    data class Blocked(val raison: String) : PipelineResult()
}

data class RapportComplet(
    val rapportGlobal: RapportGlobal,
    val rapportCrawl: RapportCrawl,
    val rapportInjection: RapportInjection?
)

sealed class PipelineCompletResult {
    data class Success(val rapport: RapportComplet) : PipelineCompletResult()
    data class Blocked(val raison: String) : PipelineCompletResult()
}

/**
 * Version étendue de runPipelineComplet qui explore d'abord l'application
 * (Crawler.kt) pour repérer les entrées utilisateur (formulaires, paramètres),
 * en plus du pipeline de scan habituel. Si le port de l'URL de démarrage
 * n'est pas 80/443, le crawl est sauté (estPortParDefaut = false dans le
 * résultat) et seul le pipeline de scan standard s'exécute.
 */
suspend fun runPipelineCompletAvecCrawl(
    urlDemarrage: String,
    domain: String,
    ownership: OwnershipCheck
): PipelineCompletResult {

    if (ownership !is OwnershipCheck.Verified) {
        return PipelineCompletResult.Blocked(
            "Scan refusé : propriété du domaine non vérifiée (${(ownership as? OwnershipCheck.Failed)?.reason ?: "statut inconnu"})"
        )
    }

    val crawl = explorerApplication(urlDemarrage, ownership)

    // Tests d'entrée uniquement si le crawl a trouvé des entrées ET que le port
    // était standard (sinon crawl.pages est vide et il n'y a rien à tester)
    val rapportInjection = if (crawl.totalEntreesUtilisateurTrouvees > 0) {
        testerEntreesUtilisateur(crawl, ownership)
    } else null

    val resultatPipeline = runPipelineComplet(domain, ownership)
    return when (resultatPipeline) {
        is PipelineResult.Blocked -> PipelineCompletResult.Blocked(resultatPipeline.raison)
        is PipelineResult.Success -> PipelineCompletResult.Success(
            RapportComplet(
                rapportGlobal = resultatPipeline.rapport,
                rapportCrawl = crawl,
                rapportInjection = rapportInjection
            )
        )
    }
}

suspend fun runPipelineComplet(
    domain: String,
    ownership: OwnershipCheck
): PipelineResult {

    // La porte d'entrée : rien ne s'exécute sans propriété vérifiée
    if (ownership !is OwnershipCheck.Verified) {
        return PipelineResult.Blocked(
            "Scan refusé : propriété du domaine non vérifiée (${(ownership as? OwnershipCheck.Failed)?.reason ?: "statut inconnu"})"
        )
    }

    // Étape 1 : détection CMS — on récupère html/headers une seule fois pour nourrir detectCms
    val (htmlBody, headers) = fetchHomePage(domain)
    val cmsResult = detectCms(htmlBody, headers)

    // Étape 2 → 8 : chaque fonction prend la sortie typée de la précédente
    val sslHeaders = scannerSslEtHeaders(domain, cmsResult)
    val cookies = scannerCookies(sslHeaders)
    val php = analyserComportementPhp(cookies)
    val indexation = analyserIndexation(php)
    val mysql = verifierExpositionMysql(indexation)
    val serveurReseau = analyserServeurEtReseau(mysql)
    val admin = verifierAccessibiliteAdmin(serveurReseau, ownership)

    // Étape 9 : assemblage final
    val rapportFinal = genererRapportFinal(admin)

    return PipelineResult.Success(rapportFinal)
}

private suspend fun fetchHomePage(domain: String): Pair<String, Map<String, String>> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val resp = client.newCall(okhttp3.Request.Builder().url("https://$domain").get().build()).execute()
        val body = resp.body?.string() ?: ""
        val headers = resp.headers.toMultimap().mapValues { it.value.joinToString(";") }
        resp.close()
        body to headers
    }
