package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.security.*

// ============================================================
// ÉCRAN DE SCAN — exécute les 9 étapes du pipeline séquentiellement
// et affiche la progression en temps réel (chaque étape se coche
// au fur et à mesure qu'elle termine, sans attendre la fin du scan).
// ============================================================

private enum class EtapeStatut { EN_ATTENTE, EN_COURS, TERMINEE, ERREUR }

private data class EtapePipeline(
    val nom: String,
    var statut: EtapeStatut = EtapeStatut.EN_ATTENTE
)

@Composable
fun ScanProgressScreen(
    domain: String,
    urlDemarrage: String,
    ownership: OwnershipCheck,
    onRapportPret: (RapportComplet) -> Unit
) {
    val etapes = remember {
        mutableStateListOf(
            EtapePipeline("Vérification de propriété"),
            EtapePipeline("Exploration de l'application"),
            EtapePipeline("Tests d'entrée (XSS / SQL)"),
            EtapePipeline("Détection du CMS"),
            EtapePipeline("SSL / TLS & en-têtes"),
            EtapePipeline("Cookies"),
            EtapePipeline("Comportement PHP"),
            EtapePipeline("Indexation"),
            EtapePipeline("Exposition MySQL"),
            EtapePipeline("Serveur & réseau"),
            EtapePipeline("Page d'administration"),
            EtapePipeline("Rapport final")
        )
    }
    var erreurMessage by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    fun marquer(index: Int, statut: EtapeStatut) {
        etapes[index] = etapes[index].copy(statut = statut)
    }

    LaunchedEffect(domain) {
        enCours = true
        erreurMessage = null

        // Étape 0 : propriété
        marquer(0, EtapeStatut.EN_COURS)
        if (ownership !is OwnershipCheck.Verified) {
            marquer(0, EtapeStatut.ERREUR)
            erreurMessage = "Scan bloqué : propriété du domaine non vérifiée"
            enCours = false
            return@LaunchedEffect
        }
        marquer(0, EtapeStatut.TERMINEE)

        try {
            // Étape 1 : exploration
            marquer(1, EtapeStatut.EN_COURS)
            val crawl = explorerApplication(urlDemarrage, ownership)
            marquer(1, EtapeStatut.TERMINEE)

            // Étape 2 : tests d'entrée (seulement si des entrées ont été trouvées)
            marquer(2, EtapeStatut.EN_COURS)
            val rapportInjection = if (crawl.totalEntreesUtilisateurTrouvees > 0) {
                testerEntreesUtilisateur(crawl, ownership)
            } else null
            marquer(2, EtapeStatut.TERMINEE)

            // Étape 3
            marquer(3, EtapeStatut.EN_COURS)
            val (html, headers) = fetchHomePagePublic(domain)
            val cms = detectCms(html, headers)
            marquer(3, EtapeStatut.TERMINEE)

            // Étape 4
            marquer(4, EtapeStatut.EN_COURS)
            val sslHeaders = scannerSslEtHeaders(domain, cms)
            marquer(4, EtapeStatut.TERMINEE)

            // Étape 5
            marquer(5, EtapeStatut.EN_COURS)
            val cookies = scannerCookies(sslHeaders)
            marquer(5, EtapeStatut.TERMINEE)

            // Étape 6
            marquer(6, EtapeStatut.EN_COURS)
            val php = analyserComportementPhp(cookies)
            marquer(6, EtapeStatut.TERMINEE)

            // Étape 7
            marquer(7, EtapeStatut.EN_COURS)
            val indexation = analyserIndexation(php)
            marquer(7, EtapeStatut.TERMINEE)

            // Étape 8
            marquer(8, EtapeStatut.EN_COURS)
            val mysql = verifierExpositionMysql(indexation)
            marquer(8, EtapeStatut.TERMINEE)

            // Étape 9
            marquer(9, EtapeStatut.EN_COURS)
            val serveurReseau = analyserServeurEtReseau(mysql)
            marquer(9, EtapeStatut.TERMINEE)

            // Étape 10
            marquer(10, EtapeStatut.EN_COURS)
            val admin = verifierAccessibiliteAdmin(serveurReseau, ownership)
            marquer(10, EtapeStatut.TERMINEE)

            // Étape 11
            marquer(11, EtapeStatut.EN_COURS)
            val rapport = genererRapportFinal(admin)
            marquer(11, EtapeStatut.TERMINEE)

            enCours = false
            onRapportPret(RapportComplet(rapport, crawl, rapportInjection))

        } catch (e: Exception) {
            val indexEnCours = etapes.indexOfFirst { it.statut == EtapeStatut.EN_COURS }
            if (indexEnCours >= 0) marquer(indexEnCours, EtapeStatut.ERREUR)
            erreurMessage = "Erreur pendant le scan : ${e.localizedMessage}"
            enCours = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Analyse en cours : $domain", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(etapes) { etape ->
                EtapeRow(etape)
            }
        }

        erreurMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(msg, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun EtapeRow(etape: EtapePipeline) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (etape.statut) {
            EtapeStatut.EN_ATTENTE -> Box(
                modifier = Modifier.size(20.dp)
            ) // simple espace vide, pas encore commencé
            EtapeStatut.EN_COURS -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            EtapeStatut.TERMINEE -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
            EtapeStatut.ERREUR -> Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
        Text(
            etape.nom,
            style = MaterialTheme.typography.bodyMedium,
            color = if (etape.statut == EtapeStatut.EN_ATTENTE)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}

// Réutilisée depuis l'orchestrateur (étape 9) — exposée ici pour l'écran
private suspend fun fetchHomePagePublic(domain: String): Pair<String, Map<String, String>> =
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
