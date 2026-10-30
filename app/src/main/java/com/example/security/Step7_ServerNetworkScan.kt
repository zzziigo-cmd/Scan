package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

// ============================================================
// ÉTAPE 7 : SERVEUR & RÉSEAU
// Entrée  : MysqlExposureReport (sortie de l'étape 6)
// Sortie  : ServerNetworkReport (entrée de l'étape 8 : rapport final)
// ============================================================

data class ServerNetworkReport(
    val domain: String,
    val cmsContexte: DetectedCms,
    val mysqlReportPrecedent: MysqlExposureReport,
    val disponible: Boolean,
    val tempsReponseMs: Long?,
    val logicielServeur: String?,
    val protocoleHttp: String?,
    val redirigeVersHttps: Boolean?,
    val contenuMixte: Int, // nombre de ressources http:// trouvées sur une page https
    val constats: List<Constat>
)

private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .followRedirects(false) // on veut observer les redirections nous-mêmes
    .build()

suspend fun analyserServeurEtReseau(
    rapportPrecedent: MysqlExposureReport // vient directement de l'étape 6
): ServerNetworkReport = withContext(Dispatchers.IO) {

    val domain = rapportPrecedent.domain
    val constats = mutableListOf<Constat>()

    var disponible = false
    var tempsReponseMs: Long? = null
    var logicielServeur: String? = null
    var protocoleHttp: String? = null
    var redirigeVersHttps: Boolean? = null
    var contenuMixte = 0

    // --- 1. Disponibilité + temps de réponse + protocole HTTP ---
    try {
        val start = System.currentTimeMillis()
        val resp = client.newCall(Request.Builder().url("https://$domain").get().build()).execute()
        tempsReponseMs = System.currentTimeMillis() - start
        disponible = true

        protocoleHttp = when (resp.protocol) {
            Protocol.HTTP_2 -> "HTTP/2"
            Protocol.HTTP_1_1 -> "HTTP/1.1"
            Protocol.HTTP_1_0 -> "HTTP/1.0"
            else -> resp.protocol.toString()
        }

        val serverHeader = resp.headers["Server"]
        logicielServeur = serverHeader // peut être null si masqué volontairement — c'est une bonne pratique

        val htmlBody = resp.body?.string() ?: ""
        resp.close()

        // Contenu mixte : ressources http:// chargées sur une page https
        contenuMixte = Regex("""(?:src|href)=["']http://[^"']+["']""", RegexOption.IGNORE_CASE)
            .findAll(htmlBody).count()

        constats += Constat("Disponibilité", Niveau.CONFORME, "Site accessible en ${tempsReponseMs} ms")
        constats += when {
            tempsReponseMs!! > 3000 -> Constat("Performance", Niveau.AVERTISSEMENT, "Temps de réponse élevé (${tempsReponseMs} ms)")
            else -> Constat("Performance", Niveau.CONFORME, "Temps de réponse correct (${tempsReponseMs} ms)")
        }
        constats += Constat("Protocole HTTP", Niveau.CONFORME, "Version utilisée : $protocoleHttp")
        constats += if (logicielServeur != null) {
            Constat("Logiciel serveur", Niveau.AVERTISSEMENT, "En-tête Server exposé : \"$logicielServeur\" — facilite le ciblage de failles connues pour ce logiciel/version")
        } else {
            Constat("Logiciel serveur", Niveau.CONFORME, "Information serveur masquée")
        }
        if (contenuMixte > 0) {
            constats += Constat("Mixed content", Niveau.AVERTISSEMENT, "$contenuMixte ressource(s) chargée(s) en HTTP sur une page HTTPS")
        } else {
            constats += Constat("Mixed content", Niveau.CONFORME, "Aucune ressource HTTP détectée sur la page HTTPS")
        }
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constats += Constat(
            "Disponibilité",
            if (diagnostic.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.CRITIQUE,
            diagnostic.explicationJustifiee
        )
    }

    // --- 2. Redirection HTTP -> HTTPS ---
    try {
        val respHttp = client.newCall(Request.Builder().url("http://$domain").get().build()).execute()
        val code = respHttp.code
        val location = respHttp.header("Location")
        respHttp.close()
        redirigeVersHttps = code in 300..399 && location?.startsWith("https://") == true
        constats += if (redirigeVersHttps == true) {
            Constat("Redirection HTTP→HTTPS", Niveau.CONFORME, "Le trafic HTTP est bien redirigé vers HTTPS")
        } else {
            Constat("Redirection HTTP→HTTPS", Niveau.CRITIQUE, "Le trafic HTTP non chiffré est accepté sans redirection forcée vers HTTPS")
        }
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constats += Constat(
            "Redirection HTTP→HTTPS",
            Niveau.INDETERMINE,
            "Test HTTP impossible — ${diagnostic.explicationJustifiee}"
        )
    }

    // --- 3. Résolution réseau de base (le domaine répond-il à une connexion TCP standard) ---
    try {
        Socket().use { it.connect(InetSocketAddress(domain, 443), 3000) }
    } catch (e: Exception) {
        val diagnostic = diagnostiquerExceptionReseau(e)
        constats += Constat(
            "Connectivité réseau (443)",
            if (diagnostic.estUnBonSigneSecurite) Niveau.CONFORME else Niveau.CRITIQUE,
            diagnostic.explicationJustifiee
        )
    }

    // --- 4. Contextualisation CMS, propagée depuis l'étape 1 ---
    if (rapportPrecedent.cmsContexte == DetectedCms.WORDPRESS && logicielServeur?.contains("Apache", true) == true) {
        constats += Constat(
            "Combinaison WordPress + Apache",
            Niveau.CONFORME,
            "Vérifier que le fichier .htaccess restreint bien l'accès aux dossiers sensibles (wp-content/uploads, etc.)"
        )
    }

    ServerNetworkReport(
        domain = domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        mysqlReportPrecedent = rapportPrecedent,
        disponible = disponible,
        tempsReponseMs = tempsReponseMs,
        logicielServeur = logicielServeur,
        protocoleHttp = protocoleHttp,
        redirigeVersHttps = redirigeVersHttps,
        contenuMixte = contenuMixte,
        constats = constats
    )
}
