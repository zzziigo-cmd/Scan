package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.OwnershipGate
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan

@Composable
fun NewScanDialog(
    onDismiss: () -> Unit,
    onStartScan: (domain: String, urlDemarrage: String) -> Unit
) {
    var domainInput by remember { mutableStateOf("scanme.nmap.org") }
    var hasAuthorization by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Radar, contentDescription = null, tint = CyberCyan)
                Text("Nouvel Audit Intelligent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Saisissez le domaine ou l'adresse du serveur à auditer. Le pipeline exécutera les 9 étapes CMS-aware et les sondes non destructives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
                        errorMessage = null
                    },
                    label = { Text("Domaine ou IP cible") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = CyberCyan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target_domain_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = hasAuthorization,
                            onCheckedChange = { hasAuthorization = it },
                            modifier = Modifier.testTag("auth_consent_checkbox")
                        )
                        Column {
                            Text("Autorisation explicite confirmée", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("J'atteste disposer de l'autorisation d'audit légal sur cette cible (Vérification directe sans DNS TXT).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { domainInput = "scanme.nmap.org" },
                        label = { Text("scanme.nmap.org", style = MaterialTheme.typography.labelSmall) }
                    )
                    SuggestionChip(
                        onClick = { domainInput = "owasp.org" },
                        label = { Text("owasp.org", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domainInput.isBlank()) {
                        errorMessage = "Veuillez entrer un domaine valide."
                        return@Button
                    }
                    if (!hasAuthorization) {
                        errorMessage = "Vous devez confirmer l'autorisation d'audit."
                        return@Button
                    }
                    val cleanDomain = domainInput.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
                    val startUrl = "https://$cleanDomain"
                    onStartScan(cleanDomain, startUrl)
                },
                modifier = Modifier.testTag("confirm_start_scan_btn")
            ) {
                Text("Démarrer le scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
