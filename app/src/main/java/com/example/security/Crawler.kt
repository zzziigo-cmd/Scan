package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

// ============================================================
// CRAWLER — explore l'application depuis l'URL de démarrage
//
// Portée (alignée sur le comportement d'une analyse "gérée" type
// Google Cloud Web Security Scanner) :
//  - Ne suit que les liens du MÊME domaine que l'URL de départ
//  - Se limite aux ports par défaut (80 HTTP / 443 HTTPS) ; un port
//    personnalisé doit être traité comme une "analyse personnalisée"
//    séparée (non couverte ici, à faire au cas par cas)
//  - Borné en profondeur et en nombre de pages pour rester raisonnable
//  - Repère les formulaires (entrées utilisateur) et les paramètres
//    d'URL rencontrés, pour alimenter l'étape suivante (tests d'entrée)
//  - Toujours soumis à OwnershipGate : aucune exploration sans
//    propriété vérifiée
// ============================================================

data class PageExploree(
    val url: String,
    val statusCode: Int,
    val formulaires: List<FormulaireDetecte>,
    val parametresUrl: List<String> // noms des paramètres de query string trouvés sur cette page
)

data class FormulaireDetecte(
    val action: String,          // URL cible du formulaire (résolue en absolu)
    val methode: String,         // GET ou POST
    val champs: List<String>     // noms des champs d'entrée (name="...")
)

data class RapportCrawl(
    val domain: String,
    val portUtilise: Int,
    val estPortParDefaut: Boolean,
    val pages: List<PageExploree>,
    val totalEntreesUtilisateurTrouvees: Int
)

private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

/**
 * Explore l'application à partir de l'URL de démarrage.
 * Nécessite OwnershipCheck.Verified — aucune exploration sans propriété confirmée.
 */
suspend fun explorerApplication(
    urlDemarrage: String,
    ownership: OwnershipCheck,
    profondeurMax: Int = 3,
    pagesMax: Int = 40,
    delaiEntreRequetesMs: Long = 300
): RapportCrawl = withContext(Dispatchers.IO) {

    require(ownership is OwnershipCheck.Verified) {
        "Exploration refusée : propriété du domaine non vérifiée"
    }

    val uriDepart = URI(urlDemarrage)
    val domaineCible = uriDepart.host
    val port = if (uriDepart.port != -1) uriDepart.port else if (uriDepart.scheme == "https") 443 else 80
    val estPortParDefaut = port == 80 || port == 443

    // Si le port n'est pas standard, on ne lance PAS l'exploration automatique :
    // conforme à la contrainte "analyse personnalisée requise" plutôt que managée.
    if (!estPortParDefaut) {
        return@withContext RapportCrawl(
            domain = domaineCible,
            portUtilise = port,
            estPortParDefaut = false,
            pages = emptyList(),
            totalEntreesUtilisateurTrouvees = 0
        )
    }

    val aVisiter = ArrayDeque<Pair<String, Int>>() // url, profondeur
    val visitees = mutableSetOf<String>()
    val pagesExplorees = mutableListOf<PageExploree>()

    aVisiter.add(urlDemarrage to 0)

    while (aVisiter.isNotEmpty() && pagesExplorees.size < pagesMax) {
        val (urlCourante, profondeur) = aVisiter.removeFirst()
        val urlNormalisee = normaliserUrl(urlCourante)
        if (urlNormalisee in visitees) continue
        visitees += urlNormalisee

        delay(delaiEntreRequetesMs) // rate limiting, cohérent avec le reste du pipeline

        try {
            val resp = client.newCall(Request.Builder().url(urlCourante).get().build()).execute()
            val code = resp.code
            val body = if (code in 200..299) resp.body?.string() ?: "" else ""
            resp.close()

            val formulaires = extraireFormulaires(body, urlCourante)
            val parametres = extraireParametresUrl(urlCourante)

            pagesExplorees += PageExploree(urlCourante, code, formulaires, parametres)

            if (profondeur < profondeurMax) {
                val liensInternes = extraireLiensInternes(body, urlCourante, domaineCible)
                liensInternes.forEach { lien ->
                    if (normaliserUrl(lien) !in visitees) {
                        aVisiter.add(lien to profondeur + 1)
                    }
                }
            }
        } catch (_: Exception) {
            // Page inaccessible pendant le crawl — on continue l'exploration des autres pages
            // (un diagnostic par page individuelle alourdirait le rapport de crawl ; le diagnostic
            // détaillé reste utile au niveau des étapes de scan ciblées, pas ici)
        }
    }

    val totalEntrees = pagesExplorees.sumOf { page ->
        page.formulaires.sumOf { it.champs.size } + page.parametresUrl.size
    }

    RapportCrawl(
        domain = domaineCible,
        portUtilise = port,
        estPortParDefaut = true,
        pages = pagesExplorees,
        totalEntreesUtilisateurTrouvees = totalEntrees
    )
}

private fun normaliserUrl(url: String): String =
    url.substringBefore("#").trimEnd('/')

private fun extraireLiensInternes(html: String, urlBase: String, domaineCible: String): List<String> {
    val regexLiens = Regex("""href=["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
    return regexLiens.findAll(html).mapNotNull { match ->
        val href = match.groupValues[1]
        try {
            val resolu = URI(urlBase).resolve(href).toString()
            val uriResolu = URI(resolu)
            if (uriResolu.host == domaineCible && (uriResolu.scheme == "http" || uriResolu.scheme == "https")) {
                resolu
            } else null
        } catch (_: Exception) { null }
    }.distinct().toList()
}

private fun extraireFormulaires(html: String, urlPage: String): List<FormulaireDetecte> {
    val regexForm = Regex("""<form[^>]*action=["']?([^"'\s>]*)["']?[^>]*method=["']?(\w+)["']?[^>]*>(.*?)</form>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val regexFormAlt = Regex("""<form[^>]*method=["']?(\w+)["']?[^>]*action=["']?([^"'\s>]*)["']?[^>]*>(.*?)</form>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val regexChamp = Regex("""<input[^>]*name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    val resultats = mutableListOf<FormulaireDetecte>()

    (regexForm.findAll(html).map { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } +
     regexFormAlt.findAll(html).map { Triple(it.groupValues[2], it.groupValues[1], it.groupValues[3]) })
        .forEach { (action, methode, contenu) ->
            val actionAbsolue = try { URI(urlPage).resolve(action.ifBlank { urlPage }).toString() } catch (_: Exception) { urlPage }
            val champs = regexChamp.findAll(contenu).map { it.groupValues[1] }.distinct().toList()
            if (champs.isNotEmpty()) {
                resultats += FormulaireDetecte(actionAbsolue, methode.uppercase().ifBlank { "GET" }, champs)
            }
        }

    return resultats
}

private fun extraireParametresUrl(url: String): List<String> =
    try {
        URI(url).query?.split("&")?.mapNotNull { it.substringBefore("=", "").takeIf { p -> p.isNotBlank() } } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
