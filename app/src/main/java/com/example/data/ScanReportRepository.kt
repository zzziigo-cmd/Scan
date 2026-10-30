package com.example.data

import com.example.security.Niveau
import com.example.security.RapportComplet
import com.example.security.RapportGlobal
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanReportRepository(private val dao: ScanReportDao) {

    val allReports: Flow<List<ScanReportEntity>> = dao.getAllReports()

    suspend fun getReportById(id: Long): ScanReportEntity? = dao.getReportById(id)

    suspend fun saveReport(rapportComplet: RapportComplet): Long {
        val global = rapportComplet.rapportGlobal
        val json = serializeReportToJson(rapportComplet)

        val entity = ScanReportEntity(
            domain = global.domain,
            cms = global.cmsDetecte.name,
            score = global.scoreSur100,
            totalCritical = global.totalCritiques,
            totalWarnings = global.totalAvertissements,
            totalPassed = global.totalConformes,
            totalFindings = global.totalConstats,
            timestamp = System.currentTimeMillis(),
            jsonContent = json,
            isSynced = false
        )
        return dao.insertReport(entity)
    }

    suspend fun deleteReport(id: Long) = dao.deleteReportById(id)

    suspend fun clearHistory() = dao.clearAllReports()

    suspend fun markSynced(id: Long) = dao.markAsSynced(id)

    companion object {
        fun serializeReportToJson(rapportComplet: RapportComplet): String {
            val global = rapportComplet.rapportGlobal
            val root = JSONObject()

            val meta = JSONObject().apply {
                put("scan_engine", "Scan Intelligent CMS-Aware")
                put("version", "2.4.0")
                put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
                put("target_domain", global.domain)
                put("detected_cms", global.cmsDetecte.name)
                put("security_score", global.scoreSur100)
            }
            root.put("metadata", meta)

            val summary = JSONObject().apply {
                put("score_out_of_100", global.scoreSur100)
                put("critical_findings", global.totalCritiques)
                put("warning_findings", global.totalAvertissements)
                put("compliant_checks", global.totalConformes)
                put("total_checks", global.totalConstats)
            }
            root.put("summary", summary)

            val categoriesArray = JSONArray()
            global.categories.forEach { cat ->
                val catObj = JSONObject().apply {
                    put("name", cat.nom)
                    put("total", cat.total)
                    put("critical", cat.critiques)
                    put("warnings", cat.avertissements)
                    put("compliant", cat.conformes)
                }
                categoriesArray.put(catObj)
            }
            root.put("categories", categoriesArray)

            val findingsArray = JSONArray()
            global.tousLesConstats.forEach { (step, constat) ->
                val fObj = JSONObject().apply {
                    put("step", step)
                    put("title", constat.titre)
                    put("severity", constat.niveau.name)
                    put("description", constat.details)
                }
                findingsArray.put(fObj)
            }
            root.put("findings", findingsArray)

            // Crawl & Injection tests
            val crawlObj = JSONObject().apply {
                put("pages_crawled", rapportComplet.rapportCrawl.pages.size)
                put("forms_detected", rapportComplet.rapportCrawl.pages.sumOf { it.formulaires.size })
                put("user_inputs_detected", rapportComplet.rapportCrawl.totalEntreesUtilisateurTrouvees)
                put("standard_port", rapportComplet.rapportCrawl.estPortParDefaut)
                put("port_tested", rapportComplet.rapportCrawl.portUtilise)
            }
            root.put("crawl_analysis", crawlObj)

            val injectionObj = JSONObject()
            if (rapportComplet.rapportInjection != null) {
                val inj = rapportComplet.rapportInjection
                injectionObj.put("tested_inputs", inj.entreesTestees)
                val probeResults = JSONArray()
                inj.resultats.forEach { res ->
                    val r = JSONObject().apply {
                        put("field", res.champ)
                        put("url", res.url)
                        put("probe_type", res.typeProbe.name)
                        put("is_vulnerable", res.vulnerable)
                        put("explanation", res.explication)
                    }
                    probeResults.put(r)
                }
                injectionObj.put("probes", probeResults)
            } else {
                injectionObj.put("status", "No user inputs crawled on default ports")
            }
            root.put("input_probes_audit", injectionObj)

            return root.toString(2)
        }

        fun generateFormattedSummaryText(report: ScanReportEntity): String {
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(report.timestamp))
            return buildString {
                appendLine("=========================================")
                appendLine("       RAPPORT D'AUDIT DE SÉCURITÉ       ")
                appendLine("         Application 'Scan' v2.4.0        ")
                appendLine("=========================================")
                appendLine("Cible       : ${report.domain}")
                appendLine("Date        : $dateStr")
                appendLine("CMS Détecté : ${report.cms}")
                appendLine("Score Global: ${report.score} / 100")
                appendLine("-----------------------------------------")
                appendLine("SYNTHÈSE DES VULNÉRABILITÉS :")
                appendLine("- Critiques      : ${report.totalCritical}")
                appendLine("- Avertissements : ${report.totalWarnings}")
                appendLine("- Conformes      : ${report.totalPassed}")
                appendLine("- Total contrôles: ${report.totalFindings}")
                appendLine("-----------------------------------------")
                appendLine("DÉTAIL DU RAPPORT :")
                appendLine(report.jsonContent)
                appendLine("=========================================")
                appendLine("Généré par Scan - Développeur : Redouane Oulaati")
                appendLine("Contact : redouaneoulaati@gmail.com | +212646898915")
            }
        }
    }
}
