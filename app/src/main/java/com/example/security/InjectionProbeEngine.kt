package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// ============================================================
// SONDES DE TEST D'ENTRÉE — catégorie OWASP A03 (Injection)
//
// PRINCIPE DE SÉCURITÉ DE CE MODULE (lire avant de modifier) :
// - Chaque entrée n'est testée qu'UNE SEULE FOIS par type de sonde
//   (pas de liste de payloads répétée, pas de force brute)
// - Le marqueur XSS est un texte INOFFENSIF même s'il s'exécute :
//   pas de <script>, pas d'alert(), pas de vol de cookie — on détecte
//   uniquement si le marqueur ressort NON ÉCHAPPÉ dans le HTML, ce qui
//   suffit à prouver la vulnérabilité sans l'exploiter.
// - La sonde SQL n'injecte qu'une apostrophe isolée et lit la réponse :
//   elle ne tente JAMAIS d'extraire de données, de contourner une
//   authentification, ni d'utiliser UNION/boolean-blind/time-based.
// - Nombre d'entrées testées plafonné + rate limiting, comme le reste
//   du pipeline. Nécessite OwnershipGate comme toutes les autres étapes.
// ============================================================

enum class TypeProbe { REFLEXION_XSS, ERREUR_SQL }

data class ResultatProbe(
    val url: String,
    val champ: String,
    val typeProbe: TypeProbe,
    val vulnerable: Boolean,
    val explication: String
)

data class RapportInjection(
    val entreesTestees: Int,
    val resultats: List<ResultatProbe>,
    val constats: List<Constat>
)

private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

// Marqueur unique par exécution, pour éviter tout faux positif venant d'un
// contenu préexistant sur la page. Volontairement composé de caractères
// HTML spéciaux SANS former de balise exécutable : si on le retrouve tel
// quel (non échappé) dans le HTML, ça prouve l'absence d'échappement de
// sortie sans qu'aucun code ne s'exécute réellement.
private fun genererMarqueurXss(): String {
    val id = Random.nextInt(100000, 999999)
    return "XSSPROBE$id<\"'>"
}

// Une seule apostrophe : suffisant pour casser une requête SQL mal
// paramétrée et faire remonter un message d'erreur, sans tenter d'altérer
// ou d'extraire quoi que ce soit.
private const val MARQUEUR_SQL = "'"

private val signaturesErreurSql = listOf(
    "SQL syntax", "mysql_fetch", "You have an error in your SQL syntax",
    "Warning: mysqli", "ORA-01756", "ORA-00933", "SQLite3::", "SQLSTATE",
    "Unclosed quotation mark", "quoted string not properly terminated",
    "PostgreSQL query failed", "pg_query()"
)

/**
 * Teste toutes les entrées découvertes par le crawler (formulaires + paramètres
 * d'URL), avec un plafond pour rester raisonnable. Nécessite une propriété vérifiée.
 */
suspend fun testerEntreesUtilisateur(
    crawl: RapportCrawl,
    ownership: OwnershipCheck,
    maxEntreesTestees: Int = 20,
    delaiEntreRequetesMs: Long = 500
): RapportInjection = withContext(Dispatchers.IO) {

    require(ownership is OwnershipCheck.Verified) {
        "Test d'entrées refusé : propriété du domaine non vérifiée"
    }

    val entrees = collecterEntreesUniques(crawl).take(maxEntreesTestees)
    val resultats = mutableListOf<ResultatProbe>()

    for (entree in entrees) {
        delay(delaiEntreRequetesMs)
        resultats += testerReflexionXss(entree)
        delay(delaiEntreRequetesMs)
        resultats += testerErreurSql(entree)
    }

    val constats = construireConstats(resultats)

    RapportInjection(
        entreesTestees = entrees.size,
        resultats = resultats,
        constats = constats
    )
}

private data class EntreeATester(val url: String, val methode: String, val champ: String)

private fun collecterEntreesUniques(crawl: RapportCrawl): List<EntreeATester> {
    val entrees = mutableListOf<EntreeATester>()

    crawl.pages.forEach { page ->
        page.formulaires.forEach { form ->
            form.champs.forEach { champ ->
                entrees += EntreeATester(form.action, form.methode, champ)
            }
        }
        page.parametresUrl.forEach { param ->
            entrees += EntreeATester(page.url, "GET", param)
        }
    }

    // Déduplique : même (url, champ) testé une seule fois même si rencontré sur plusieurs pages
    return entrees.distinctBy { "${it.url}|${it.champ}" }
}

private suspend fun testerReflexionXss(entree: EntreeATester): ResultatProbe {
    val marqueur = genererMarqueurXss()
    val body = executerRequeteAvecValeur(entree, marqueur) ?: return ResultatProbe(
        entree.url, entree.champ, TypeProbe.REFLEXION_XSS, false,
        "Requête de test non concluante (page inaccessible)"
    )

    // Vulnérable si le marqueur apparaît TEL QUEL (caractères < " ' > non échappés).
    // S'il apparaît encodé (&lt; &quot; etc.), l'échappement de sortie fonctionne correctement.
    val reflechiNonEchappe = body.contains(marqueur)

    return ResultatProbe(
        url = entree.url,
        champ = entree.champ,
        typeProbe = TypeProbe.REFLEXION_XSS,
        vulnerable = reflechiNonEchappe,
        explication = if (reflechiNonEchappe) {
            "Le champ \"${entree.champ}\" renvoie l'entrée utilisateur SANS échappement HTML — une faille XSS réfléchie est probable à cet endroit."
        } else {
            "Le champ \"${entree.champ}\" semble correctement échappé en sortie (aucun caractère spécial non échappé détecté)."
        }
    )
}

private suspend fun testerErreurSql(entree: EntreeATester): ResultatProbe {
    val body = executerRequeteAvecValeur(entree, MARQUEUR_SQL) ?: return ResultatProbe(
        entree.url, entree.champ, TypeProbe.ERREUR_SQL, false,
        "Requête de test non concluante (page inaccessible)"
    )

    val signatureTrouvee = signaturesErreurSql.firstOrNull { body.contains(it, ignoreCase = true) }

    return ResultatProbe(
        url = entree.url,
        champ = entree.champ,
        typeProbe = TypeProbe.ERREUR_SQL,
        vulnerable = signatureTrouvee != null,
        explication = if (signatureTrouvee != null) {
            "Le champ \"${entree.champ}\" a déclenché un message d'erreur de base de données (\"$signatureTrouvee\") suite à l'envoi d'une simple apostrophe — signe que l'entrée n'est pas correctement paramétrée dans la requête SQL."
        } else {
            "Le champ \"${entree.champ}\" n'a pas déclenché d'erreur SQL visible avec ce test (ne garantit pas l'absence totale d'injection, seulement l'absence d'erreur exposée)."
        }
    )
}

private fun executerRequeteAvecValeur(entree: EntreeATester, valeur: String): String? {
    return try {
        val requete = if (entree.methode.equals("POST", ignoreCase = true)) {
            val formBody = FormBody.Builder().add(entree.champ, valeur).build()
            Request.Builder().url(entree.url).post(formBody).build()
        } else {
            val uri = URI(entree.url)
            val query = (uri.query?.let { "$it&" } ?: "") + "${entree.champ}=${java.net.URLEncoder.encode(valeur, "UTF-8")}"
            val urlAvecParam = URI(uri.scheme, uri.authority, uri.path, query, uri.fragment).toString()
            Request.Builder().url(urlAvecParam).get().build()
        }
        val resp = client.newCall(requete).execute()
        val corps = resp.body?.string()
        resp.close()
        corps
    } catch (_: Exception) {
        null
    }
}

private fun construireConstats(resultats: List<ResultatProbe>): List<Constat> {
    val constats = mutableListOf<Constat>()

    val xssVulnerables = resultats.filter { it.typeProbe == TypeProbe.REFLEXION_XSS && it.vulnerable }
    if (xssVulnerables.isNotEmpty()) {
        constats += Constat(
            "Réflexion XSS détectée",
            Niveau.CRITIQUE,
            xssVulnerables.joinToString("; ") { "${it.champ} (${it.url})" }
        )
    } else {
        constats += Constat("Réflexion XSS", Niveau.CONFORME, "Aucune réflexion non échappée détectée sur les entrées testées")
    }

    val sqlVulnerables = resultats.filter { it.typeProbe == TypeProbe.ERREUR_SQL && it.vulnerable }
    if (sqlVulnerables.isNotEmpty()) {
        constats += Constat(
            "Erreur SQL exposée",
            Niveau.CRITIQUE,
            sqlVulnerables.joinToString("; ") { "${it.champ} (${it.url})" }
        )
    } else {
        constats += Constat("Erreur SQL exposée", Niveau.CONFORME, "Aucune erreur SQL déclenchée sur les entrées testées")
    }

    return constats
}
