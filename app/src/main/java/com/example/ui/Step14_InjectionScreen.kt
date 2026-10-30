package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.*

// ============================================================
// ÉCRAN DE DÉTAIL DES TESTS D'ENTRÉE
// Entrée : RapportCrawl (exploration) + RapportInjection (sondes XSS/SQL)
// Affiche les statistiques de l'exploration, puis chaque résultat de
// sonde champ par champ, avec statut vulnérable/sûr et explication.
// ============================================================

@Composable
fun InjectionScreen(crawl: RapportCrawl, injection: RapportInjection?, cms: DetectedCms = DetectedCms.UNKNOWN) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Tests d'entrée utilisateur", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Exploration et sondes non destructives (réflexion XSS, erreur SQL)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { StatistiquesCrawlCard(crawl) }

        if (!crawl.estPortParDefaut) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Port ${crawl.portUtilise} non standard — exploration automatique non effectuée. " +
                            "Une analyse personnalisée est nécessaire pour ce port.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (injection == null) {
            item {
                Card {
                    Text(
                        "Aucune entrée utilisateur détectée lors de l'exploration — pas de test à effectuer.",
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            item {
                Text(
                    "${injection.entreesTestees} entrée(s) testée(s)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val parType = injection.resultats.groupBy { it.typeProbe }
            parType[TypeProbe.REFLEXION_XSS]?.let { resultats ->
                item { SousTitreProbe("Réflexion XSS", resultats.count { it.vulnerable }) }
                items(resultats) { ResultatProbeRow(it, cms) }
            }
            parType[TypeProbe.ERREUR_SQL]?.let { resultats ->
                item { SousTitreProbe("Erreur SQL exposée", resultats.count { it.vulnerable }) }
                items(resultats) { ResultatProbeRow(it, cms) }
            }
        }
    }
}

@Composable
private fun StatistiquesCrawlCard(crawl: RapportCrawl) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Exploration de l'application", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            LigneStat("Domaine", crawl.domain)
            LigneStat("Port utilisé", "${crawl.portUtilise} (${if (crawl.estPortParDefaut) "standard" else "non standard"})")
            LigneStat("Pages explorées", "${crawl.pages.size}")
            LigneStat("Formulaires trouvés", "${crawl.pages.sumOf { it.formulaires.size }}")
            LigneStat("Entrées utilisateur trouvées", "${crawl.totalEntreesUtilisateurTrouvees}")
        }
    }
}

@Composable
private fun LigneStat(label: String, valeur: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valeur, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SousTitreProbe(nom: String, nbVulnerables: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(nom, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (nbVulnerables > 0) {
            Surface(
                color = Color(0xFFC62828).copy(alpha = 0.15f),
                contentColor = Color(0xFFC62828),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "$nbVulnerables problème(s)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Titre de constat correspondant à chaque type de sonde, pour réutiliser
// exactement les textes déjà écrits dans RecommendationEngine.kt
private fun titreConstatPour(typeProbe: TypeProbe): String = when (typeProbe) {
    TypeProbe.REFLEXION_XSS -> "Réflexion XSS détectée"
    TypeProbe.ERREUR_SQL -> "Erreur SQL exposée"
}

@Composable
private fun ResultatProbeRow(resultat: ResultatProbe, cms: DetectedCms) {
    var etendu by remember { mutableStateOf(resultat.vulnerable) }
    val recommandation = remember(resultat) {
        if (resultat.vulnerable) {
            genererRecommandation(
                "Tests d'entrée",
                Constat(titreConstatPour(resultat.typeProbe), Niveau.CRITIQUE, resultat.explication),
                cms
            )
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { etendu = !etendu },
        colors = CardDefaults.cardColors(
            containerColor = if (resultat.vulnerable)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (resultat.vulnerable) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (resultat.vulnerable) Color(0xFFFF1744) else Color(0xFF00E676),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(resultat.champ, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Surface(
                            color = if (resultat.vulnerable) Color(0xFFFF1744).copy(alpha = 0.2f) else Color(0xFF00E676).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                if (resultat.vulnerable) "VULNÉRABLE" else "SÉCURISÉ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (resultat.vulnerable) Color(0xFFFF1744) else Color(0xFF00E676),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(resultat.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(resultat.explication, style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (etendu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (recommandation != null) {
                AnimatedVisibility(visible = etendu) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Solution recommandée sous ce champ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                recommandation.solution,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
