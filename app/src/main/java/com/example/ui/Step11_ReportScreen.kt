package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.*

// ============================================================
// ÉCRAN DE RAPPORT FINAL
// Entrée : RapportGlobal (sortie de genererRapportFinal, étape 9)
// Affiche : score global en donut, répartition par catégorie en barres,
// et pour chaque problème une fiche dépliable (explication + solution),
// générée par genererToutesLesRecommandations (RecommendationEngine).
// ============================================================

@Composable
fun ReportScreen(rapport: RapportGlobal) {
    val recommandations = remember(rapport) { genererToutesLesRecommandations(rapport) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { EnTeteRapport(rapport) }
        item { ScoreDonutCard(rapport) }
        item { RepartitionParCategorie(rapport) }
        item {
            Text(
                "Problèmes identifiés & solutions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (recommandations.isEmpty()) {
            item {
                Card {
                    Text(
                        "Aucun problème détecté — tous les contrôles sont conformes.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(recommandations) { reco ->
                RecommandationCard(reco)
            }
        }
    }
}

@Composable
private fun EnTeteRapport(rapport: RapportGlobal) {
    Column {
        Text("Rapport d'audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(rapport.domain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "CMS détecté : ${rapport.cmsDetecte.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreDonutCard(rapport: RapportGlobal) {
    val couleurScore = when {
        rapport.scoreSur100 >= 80 -> Color(0xFF2E7D32)
        rapport.scoreSur100 >= 50 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                DonutChart(
                    critiques = rapport.totalCritiques,
                    avertissements = rapport.totalAvertissements,
                    conformes = rapport.totalConformes,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${rapport.scoreSur100}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = couleurScore)
                    Text("/ 100", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendeItem("Critique", rapport.totalCritiques, Color(0xFFC62828))
                LegendeItem("Avertissement", rapport.totalAvertissements, Color(0xFFF9A825))
                LegendeItem("Conforme", rapport.totalConformes, Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
private fun LegendeItem(label: String, valeur: Int, couleur: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(couleur))
        Text("$label : $valeur", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DonutChart(critiques: Int, avertissements: Int, conformes: Int, modifier: Modifier = Modifier) {
    val total = (critiques + avertissements + conformes).coerceAtLeast(1)
    val couleurCritique = Color(0xFFC62828)
    val couleurAvertissement = Color(0xFFF9A825)
    val couleurConforme = Color(0xFF2E7D32)

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        var angleDebut = -90f
        listOf(
            critiques to couleurCritique,
            avertissements to couleurAvertissement,
            conformes to couleurConforme
        ).forEach { (valeur, couleur) ->
            val sweep = 360f * (valeur.toFloat() / total.toFloat())
            if (valeur > 0) {
                drawArc(
                    color = couleur,
                    startAngle = angleDebut,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }
            angleDebut += sweep
        }
    }
}

@Composable
private fun RepartitionParCategorie(rapport: RapportGlobal) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Répartition par catégorie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            rapport.categories.forEach { cat ->
                CategorieBarRow(cat)
            }
        }
    }
}

@Composable
private fun CategorieBarRow(cat: CategorieScore) {
    val total = cat.total.coerceAtLeast(1)
    val propConforme = cat.conformes.toFloat() / total
    val propAvertissement = cat.avertissements.toFloat() / total
    val propCritique = cat.critiques.toFloat() / total

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cat.nom, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    cat.critiques > 0 -> "${cat.critiques} critique(s)"
                    cat.avertissements > 0 -> "${cat.avertissements} alerte(s)"
                    else -> "Conforme"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    cat.critiques > 0 -> Color(0xFFC62828)
                    cat.avertissements > 0 -> Color(0xFFF9A825)
                    else -> Color(0xFF2E7D32)
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            if (propConforme > 0) Box(modifier = Modifier.weight(propConforme).fillMaxHeight().background(Color(0xFF2E7D32)))
            if (propAvertissement > 0) Box(modifier = Modifier.weight(propAvertissement).fillMaxHeight().background(Color(0xFFF9A825)))
            if (propCritique > 0) Box(modifier = Modifier.weight(propCritique).fillMaxHeight().background(Color(0xFFC62828)))
        }
    }
}

@Composable
private fun RecommandationCard(reco: Recommandation) {
    var etendu by remember { mutableStateOf(reco.niveau == Niveau.CRITIQUE) }
    val couleurBord = when (reco.niveau) {
        Niveau.CRITIQUE -> Color(0xFFC62828)
        Niveau.AVERTISSEMENT -> Color(0xFFF9A825)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { etendu = !etendu }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(couleurBord))
                    Text(reco.titreProbleme, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Icon(if (etendu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            AnimatedVisibility(visible = etendu) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("Pourquoi c'est un problème", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(reco.explication, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Solution recommandée", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(reco.solution, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
