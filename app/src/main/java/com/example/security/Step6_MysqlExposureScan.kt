package com.example.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

// ============================================================
// ÉTAPE 6 : EXPOSITION MYSQL
// Entrée  : IndexationReport (sortie de l'étape 5)
// Sortie  : MysqlExposureReport (entrée de l'étape 7 : rapport final)
//
// PORTÉE VOLONTAIREMENT LIMITÉE :
// - On vérifie seulement si le port 3306 répond (TCP connect)
// - On lit la bannière de "handshake" que MySQL envoie automatiquement
//   avant toute authentification (comportement standard du protocole,
//   ne nécessite aucun identifiant)
// - AUCUNE tentative de connexion/authentification n'est faite ici.
//   Un vrai audit s'arrête à "la base est-elle exposée ?" — pas à
//   "peut-on y entrer ?", qui sortirait du cadre du scan passif.
// ============================================================

data class MysqlExposureReport(
    val domain: String,
    val cmsContexte: DetectedCms,
    val indexationReportPrecedent: IndexationReport,
    val portOuvert: Boolean,
    val versionServeurDetectee: String?,
    val constats: List<Constat>
)

suspend fun verifierExpositionMysql(
    rapportPrecedent: IndexationReport, // vient directement de l'étape 5
    port: Int = 3306,
    timeoutMs: Int = 3000
): MysqlExposureReport = withContext(Dispatchers.IO) {

    val domain = rapportPrecedent.domain
    val constats = mutableListOf<Constat>()
    var portOuvert = false
    var version: String? = null
    var diagnosticFermeture: DiagnosticBlocage? = null

    val resultat = withTimeoutOrNull(timeoutMs.toLong()) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(domain, port), timeoutMs)
                portOuvert = true

                // Lecture passive de la bannière de handshake MySQL.
                // Format (simplifié) : [longueur 3 octets][seq 1 octet][protocole 1 octet]
                // [version serveur en texte, terminée par 0x00]...
                val input = socket.getInputStream()
                val buffer = ByteArray(256)
                val lus = input.read(buffer)
                if (lus > 5) {
                    // la version commence à l'offset 5 (après longueur+seq+protocole) et se termine au premier 0x00
                    val finVersion = buffer.indexOfFirst { it == 0.toByte() }.let { if (it > 5) it else lus }
                    version = String(buffer, 5, (finVersion - 5).coerceAtLeast(0)).trim()
                }
            }
        } catch (e: Exception) {
            // Port fermé, filtré, ou timeout — c'est le résultat attendu dans le bon cas,
            // mais on garde le diagnostic pour justifier précisément pourquoi dans le rapport.
            diagnosticFermeture = diagnostiquerExceptionReseau(e)
        }
    }

    if (portOuvert) {
        constats += Constat(
            "Port MySQL exposé publiquement",
            Niveau.CRITIQUE,
            "Le port $port répond depuis l'extérieur — une base de données ne devrait jamais être directement accessible depuis internet, même avec un mot de passe fort. Recommandation : restreindre via pare-feu / VPC / bind sur localhost uniquement."
        )
        if (version != null && version!!.isNotBlank()) {
            constats += Constat(
                "Version serveur MySQL exposée",
                Niveau.AVERTISSEMENT,
                "Bannière de connexion révèle : \"$version\" — permet de cibler des CVE connues pour cette version"
            )
        }
    } else {
        val explication = diagnosticFermeture?.explicationJustifiee
            ?: "Aucune réponse reçue — port probablement fermé par défaut"
        constats += Constat(
            "Port MySQL",
            Niveau.CONFORME,
            "Port $port non accessible depuis l'extérieur. $explication"
        )
    }

    MysqlExposureReport(
        domain = domain,
        cmsContexte = rapportPrecedent.cmsContexte,
        indexationReportPrecedent = rapportPrecedent,
        portOuvert = portOuvert,
        versionServeurDetectee = version,
        constats = constats
    )
}
