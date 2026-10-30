package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.*

// ============================================================
// ÉCRAN D'ANALYSE ARCHITECTURALE
// Entrée : RapportArchitectural (sortie de analyserArchitectureSecurite)
// Deux sections : chaînes de risque combinées, puis axes architecturaux.
// ============================================================

@Composable
fun ArchitectureScreen(rapport: RapportArchitectural) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Analyse architecturale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(rapport.domain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    rapport.syntheseGlobale,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (rapport.chainesDeRisque.isNotEmpty()) {
            item {
                SectionTitre("Chaînes de risque combinées", rapport.chainesDeRisque.size)
            }
            items(rapport.chainesDeRisque) { chaine ->
                ChaineDeRisqueCard(chaine)
            }
        }

        item {
            SectionTitre("Axes architecturaux recommandés", rapport.axesArchitecturaux.size)
        }
        items(rapport.axesArchitecturaux) { axe ->
            AxeArchitecturalCard(axe)
        }
    }
}

@Composable
private fun SectionTitre(titre: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChaineDeRisqueCard(chaine: ChaineDeRisque) {
    var etendu by remember { mutableStateOf(false) }
    val couleur = when (chaine.priorite) {
        1 -> Color(0xFFC62828)
        2 -> Color(0xFFF9A825)
        else -> MaterialTheme.colorScheme.outline
    }
    val labelPriorite = when (chaine.priorite) {
        1 -> "Urgent"
        2 -> "Important"
        else -> "Structurel"
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { etendu = !etendu }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = couleur.copy(alpha = 0.15f),
                        contentColor = couleur,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            labelPriorite,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(chaine.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Icon(if (etendu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                chaine.signauxImpliques.forEach { signal ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                    ) {
                        Text(
                            signal,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = etendu) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text("Scénario d'exploitation", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(chaine.scenarioExploitation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AxeArchitecturalCard(axe: AxeArchitectural) {
    var etendu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { etendu = !etendu }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(axe.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Icon(if (etendu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            if (axe.dejaPartielleemntCouvert) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Lié à des problèmes déjà détectés dans ce scan",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            AnimatedVisibility(visible = etendu) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("Pourquoi des correctifs ponctuels ne suffisent pas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(axe.problemeDeFond, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Solution architecturale", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(axe.solutionConcrete, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
