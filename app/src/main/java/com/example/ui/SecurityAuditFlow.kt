package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.ScanDatabase
import com.example.data.ScanReportRepository
import com.example.security.*
import com.example.util.NotificationHelper
import kotlinx.coroutines.launch

private sealed class EcranAudit {
    data class Progression(val domain: String, val urlDemarrage: String, val ownership: OwnershipCheck) : EcranAudit()
    data class Rapport(val rapportComplet: RapportComplet) : EcranAudit()
    data class Architecture(val rapportArchi: RapportArchitectural, val rapportOrigine: RapportComplet) : EcranAudit()
    data class Owasp(val rapportOrigine: RapportComplet) : EcranAudit()
    data class Injection(val rapportOrigine: RapportComplet) : EcranAudit()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditFlow(
    domain: String,
    urlDemarrage: String,
    ownership: OwnershipCheck,
    onTerminer: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember {
        ScanReportRepository(ScanDatabase.getInstance(context).scanReportDao())
    }

    var ecran by remember { mutableStateOf<EcranAudit>(EcranAudit.Progression(domain, urlDemarrage, ownership)) }

    fun revenirAuRapport(origine: RapportComplet) {
        ecran = EcranAudit.Rapport(origine)
    }

    BackHandler {
        when (val e = ecran) {
            is EcranAudit.Progression -> onTerminer()
            is EcranAudit.Rapport -> onTerminer()
            is EcranAudit.Architecture -> revenirAuRapport(e.rapportOrigine)
            is EcranAudit.Owasp -> revenirAuRapport(e.rapportOrigine)
            is EcranAudit.Injection -> revenirAuRapport(e.rapportOrigine)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titrePourEcran(ecran)) },
                navigationIcon = {
                    when (val e = ecran) {
                        is EcranAudit.Progression -> IconButton(onClick = onTerminer) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                        is EcranAudit.Rapport -> IconButton(onClick = onTerminer) {
                            Icon(Icons.Default.Dashboard, contentDescription = "Tableau de Bord")
                        }
                        is EcranAudit.Architecture -> IconButton(onClick = { revenirAuRapport(e.rapportOrigine) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                        is EcranAudit.Owasp -> IconButton(onClick = { revenirAuRapport(e.rapportOrigine) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                        is EcranAudit.Injection -> IconButton(onClick = { revenirAuRapport(e.rapportOrigine) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    if (ecran is EcranAudit.Rapport) {
                        TextButton(onClick = onTerminer) {
                            Text("Terminer")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val e = ecran) {
                is EcranAudit.Progression -> ScanProgressScreen(
                    domain = e.domain,
                    urlDemarrage = e.urlDemarrage,
                    ownership = e.ownership,
                    onRapportPret = { rapportComplet ->
                        ecran = EcranAudit.Rapport(rapportComplet)

                        // Save to Room Database
                        coroutineScope.launch {
                            repository.saveReport(rapportComplet)
                        }

                        // Send Push Notification
                        val isCrit = rapportComplet.rapportGlobal.totalCritiques > 0
                        val msg = if (isCrit) {
                            "${rapportComplet.rapportGlobal.totalCritiques} vulnérabilité(s) critique(s) détectée(s) sur ${rapportComplet.rapportGlobal.domain}. Score : ${rapportComplet.rapportGlobal.scoreSur100}/100"
                        } else {
                            "Audit terminé avec succès sur ${rapportComplet.rapportGlobal.domain}. Score de sécurité : ${rapportComplet.rapportGlobal.scoreSur100}/100"
                        }
                        NotificationHelper.sendSecurityAlert(
                            context = context,
                            domain = rapportComplet.rapportGlobal.domain,
                            title = if (isCrit) "Alerte de Sécurité Critique" else "Audit de Sécurité Terminé",
                            message = msg,
                            isCritical = isCrit
                        )
                    }
                )

                is EcranAudit.Rapport -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        ReportScreen(rapport = e.rapportComplet.rapportGlobal)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { ecran = EcranAudit.Owasp(e.rapportComplet) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("OWASP", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { ecran = EcranAudit.Injection(e.rapportComplet) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Entrées XSS/SQL", maxLines = 1)
                        }
                        Button(
                            onClick = {
                                val rapportArchi = analyserArchitectureSecurite(e.rapportComplet.rapportGlobal)
                                ecran = EcranAudit.Architecture(rapportArchi, e.rapportComplet)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Architecture", maxLines = 1)
                        }
                    }
                }

                is EcranAudit.Architecture -> ArchitectureScreen(rapport = e.rapportArchi)

                is EcranAudit.Owasp -> OwaspScreen(
                    rapport = e.rapportOrigine.rapportGlobal,
                    rapportInjection = e.rapportOrigine.rapportInjection
                )

                is EcranAudit.Injection -> InjectionScreen(
                    crawl = e.rapportOrigine.rapportCrawl,
                    injection = e.rapportOrigine.rapportInjection,
                    cms = e.rapportOrigine.rapportGlobal.cmsDetecte
                )
            }
        }
    }
}

private fun titrePourEcran(ecran: EcranAudit): String = when (ecran) {
    is EcranAudit.Progression -> "Scan en cours"
    is EcranAudit.Rapport -> "Rapport d'audit"
    is EcranAudit.Architecture -> "Analyse architecturale"
    is EcranAudit.Owasp -> "Conformité OWASP Top 10"
    is EcranAudit.Injection -> "Tests d'entrée (XSS & SQL)"
}
