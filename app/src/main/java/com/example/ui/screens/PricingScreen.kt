package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AuthManager
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen

data class PricingTier(
    val id: String,
    val name: String,
    val price: String,
    val period: String,
    val description: String,
    val features: List<String>,
    val isPopular: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    authManager: AuthManager,
    currentPlan: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val tiers = listOf(
        PricingTier(
            id = "starter",
            name = "Standard Gratuit",
            price = "0 €",
            period = "/mois",
            description = "Idéal pour tester le moteur sur des sites personnels.",
            features = listOf(
                "3 scans par mois",
                "Détection CMS (6 frameworks)",
                "Contrôles SSL & en-têtes HTTP",
                "Export JSON simple",
                "Historique local Room"
            )
        ),
        PricingTier(
            id = "pro",
            name = "Pro Sécurité",
            price = "19,99 €",
            period = "/mois",
            description = "Pour les développeurs, agences et webmasters professionnels.",
            features = listOf(
                "Audits illimités sans restriction",
                "Module complet tests d'injections XSS/SQL",
                "Matrice de conformité OWASP Top 10",
                "Analyse architecturale & chaînes de risques",
                "Solutions recommandées sous chaque champ",
                "Export PDF & JSON haute fidélité",
                "Support chiffré prioritaire E2EE"
            ),
            isPopular = true
        ),
        PricingTier(
            id = "enterprise",
            name = "Entreprise Audit",
            price = "79,99 €",
            period = "/mois",
            description = "Infrastructure complète et audits automatisés en continu.",
            features = listOf(
                "Toutes les fonctionnalités Pro",
                "Surveillance continue 24/7",
                "Sauvegarde automatique Google Drive & Cloud",
                "Certificat officiel de conformité cyber",
                "Support dédié direct avec Redouane Oulaati",
                "Accès API REST & Webhooks"
            )
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abonnements & Monétisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("pricing_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                Text("Choisissez votre niveau d'audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Facturation mensuelle sans engagement • Annulable à tout moment depuis Google Play", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items(tiers) { tier ->
                val isSelected = currentPlan.contains(tier.name, ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (tier.isPopular) it.border(1.5.dp, CyberCyan, RoundedCornerShape(16.dp)) else it
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (tier.isPopular)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (tier.isPopular) {
                                Surface(
                                    color = CyberCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                                        Text("RECOMMANDÉ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyberCyan)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(tier.price, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                            Text(tier.period, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }

                        Text(tier.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Divider()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (feature in tier.features) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                                    Text(feature, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isSelected) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abonnement Actuel")
                            }
                        } else {
                            Cyber3DButton(
                                text = "Choisir l'offre ${tier.name}",
                                onClick = {
                                    authManager.upgradePlan(tier.name)
                                    Toast.makeText(context, "Souscription à ${tier.name} confirmée !", Toast.LENGTH_SHORT).show()
                                },
                                primaryColor = if (tier.isPopular) CyberCyan else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
