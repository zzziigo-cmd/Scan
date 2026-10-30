package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

// ============================================================
// 1. DÉTECTION DE CMS (fingerprinting)
// ============================================================

enum class DetectedCms { WORDPRESS, DRUPAL, JOOMLA, MAGENTO, PRESTASHOP, LARAVEL, UNKNOWN }

data class CmsSignature(
    val cms: DetectedCms,
    val htmlMarkers: List<String> = emptyList(),
    val headerMarkers: Map<String, String> = emptyMap(), // header name -> substring attendu
    val cookieMarkers: List<String> = emptyList(),
    val adminPaths: List<String>
)

object CmsSignatures {
    val all = listOf(
        CmsSignature(
            cms = DetectedCms.WORDPRESS,
            htmlMarkers = listOf("wp-content", "wp-includes", "wp-json"),
            adminPaths = listOf("/wp-login.php", "/wp-admin/")
        ),
        CmsSignature(
            cms = DetectedCms.DRUPAL,
            htmlMarkers = listOf("/sites/default/", "Drupal.settings"),
            headerMarkers = mapOf("X-Generator" to "Drupal"),
            adminPaths = listOf("/user/login")
        ),
        CmsSignature(
            cms = DetectedCms.JOOMLA,
            htmlMarkers = listOf("/media/jui/", "Joomla!"),
            adminPaths = listOf("/administrator/")
        ),
        CmsSignature(
            cms = DetectedCms.MAGENTO,
            htmlMarkers = listOf("/static/frontend/", "Mage.Cookies"),
            adminPaths = listOf("/admin", "/index.php/admin")
        ),
        CmsSignature(
            cms = DetectedCms.PRESTASHOP,
            htmlMarkers = listOf("PrestaShop", "/modules/"),
            cookieMarkers = listOf("PrestaShop-"),
            adminPaths = listOf("/admin123", "/adminXXXX") // chemin réel à adapter : Prestashop randomise ce dossier
        ),
        CmsSignature(
            cms = DetectedCms.LARAVEL,
            headerMarkers = mapOf("Set-Cookie" to "laravel_session"),
            adminPaths = listOf("/login")
        )
    )
}

data class CmsDetectionResult(
    val cms: DetectedCms,
    val matchedOn: String // ce qui a permis la détection, pour la traçabilité du rapport
)

fun detectCms(htmlBody: String, headers: Map<String, String>): CmsDetectionResult {
    for (sig in CmsSignatures.all) {
        val htmlMatch = sig.htmlMarkers.firstOrNull { htmlBody.contains(it, ignoreCase = true) }
        if (htmlMatch != null) {
            return CmsDetectionResult(sig.cms, "marqueur HTML : \"$htmlMatch\"")
        }
        for ((headerName, expectedSubstring) in sig.headerMarkers) {
            val headerValue = headers[headerName] ?: headers.entries
                .firstOrNull { it.key.equals(headerName, ignoreCase = true) }?.value
            if (headerValue?.contains(expectedSubstring, ignoreCase = true) == true) {
                return CmsDetectionResult(sig.cms, "en-tête $headerName : \"$expectedSubstring\"")
            }
        }
    }
    return CmsDetectionResult(DetectedCms.UNKNOWN, "aucun marqueur de CMS reconnu")
}

fun pathsToTest(cms: DetectedCms): List<String> {
    val generic = listOf("/admin", "/login")
    val specific = CmsSignatures.all.find { it.cms == cms }?.adminPaths ?: emptyList()
    return (generic + specific).distinct()
}

// ============================================================
// 2. VERROU D'AUTORISATION ET PROPRIÉTÉ
// Validation d'audit direct ou par fichier racine (vérification DNS TXT retirée)
// ============================================================

sealed class OwnershipCheck {
    data class Verified(val domain: String, val method: String) : OwnershipCheck()
    data class Failed(val reason: String) : OwnershipCheck()
}

object OwnershipGate {

    /**
     * Autorisation directe confirmée par l'administrateur ou le propriétaire du système
     */
    fun directAuthorization(domain: String, notes: String = "Autorisation explicite confirmée"): OwnershipCheck {
        return OwnershipCheck.Verified(domain, notes)
    }

    /**
     * Vérifie la propriété via un fichier texte déposé à la racine :
     * https://<domain>/webscan-verify.txt contenant le token attendu
     */
    suspend fun verifyViaFile(domain: String, expectedToken: String): OwnershipCheck =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
            try {
                val request = Request.Builder()
                    .url("https://$domain/webscan-verify.txt")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()
                return@withContext if (response.code == 200 && body.trim() == expectedToken) {
                    OwnershipCheck.Verified(domain, "Fichier racine")
                } else {
                    OwnershipCheck.Failed("Fichier absent ou jeton incorrect (HTTP ${response.code})")
                }
            } catch (e: Exception) {
                OwnershipCheck.Failed("Erreur réseau : ${e.localizedMessage}")
            }
        }
}

// ============================================================
// 3. SCANNER CIBLÉ — ne s'exécute que si la propriété est vérifiée
// ============================================================

data class ScanLogEntry(
    val timestampMs: Long,
    val path: String,
    val reasonTested: String, // traçabilité : pourquoi ce chemin a été choisi
    val statusCode: Int,
    val latencyMs: Long
)

data class TargetedScanReport(
    val domain: String,
    val ownership: OwnershipCheck.Verified,
    val detectedCms: CmsDetectionResult,
    val entries: List<ScanLogEntry>
)

sealed class ScanOutcome {
    data class Success(val report: TargetedScanReport) : ScanOutcome()
    data class Blocked(val reason: String) : ScanOutcome()
}

class TargetedAdminScanner(
    private val minDelayBetweenRequestsMs: Long = 400 // rate limiting : évite le bombardement du serveur cible
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false) // on veut voir les 301/302 bruts, pas la destination finale
        .build()

    suspend fun scan(
        domain: String,
        ownership: OwnershipCheck,
        onProgress: (ScanLogEntry) -> Unit = {}
    ): ScanOutcome = withContext(Dispatchers.IO) {

        // Porte d'entrée obligatoire : sans propriété vérifiée, aucun chemin n'est testé
        val verified = ownership as? OwnershipCheck.Verified
            ?: return@withContext ScanOutcome.Blocked(
                "Scan refusé : propriété du domaine non vérifiée (${(ownership as? OwnershipCheck.Failed)?.reason ?: "statut inconnu"})"
            )

        val baseUrl = "https://$domain"

        // Étape 1 : récupérer la page d'accueil pour le fingerprinting
        val homeRequest = Request.Builder().url(baseUrl).get().build()
        val homeResponse = client.newCall(homeRequest).execute()
        val htmlBody = homeResponse.body?.string() ?: ""
        val headers = homeResponse.headers.toMultimap().mapValues { it.value.joinToString(";") }
        homeResponse.close()

        val cmsResult = detectCms(htmlBody, headers)
        val paths = pathsToTest(cmsResult.cms)

        val entries = mutableListOf<ScanLogEntry>()
        for (path in paths) {
            delay(minDelayBetweenRequestsMs) // rate limiting entre chaque requête

            val start = System.currentTimeMillis()
            val reason = if (cmsResult.cms == DetectedCms.UNKNOWN) {
                "chemin générique (aucun CMS détecté)"
            } else {
                "ciblé pour ${cmsResult.cms} — détecté via ${cmsResult.matchedOn}"
            }

            val entry = try {
                val req = Request.Builder().url("$baseUrl$path").get().build()
                val resp = client.newCall(req).execute()
                val latency = System.currentTimeMillis() - start
                val code = resp.code
                resp.close()
                ScanLogEntry(System.currentTimeMillis(), path, reason, code, latency)
            } catch (e: Exception) {
                ScanLogEntry(System.currentTimeMillis(), path, reason, 0, System.currentTimeMillis() - start)
            }

            entries.add(entry)
            onProgress(entry)
        }

        ScanOutcome.Success(
            TargetedScanReport(
                domain = domain,
                ownership = verified,
                detectedCms = cmsResult,
                entries = entries
            )
        )
    }
}
