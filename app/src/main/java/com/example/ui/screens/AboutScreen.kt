package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À Propos & Moteur de Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("about_back_btn")) {
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

            // 1. App Identity Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(42.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Application 'Scan'", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Version 2.4.0 (Build Enterprise)", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Scanner de sécurité intelligent CMS-aware, cartographie OWASP Top 10, sondes non-destructives XSS & SQL, audit d'architecture et recommandations de durcissement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // 2. Developer Card
            item {
                Text("Informations Développeur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                            Column {
                                Text("Redouane Oulaati", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Lead Software Engineer & Cyber-Security Specialist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Divider()

                        // Contact Email
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Email de contact", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("redouaneoulaati@gmail.com", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:redouaneoulaati@gmail.com")
                                }
                                context.startActivity(Intent.createChooser(intent, "Contacter par Email"))
                            }) {
                                Text("Écrire")
                            }
                        }

                        // Contact Phone
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Téléphone direct / WhatsApp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("+212646898915", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:+212646898915")
                                }
                                context.startActivity(intent)
                            }) {
                                Text("Appeler")
                            }
                        }
                    }
                }
            }

            // 3. Toutes les analyses de sécurité offertes
            item {
                Text("Analyses de Sécurité Intégrées (9 Étapes)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            val steps = listOf(
                "Étape 1 : Détection CMS (Fingerprinting)" to "Reconnaissance intelligente WordPress, Drupal, Joomla, Magento, PrestaShop, Laravel par marqueurs HTML, en-têtes et cookies.",
                "Étape 2 : Audit SSL/TLS & En-têtes HTTP" to "Vérification du certificat HTTPS, HSTS, CSP, X-Frame-Options, X-Content-Type-Options et Referrer-Policy.",
                "Étape 3 : Contrôle de Sécurité des Cookies" to "Inspection des drapeaux Secure, HttpOnly, SameSite (Strict/Lax) pour prévenir le vol de session.",
                "Étape 4 : Comportement du Moteur PHP" to "Détection de l'exposition de la version PHP (X-Powered-By) et des messages d'erreurs verbeux.",
                "Étape 5 : Indexation & Exposition de Fichiers" to "Analyse du robots.txt, sitemap.xml et détection du directory listing sensible.",
                "Étape 6 : Exposition de Port Base de Données" to "Vérification de l'exposition du port 3306 (MySQL) et lecture passive de bannière.",
                "Étape 7 : Configuration Réseau & Serveur" to "Contrôle de la disponibilité, logiciel serveur (Server header), redirection forcée HTTPS et mixed content.",
                "Étape 8 : Accessibilité de la Page d'Administration" to "Recherche ciblée par CMS des interfaces d'administration avec contrôle de password field anti-faux-positifs.",
                "Étape 9 : Rapport Final & Scoring Global" to "Calcul de l'indice de sécurité /100 et consolidation de toutes les vulnérabilités.",
                "Module A : Tests d'Entrée XSS & SQL" to "Sondes non destructives sur formulaires et paramètres d'URL (réflexion inoffensive, injection d'apostrophe isolée).",
                "Module B : Matrice de Conformité OWASP Top 10" to "Classement officiel dans les 10 catégories de l'OWASP (A01 à A10) avec impact et remédiations.",
                "Module C : Analyse Architecturale Avancée" to "Corrélation des constats en chaînes de risque combinées et recommandations structurelles (WAF, DMZ, IaC)."
            )

            items(steps) { (title, desc) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
