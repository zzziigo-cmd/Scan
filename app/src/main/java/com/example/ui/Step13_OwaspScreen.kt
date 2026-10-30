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
// ÉCRAN DE CONFORMITÉ OWASP TOP 10
// Entrée : List<SyntheseOwasp> (sortie de genererSyntheseOwasp, OwaspMapper.kt)
// Regroupe les constats déjà produits par le pipeline sous chaque
// catégorie officielle OWASP, avec description + conseil général.
// ============================================================

@Composable
fun OwaspScreen(rapport: RapportGlobal, rapportInjection: RapportInjection? = null) {
    val syntheses = remember(rapport, rapportInjection) { genererSyntheseOwaspComplet(rapport, rapportInjection) }
    val categoriesToutes = remember { CategorieOwasp.values().toList() }
    val categoriesTouchees = syntheses.map { it.categorie }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Conformité OWASP Top 10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${syntheses.size} catégorie(s) sur 10 présentent au moins un point à corriger",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            VueEnsembleOwasp(categoriesToutes, categoriesTouchees)
        }

        items(syntheses) { synthese ->
            OwaspCategorieCard(synthese)
        }

        if (syntheses.isEmpty()) {
            item {
                Card { Text("Aucun constat classifiable OWASP sur ce scan.", modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun VueEnsembleOwasp(toutes: List<CategorieOwasp>, touchees: Set<CategorieOwasp>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            toutes.forEach { cat ->
                val estTouchee = cat in touchees
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (estTouchee) Color(0xFFC62828) else Color(0xFF2E7D32))
                    )
                    Text(
                        "${cat.code} — ${cat.titre}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (estTouchee) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OwaspCategorieCard(synthese: SyntheseOwasp) {
    var etendu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { etendu = !etendu }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(synthese.categorie.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(synthese.categorie.titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("${synthese.constatsAssocies.size} constat(s) lié(s)", style = MaterialTheme.typography.labelSmall)
                }
                Icon(if (etendu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            AnimatedVisibility(visible = etendu) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Description", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(synthese.description.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Conseil de résolution", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(synthese.description.conseilGeneral, style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Constats liés sur ce scan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        synthese.constatsAssocies.forEach { (etape, constat) ->
                            Text("• [$etape] ${constat.titre}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
