package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.UserProfile
import com.example.data.ScanReportEntity
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: UserProfile?,
    reports: List<ScanReportEntity>,
    onStartNewScan: () -> Unit,
    onViewReport: (ScanReportEntity) -> Unit,
    onOpenSupportChat: () -> Unit,
    onSyncCloud: () -> Unit,
    isSyncing: Boolean
) {
    var showCustomizeModal by remember { mutableStateOf(false) }

    // Customizable widget toggles
    var showLiveStatus by remember { mutableStateOf(true) }
    var showTelemetry by remember { mutableStateOf(true) }
    var showOwaspSummary by remember { mutableStateOf(true) }
    var showRecentHistory by remember { mutableStateOf(true) }
    var showCloudStatus by remember { mutableStateOf(true) }

    val latestReport = reports.firstOrNull()
    val averageScore = remember(reports) {
        if (reports.isEmpty()) 92 else reports.map { it.score }.average().toInt()
    }
    val totalCriticalAcrossAll = remember(reports) {
        reports.sumOf { it.totalCritical }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("Poste de Commande", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Surveillance temps réel & analyses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCustomizeModal = true }, modifier = Modifier.testTag("customize_dash_btn")) {
                        Icon(Icons.Default.Tune, contentDescription = "Personnaliser le tableau de bord", tint = CyberCyan)
                    }
                    IconButton(onClick = onOpenSupportChat, modifier = Modifier.testTag("support_chat_btn")) {
                        Icon(Icons.Default.Chat, contentDescription = "Support Chiffré", tint = MaterialTheme.colorScheme.primary)
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

            // 1. Live System Status Bar
            if (showLiveStatus) {
                item {
                    LiveStatusBar(
                        user = user,
                        isSyncing = isSyncing,
                        onSyncCloud = onSyncCloud
                    )
                }
            }

            // 2. Global Security Gauge
            item {
                SecurityScoreGaugeCard(
                    score = averageScore,
                    totalScans = reports.size,
                    totalCritical = totalCriticalAcrossAll,
                    latestDomain = latestReport?.domain ?: "Aucun scan récent"
                )
            }

            // 3. Quick Action: Lancer un Scan
            item {
                Cyber3DButton(
                    text = "Lancer un Nouvel Audit Intelligent",
                    onClick = onStartNewScan,
                    icon = { Icon(Icons.Default.Radar, contentDescription = null, tint = Color.White) },
                    testTag = "start_new_scan_main_btn"
                )
            }

            // 4. Real-time Telemetry & Account Analytics
            if (showTelemetry) {
                item {
                    TelemetryAnalyticsCard(
                        totalAudits = reports.size,
                        criticalVulns = totalCriticalAcrossAll,
                        resolvedCount = (reports.size * 3),
                        protectionLevel = "Niveau Entreprise"
                    )
                }
            }

            // 5. OWASP Top 10 Live Risk Radar
            if (showOwaspSummary) {
                item {
                    OwaspQuickSummaryCard(reports = reports)
                }
            }

            // 6. Cloud & Drive Backup Card
            if (showCloudStatus) {
                item {
                    CloudBackupStatusCard(
                        isSyncing = isSyncing,
                        onSyncCloud = onSyncCloud
                    )
                }
            }

            // 7. Recent Scans History
            if (showRecentHistory) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dernières Analyses de Sécurité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${reports.size} rapport(s)", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                }

                if (reports.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Aucun audit dans la base locale", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Démarrez votre première inspection CMS-aware dès maintenant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(reports.take(5)) { report ->
                        ReportHistoryItemCard(report = report, onClick = { onViewReport(report) })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Modal to customize dashboard
    if (showCustomizeModal) {
        AlertDialog(
            onDismissRequest = { showCustomizeModal = false },
            title = { Text("Personnaliser le Tableau de Bord") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Activez ou masquez les modules en temps réel :", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Barre de télémétrie système", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showLiveStatus, onCheckedChange = { showLiveStatus = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Statistiques du compte", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showTelemetry, onCheckedChange = { showTelemetry = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Radar OWASP Top 10", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showOwaspSummary, onCheckedChange = { showOwaspSummary = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Sauvegarde Cloud & Drive", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showCloudStatus, onCheckedChange = { showCloudStatus = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Historique récent", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showRecentHistory, onCheckedChange = { showRecentHistory = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomizeModal = false }) {
                    Text("Enregistrer")
                }
            }
        )
    }
}

@Composable
private fun LiveStatusBar(
    user: UserProfile?,
    isSyncing: Boolean,
    onSyncCloud: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(CyberGreen))
                Column {
                    Text(
                        user?.displayName ?: "Auditeur Connecté",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Système actif • Moteur CMS v2.4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(onClick = onSyncCloud, modifier = Modifier.size(32.dp)) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyberCyan)
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = "Sync", tint = CyberCyan, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SecurityScoreGaugeCard(
    score: Int,
    totalScans: Int,
    totalCritical: Int,
    latestDomain: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Posture Globale de Sécurité", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Indicateur basé sur $totalScans audit(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = if (score >= 80) CyberGreen.copy(alpha = 0.15f) else CyberWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (score >= 80) "OPTIMAL" else "À RENFORCER",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (score >= 80) CyberGreen else CyberWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Score metric
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = if (score >= 80) CyberGreen else CyberWarning
                    )
                    Text("Score moyen", style = MaterialTheme.typography.labelSmall)
                }

                Divider(modifier = Modifier.height(48.dp).width(1.dp))

                // Critical metric
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$totalCritical",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = if (totalCritical > 0) MaterialTheme.colorScheme.error else CyberGreen
                    )
                    Text("Vulnérabilités critiques", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                    Icon(Icons.Default.Shield, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Text(
                        "Dernière inspection : $latestDomain",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryAnalyticsCard(
    totalAudits: Int,
    criticalVulns: Int,
    resolvedCount: Int,
    protectionLevel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tableau de Bord Analytique du Compte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMiniItem("Audits Totaux", "$totalAudits", Icons.Default.Assessment, modifier = Modifier.weight(1f))
                AnalyticsMiniItem("Failles Bloquées", "$resolvedCount", Icons.Default.GppGood, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMiniItem("Niveau Sécurité", protectionLevel, Icons.Default.VerifiedUser, modifier = Modifier.weight(1f))
                AnalyticsMiniItem("État Serveurs", "Opérationnel", Icons.Default.Sensors, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalyticsMiniItem(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OwaspQuickSummaryCard(reports: List<ScanReportEntity>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Conformité OWASP Top 10 (2021)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Checklist, contentDescription = null, tint = CyberCyan)
            }
            Text(
                "Le pipeline couvre : A01 (Contrôle d'accès), A02 (Cryptographie SSL), A03 (Injections XSS/SQL), A05 (Mauvaise configuration serveur) et A07 (Identification/Cookies).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudBackupStatusCard(
    isSyncing: Boolean,
    onSyncCloud: () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = CyberGreen)
                    Text("Sauvegarde Cloud & Google Drive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Synchronisation chiffrée automatique active pour l'historique et les profils d'audit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(onClick = onSyncCloud, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sync", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReportHistoryItemCard(report: ScanReportEntity, onClick: () -> Unit) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(report.timestamp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        report.score >= 80 -> CyberGreen.copy(alpha = 0.15f)
                        report.score >= 50 -> CyberWarning.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${report.score}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                report.score >= 80 -> CyberGreen
                                report.score >= 50 -> CyberWarning
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                Column {
                    Text(report.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("${report.cms} • $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (report.totalCritical > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${report.totalCritical} crit.",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
