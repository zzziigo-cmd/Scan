package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScanReportEntity
import com.example.data.ScanReportRepository
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberWarning
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportViewerScreen(
    report: ScanReportEntity,
    onNavigateBack: () -> Unit,
    onDeleteReport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Vue Synthétique", "JSON Détaillé", "Export & Partage")

    val dateFormatted = remember(report.timestamp) {
        SimpleDateFormat("dd MMMM yyyy à HH:mm", Locale.FRANCE).format(Date(report.timestamp))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(report.domain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Audit du $dateFormatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("report_viewer_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (onDeleteReport != null) {
                        IconButton(onClick = onDeleteReport) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> SyntheticView(report)
                1 -> JsonDetailedView(report.jsonContent, context)
                2 -> ExportShareView(report, context)
            }
        }
    }
}

@Composable
private fun SyntheticView(report: ScanReportEntity) {
    val findings = remember(report.jsonContent) {
        parseFindings(report.jsonContent)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Score Global de Sécurité", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${report.score} / 100",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                report.score >= 80 -> CyberGreen
                                report.score >= 50 -> CyberWarning
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text("CMS : ${report.cms}", style = MaterialTheme.typography.bodySmall)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                            Text("Statut Général", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (report.score >= 80) "Robuste" else if (report.score >= 50) "Modéré" else "Critique",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (report.score >= 80) CyberGreen else if (report.score >= 50) CyberWarning else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Répartition des Résultats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(
                    label = "Critiques",
                    count = report.totalCritical,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "Alertes",
                    count = report.totalWarnings,
                    color = CyberWarning,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "Conformes",
                    count = report.totalPassed,
                    color = CyberGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (findings.isNotEmpty()) {
            item {
                Text("Détail des Constats par Étape", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            items(findings) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.severity == "CRITIQUE")
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.step, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (item.severity == "CRITIQUE") MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else if (item.severity == "AVERTISSEMENT") CyberWarning.copy(alpha = 0.15f)
                                else CyberGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    item.severity,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.severity == "CRITIQUE") MaterialTheme.colorScheme.error
                                    else if (item.severity == "AVERTISSEMENT") CyberWarning
                                    else CyberGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun JsonDetailedView(jsonString: String, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Structure JSON Complète", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Rapport JSON", jsonString)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "JSON copié dans le presse-papiers !", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = CyberCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            SelectionContainer {
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        text = jsonString,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportShareView(report: ScanReportEntity, context: Context) {
    val formattedText = remember(report) {
        ScanReportRepository.generateFormattedSummaryText(report)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exportation & Téléchargement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Générez un export au format JSON structuré ou un rapport officiel formaté prêt pour l'archivage ou l'impression PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Cyber3DButton(
                text = "Générer & Exporter le Document PDF",
                onClick = {
                    com.example.util.ReportExportHelper.sharePdfReport(context, report)
                },
                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White) }
            )
        }

        item {
            Cyber3DButton(
                text = "Exporter Fichier JSON Structuré",
                onClick = {
                    com.example.util.ReportExportHelper.shareJsonReport(context, report)
                },
                primaryColor = MaterialTheme.colorScheme.secondary,
                icon = { Icon(Icons.Default.Code, contentDescription = null, tint = Color.White) }
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aperçu de la Synthèse Exportée", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            formattedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class ParsedFinding(
    val step: String,
    val title: String,
    val severity: String,
    val description: String
)

private fun parseFindings(json: String): List<ParsedFinding> {
    val list = mutableListOf<ParsedFinding>()
    try {
        val root = JSONObject(json)
        val findings = root.optJSONArray("findings") ?: return emptyList()
        for (i in 0 until findings.length()) {
            val item = findings.getJSONObject(i)
            list.add(
                ParsedFinding(
                    step = item.optString("step", "Général"),
                    title = item.optString("title", "Contrôle"),
                    severity = item.optString("severity", "INFO"),
                    description = item.optString("description", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
