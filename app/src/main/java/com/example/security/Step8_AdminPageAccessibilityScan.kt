package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ============================================================
// ÉTAPE 8 : ACCESSIBILITÉ DE LA PAGE D'ADMINISTRATION
// Entrée  : ServerNetworkReport (sortie de l'étape 7)
// Sortie  : AdminPageReport (entrée de l'étape 9 : rapport final)
//
// Réutilise pathsToTest(cms) de l'étape 1 : on ne teste QUE les chemins
// propres au CMS déjà détecté, jamais une liste générique devinée.
// On va plus loin que "200 = trouvé" (la source des faux positifs
// identifiés sur Webscan/orange.ma) : on inspecte le CONTENU pour
// distinguer une vraie page de connexion admin d'une page générique
// qui renvoie juste un 200 par défaut.
// ============================================================

enum class StatutAdmin { SECURISE, ACCESSIBLE_SANS_PROTECTION, NON_TROUVE, INDETERMINE }

data class AdminPathResult(
    val chemin: String,
    val urlComplete: String,
    val statusCode: Int,
    val statut: StatutAdmin,
    val raison: String
)

data class AdminPageReport(
    val domain: String,
    val cmsContexte: DetectedCms,
    val serverReportPrecedent: ServerNetworkReport,
    val resultats: List<AdminPathResult>,
    val constats: List<Constat>
)

private val client = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()

// Marqueurs de contenu qui confirment une VRAIE page de connexion admin
// (par opposition à une page d'accueil générique renvoyée en 200 par défaut)
private val marqueursPageConnexion = listOf(
    "type=\"password\"", "name=\"password\"", "login", "se connecter",
    "nom d'utilisateur", "username", "sign in"
)

suspend fun verifierAccessibiliteAdmin(
    rapportPrecedent: ServerNetworkReport, // vient directement de l'étape 7
    ownership: OwnershipCheck              // revérifié ici : porte d'entrée obligatoire, même tard dans le pipeline
): AdminPageReport = withContext(Dispatchers.IO) {

    val domain = rapportPrecedent.domain
    val constats = mutableListOf<Constat>()

    if (ownership !is OwnershipCheck.Verified) {
        return@withContext AdminPageReport(
            domain = domain,
            cmsContexte = rapportPrecedent.cmsContexte,
            serverReportPrecedent = rapportPrecedent,
            resultats = emptyList(),
            constats = listOf(Constat("Vérification d'admin", Niveau.CRITIQUE, "Étape bloquée : propriété du domaine non vérifiée"))
        )
    }

    // Chemins ciblés uniquement selon le CMS détecté à l'étape 1 (réutilise pathsToTest de ce fichier)
    val cheminsATester = pathsToTest(rapportPrecedent.cmsContexte)
    val resultats = mutableListOf<AdminPathResult>()

    for (chemin in cheminsATester) {
        delay(400) // rate limiting, cohérent avec le reste du pipeline
        val url = "https://$domain$chemin"

        val resultat = try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val code = resp.code
            val headersReponse = resp.headers.toMultimap().mapValues { it.value.joinToString(" | ") }
            val body = if (code in 200..299) resp.body?.string() ?: "" else ""
            resp.close()

            when {
                code == 401 || code == 403 || code == 429 || code == 503 -> {
                    val diagnostic = diagnostiquerCodeHttp(code, headersReponse)
                    AdminPathResult(chemin, url, code, StatutAdmin.SECURISE, diagnostic.explicationJustifiee)
                }
                code in 200..299 -> {
                    val contenuConfirmeLogin = marqueursPageConnexion.any { body.contains(it, ignoreCase = true) }
                    if (contenuConfirmeLogin) {
                        AdminPathResult(
                            chemin, url, code, StatutAdmin.ACCESSIBLE_SANS_PROTECTION,
                            "Page de connexion admin atteignable sans restriction réseau préalable (HTTP $code, formulaire de connexion détecté)"
                        )
                    } else {
                        AdminPathResult(
                            chemin, url, code, StatutAdmin.INDETERMINE,
                            "HTTP $code mais contenu ne correspond pas à une page de connexion identifiable — probable faux positif (page générique)"
                        )
                    }
                }
                code in 300..399 -> AdminPathResult(
                    chemin, url, code, StatutAdmin.INDETERMINE,
                    "Redirection (HTTP $code) — destination à vérifier manuellement : ${resp.header("Location") ?: "inconnue"}"
                )
                else -> AdminPathResult(chemin, url, code, StatutAdmin.NON_TROUVE, "HTTP $code")
            }
        } catch (e: Exception) {
            val diagnostic = diagnostiquerExceptionReseau(e)
            val statut = if (diagnostic.estUnBonSigneSecurite) StatutAdmin.SECURISE else StatutAdmin.INDETERMINE
            AdminPathResult(chemin, url, 0, statut, diagnostic.explicationJustifiee)
        }

        resultats += resultat
    }

    val accessiblesSansProtection = resultats.filter { it.statut == StatutAdmin.ACCESSIBLE_SANS_PROTECTION }
    if (accessiblesSansProtection.isNotEmpty()) {
        constats += Constat(
            "Page(s) d'administration non protégée(s) en amont",
            Niveau.AVERTISSEMENT,
            accessiblesSansProtection.joinToString("; ") { "${it.chemin} (${it.raison})" } +
                " — recommandé : restriction par IP, authentification à deux facteurs, ou renommage du chemin par défaut"
        )
    }
    val securisees = resultats.filter { it.statut == StatutAdmin.SECURISE }
    if (securisees.isNotEmpty()) {
        constats += Constat(
            "Page(s) d'administration protégée(s)",
            Niveau.CONFORME,
            securisees.joinToString(", ") { it.chemin }
        )
    }
    if (accessiblesSansProtection.isEmpty() && securisees.isEmpty()) {
        constats += Constat(
            "Page d'administration",
            Niveau.INDETERMINE,
            "Aucun chemin testé (propre au CMS ${rapportPrecedent.cmsContexte}) n'a donné de résultat concluant"
        )
    }

    AdminPageReport(
        domain = domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        serverReportPrecedent = rapportPrecedent,
        resultats = resultats,
        constats = constats
    )
}
