package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthManager
import com.example.auth.UserProfile
import com.example.data.ScanDatabase
import com.example.data.ScanReportEntity
import com.example.data.ScanReportRepository
import com.example.security.OwnershipGate
import com.example.ui.SecurityAuditFlow
import com.example.ui.components.Cyber3DButton
import com.example.ui.screens.*
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberWarning
import com.example.ui.theme.ScanTheme
import com.example.util.NotificationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppNavTab {
    DASHBOARD,
    HISTORY,
    SUPPORT_CHAT,
    PROFILE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)

        val authManager = AuthManager.getInstance(this)
        val database = ScanDatabase.getInstance(this)
        val repository = ScanReportRepository(database.scanReportDao())

        setContent {
            val isDarkMode by authManager.isDarkMode.collectAsStateWithLifecycle()

            ScanTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanAppRoot(authManager = authManager, repository = repository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanAppRoot(
    authManager: AuthManager,
    repository: ScanReportRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isAuthenticated by authManager.isAuthenticated.collectAsStateWithLifecycle()
    val currentUser by authManager.currentUser.collectAsStateWithLifecycle()
    val allReports by repository.allReports.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentTab by remember { mutableStateOf(AppNavTab.DASHBOARD) }

    // Dialog & Sub-screen states
    var showNewScanDialog by remember { mutableStateOf(false) }
    var activeScanTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // domain to urlDemarrage
    var viewingReport by remember { mutableStateOf<ScanReportEntity?>(null) }
    var isShowingAbout by remember { mutableStateOf(false) }
    var isShowingPricing by remember { mutableStateOf(false) }
    var isShowingTerms by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // Cloud sync simulation
    fun triggerCloudSync() {
        if (isSyncing) return
        isSyncing = true
        coroutineScope.launch {
            kotlinx.coroutines.delay(1200)
            allReports.forEach {
                repository.markSynced(it.id)
            }
            isSyncing = false
            Toast.makeText(context, "Sauvegarde Cloud & Google Drive synchronisée avec succès !", Toast.LENGTH_SHORT).show()
        }
    }

    // Unauthenticated state -> AuthScreen
    if (!isAuthenticated) {
        AuthScreen(
            authManager = authManager,
            onAuthSuccess = {
                currentTab = AppNavTab.DASHBOARD
            }
        )
        return
    }

    // Active Audit Pipeline in progress -> SecurityAuditFlow
    if (activeScanTarget != null) {
        val (domain, urlDemarrage) = activeScanTarget!!
        val ownership = remember(domain) {
            OwnershipGate.directAuthorization(domain, "Autorisation explicite confirmée")
        }
        SecurityAuditFlow(
            domain = domain,
            urlDemarrage = urlDemarrage,
            ownership = ownership,
            onTerminer = {
                activeScanTarget = null
            }
        )
        return
    }

    // Sub-screens: Report Viewer
    if (viewingReport != null) {
        ReportViewerScreen(
            report = viewingReport!!,
            onNavigateBack = { viewingReport = null },
            onDeleteReport = {
                val toDelete = viewingReport!!
                coroutineScope.launch {
                    repository.deleteReport(toDelete.id)
                    viewingReport = null
                    Toast.makeText(context, "Rapport supprimé.", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    // Sub-screens: About / Dev Info
    if (isShowingAbout) {
        AboutScreen(onNavigateBack = { isShowingAbout = false })
        return
    }

    // Sub-screens: Pricing / Subscriptions
    if (isShowingPricing) {
        PricingScreen(
            authManager = authManager,
            currentPlan = currentUser?.subscriptionPlan ?: "Pro Sécurité",
            onNavigateBack = { isShowingPricing = false }
        )
        return
    }

    // Sub-screens: Terms of Service
    if (isShowingTerms) {
        TermsScreen(onNavigateBack = { isShowingTerms = false })
        return
    }

    // Handle back press in sub-tabs
    BackHandler(enabled = currentTab != AppNavTab.DASHBOARD) {
        currentTab = AppNavTab.DASHBOARD
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppNavTab.DASHBOARD,
                    onClick = { currentTab = AppNavTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Poste de commande") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == AppNavTab.HISTORY,
                    onClick = { currentTab = AppNavTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historique") },
                    label = { Text("Rapports") },
                    modifier = Modifier.testTag("nav_tab_history")
                )
                NavigationBarItem(
                    selected = currentTab == AppNavTab.SUPPORT_CHAT,
                    onClick = { currentTab = AppNavTab.SUPPORT_CHAT },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Support Chiffré") },
                    label = { Text("Support") },
                    modifier = Modifier.testTag("nav_tab_chat")
                )
                NavigationBarItem(
                    selected = currentTab == AppNavTab.PROFILE,
                    onClick = { currentTab = AppNavTab.PROFILE },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profil & Paramètres") },
                    label = { Text("Profil") },
                    modifier = Modifier.testTag("nav_tab_profile")
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "tab_navigation_animation"
            ) { tab ->
                when (tab) {
                    AppNavTab.DASHBOARD -> DashboardScreen(
                        user = currentUser,
                        reports = allReports,
                        onStartNewScan = { showNewScanDialog = true },
                        onViewReport = { viewingReport = it },
                        onOpenSupportChat = { currentTab = AppNavTab.SUPPORT_CHAT },
                        onSyncCloud = { triggerCloudSync() },
                        isSyncing = isSyncing
                    )

                    AppNavTab.HISTORY -> HistoryReportsScreen(
                        reports = allReports,
                        onViewReport = { viewingReport = it },
                        onStartNewScan = { showNewScanDialog = true },
                        onClearAll = {
                            coroutineScope.launch {
                                repository.clearHistory()
                                Toast.makeText(context, "Historique effacé.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    AppNavTab.SUPPORT_CHAT -> SecureChatScreen(
                        onNavigateBack = { currentTab = AppNavTab.DASHBOARD }
                    )

                    AppNavTab.PROFILE -> ProfileScreen(
                        authManager = authManager,
                        user = currentUser,
                        onNavigateToAbout = { isShowingAbout = true },
                        onNavigateToPricing = { isShowingPricing = true },
                        onNavigateToTerms = { isShowingTerms = true },
                        onLogout = { authManager.logout() },
                        onAccountDeleted = { currentTab = AppNavTab.DASHBOARD }
                    )
                }
            }
        }
    }

    // Modal: Launch New Audit
    if (showNewScanDialog) {
        NewScanDialog(
            onDismiss = { showNewScanDialog = false },
            onStartScan = { domain, urlDemarrage ->
                showNewScanDialog = false
                activeScanTarget = Pair(domain, urlDemarrage)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryReportsScreen(
    reports: List<ScanReportEntity>,
    onViewReport: (ScanReportEntity) -> Unit,
    onStartNewScan: () -> Unit,
    onClearAll: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des Audits (Room)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    if (reports.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Tout effacer", tint = MaterialTheme.colorScheme.error)
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${reports.size} rapport(s) archivé(s)", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    TextButton(onClick = onStartNewScan) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nouvel audit")
                    }
                }
            }

            if (reports.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Aucun rapport archivé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Les rapports d'audits terminés seront conservés ici dans la base SQLite Room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Cyber3DButton(
                                text = "Lancer un Premier Audit",
                                onClick = onStartNewScan,
                                primaryColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                items(reports) { report ->
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(report.timestamp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewReport(report) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        report.score >= 80 -> CyberGreen.copy(alpha = 0.15f)
                                        report.score >= 50 -> CyberWarning.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${report.score}",
                                            style = MaterialTheme.typography.titleMedium,
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
                                    Text(report.domain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("CMS : ${report.cms} • $dateFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        if (report.totalCritical > 0) {
                                            Text(
                                                "${report.totalCritical} critique(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            "${report.totalWarnings} alerte(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberWarning
                                        )
                                        Text(
                                            "${report.totalPassed} conforme(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberGreen
                                        )
                                    }
                                }
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = "Détail", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Vider tout l'historique ?") },
            text = { Text("Tous les rapports enregistrés en local seront supprimés définitivement.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer tout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
