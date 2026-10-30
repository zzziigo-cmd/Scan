package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureChatScreen(
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    text = "Bonjour ! Vous êtes connecté au canal sécurisé du Support Technique & Cyber-Audits de l'application Scan.",
                    isFromUser = false
                ),
                ChatMessage(
                    text = "Toutes vos communications et rapports partagés sont chiffrés de bout en bout (protocole Signal / AES-256-GCM). Comment pouvons-nous vous assister ?",
                    isFromUser = false
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Support Cyber-Expert", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(12.dp))
                                Text("Chiffré E2EE", style = MaterialTheme.typography.labelSmall, color = CyberGreen)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // E2EE Info Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Text(
                        "Canal chiffré de bout en bout • Vos clés privées restent locales",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }

            // Quick Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {
                        inputText = "Comment corriger une réflexion XSS détectée ?"
                    },
                    label = { Text("Fixer XSS", style = MaterialTheme.typography.labelSmall) }
                )
                SuggestionChip(
                    onClick = {
                        inputText = "Aide pour masquer la page d'administration"
                    },
                    label = { Text("Protéger Admin", style = MaterialTheme.typography.labelSmall) }
                )
                SuggestionChip(
                    onClick = {
                        inputText = "Contacter Redouane Oulaati"
                    },
                    label = { Text("Contact Direct", style = MaterialTheme.typography.labelSmall) }
                )
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message chiffré au support...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val userMsg = ChatMessage(text = inputText.trim(), isFromUser = true)
                                val query = inputText.trim()
                                inputText = ""
                                val updated = messages + userMsg
                                messages = updated

                                coroutineScope.launch {
                                    listState.animateScrollToItem(updated.size - 1)
                                    kotlinx.coroutines.delay(600)
                                    val replyText = when {
                                        query.contains("xss", ignoreCase = true) ->
                                            "Pour neutraliser la réflexion XSS : appliquez l'échappement contextuel (htmlspecialchars en PHP, encodage HTML en Blade/Twig) et activez l'en-tête Content-Security-Policy (CSP)."
                                        query.contains("admin", ignoreCase = true) ->
                                            "Pour protéger votre interface admin : restreignez l'accès par adresse IP dans .htaccess / Nginx, changez l'URL par défaut (/adminXXXX), et activez une authentification double facteur (2FA)."
                                        query.contains("redouane", ignoreCase = true) || query.contains("contact", ignoreCase = true) ->
                                            "Vous pouvez contacter directement l'auteur Redouane Oulaati par email : redouaneoulaati@gmail.com ou téléphone / WhatsApp : +212646898915."
                                        else ->
                                            "Votre demande chiffrée a été enregistrée avec succès. Notre équipe d'ingénieurs en cybersécurité analyse votre requête."
                                    }
                                    messages = updated + ChatMessage(text = replyText, isFromUser = false)
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        modifier = Modifier
                            .testTag("chat_send_button")
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    val isUser = message.isFromUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isEncrypted) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Chiffré",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
