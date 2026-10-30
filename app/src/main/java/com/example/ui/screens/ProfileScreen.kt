package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.auth.UserProfile
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authManager: AuthManager,
    user: UserProfile?,
    onNavigateToAbout: () -> Unit,
    onNavigateToPricing: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    var isEditingProfile by remember { mutableStateOf(false) }

    var editName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var editOrg by remember(user) { mutableStateOf(user?.organization ?: "") }
    var editPhone by remember(user) { mutableStateOf(user?.phone ?: "") }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil & Paramètres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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

            // 1. User Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (user?.displayName?.take(1) ?: "S").uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user?.displayName ?: "Utilisateur Scan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(user?.email ?: "auditeur@scan.app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CyberGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            user?.subscriptionPlan ?: "Pro Sécurité",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text("• Membre vérifié", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            IconButton(onClick = { isEditingProfile = !isEditingProfile }) {
                                Icon(
                                    if (isEditingProfile) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription = "Modifier",
                                    tint = CyberCyan
                                )
                            }
                        }

                        // Edit Form Accordion
                        AnimatedVisibility(visible = isEditingProfile) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Nom complet") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editOrg,
                                    onValueChange = { editOrg = it },
                                    label = { Text("Organisation / Entreprise") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editPhone,
                                    onValueChange = { editPhone = it },
                                    label = { Text("Téléphone professionnel") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        authManager.updateProfile(editName, editOrg, editPhone)
                                        isEditingProfile = false
                                        Toast.makeText(context, "Profil mis à jour !", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Enregistrer les modifications")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Subscription & Monetization Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Abonnement & Monétisation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Formule actuelle : ${user?.subscriptionPlan ?: "Standard"}. Audits illimités et export PDF complet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onNavigateToPricing, modifier = Modifier.testTag("pricing_btn")) {
                            Text("Plans", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 3. Privacy & Security Settings
            item {
                Text("Sécurité & Confidentialité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Dark Mode Toggle
                        SettingToggleRow(
                            title = "Mode Sombre",
                            subtitle = "Thème optimisé pour basse luminosité",
                            icon = Icons.Default.DarkMode,
                            isChecked = user?.isDarkMode ?: true,
                            onCheckedChange = { authManager.toggleDarkMode(it) }
                        )

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // Biometrics
                        SettingToggleRow(
                            title = "Authentification Biométrique",
                            subtitle = "Empreinte digitale / reconnaissance faciale",
                            icon = Icons.Default.Fingerprint,
                            isChecked = user?.isBiometricEnabled ?: true,
                            onCheckedChange = { authManager.toggleBiometric(it) }
                        )

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // 2FA
                        SettingToggleRow(
                            title = "Double Authentification (2FA)",
                            subtitle = "Protection par code OTP à chaque connexion",
                            icon = Icons.Default.Security,
                            isChecked = user?.is2FAEnabled ?: false,
                            onCheckedChange = { authManager.toggle2FA(it) }
                        )

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        // Cloud Sync
                        SettingToggleRow(
                            title = "Synchronisation Cloud & Google Drive",
                            subtitle = "Sauvegarde continue des rapports chiffrés",
                            icon = Icons.Default.CloudSync,
                            isChecked = user?.isCloudSyncEnabled ?: true,
                            onCheckedChange = { authManager.toggleCloudSync(it) }
                        )
                    }
                }
            }

            // 4. App Info, Dev & Terms
            item {
                Text("Informations & Conditions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SettingActionRow(
                            title = "À propos de l'application & Développeur",
                            subtitle = "Redouane Oulaati • Contact & Version 2.4.0",
                            icon = Icons.Default.Info,
                            onClick = onNavigateToAbout
                        )

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        SettingActionRow(
                            title = "Fonctionnement des 9 étapes de scan",
                            subtitle = "Détail de la méthodologie CMS-aware et OWASP",
                            icon = Icons.Default.Layers,
                            onClick = onNavigateToAbout
                        )

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        SettingActionRow(
                            title = "Conditions Générales d'Utilisation (CGU)",
                            subtitle = "Règles d'audit éthique et protection des données",
                            icon = Icons.Default.Description,
                            onClick = onNavigateToTerms
                        )
                    }
                }
            }

            // 5. Account Actions: Logout & Permanent Deletion
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Déconnexion")
                    }

                    TextButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_btn"),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Supprimer définitivement mon compte")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Dialog for permanent account deletion
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Supprimer Définitivement le Compte ?") },
            text = {
                Text(
                    "Cette action est irréversible. Toutes vos données locales, configurations d'audit, historiques de rapports et préférences de synchronisation seront effacées de manière permanente."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        authManager.deleteAccount()
                        onAccountDeleted()
                        Toast.makeText(context, "Compte supprimé définitivement.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer mon compte")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
