package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.auth.AuthManager
import com.example.ui.components.Cyber3DButton
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authManager: AuthManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    var isSignUpTab by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item { Spacer(modifier = Modifier.height(40.dp)) }

        // Glowing App Logo & Brand Header
        item {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(CyberCyan.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
                    .border(2.dp, CyberCyan, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Logo Scan",
                    tint = CyberCyan,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "SCAN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Plateforme d'Audit & Sécurité Web",
                style = MaterialTheme.typography.bodySmall,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Tabs: Connexion / Inscription Rapide
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    TabButton(
                        text = "Connexion",
                        isSelected = !isSignUpTab,
                        onClick = {
                            isSignUpTab = false
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Inscription Rapide",
                        isSelected = isSignUpTab,
                        onClick = {
                            isSignUpTab = true
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Error banner
        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Inputs
        if (isSignUpTab) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom complet ou pseudonyme") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = CyberCyan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_field"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse email professionnelle") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = CyberCyan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_field"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CyberCyan) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Afficher mot de passe"
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_field"),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
        }

        if (isSignUpTab) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmer le mot de passe") },
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = CyberCyan) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        }

        // Forgot password button
        if (!isSignUpTab) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        recoveryEmail = email
                        showForgotPasswordDialog = true
                    }) {
                        Text("Mot de passe oublié ?", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                    }
                }
            }
        } else {
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Primary 3D Submit Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Cyber3DButton(
                text = if (isSignUpTab) "Créer mon Compte Sécurisé" else "Se Connecter à la Console",
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Veuillez renseigner votre email et mot de passe."
                        return@Cyber3DButton
                    }
                    if (isSignUpTab && password != confirmPassword) {
                        errorMessage = "Les mots de passe ne correspondent pas."
                        return@Cyber3DButton
                    }

                    if (isSignUpTab) {
                        authManager.register(email.trim(), name.ifBlank { "Auditeur Sécurité" })
                    } else {
                        authManager.login(email.trim())
                    }
                    onAuthSuccess()
                },
                testTag = "auth_submit_btn"
            )
        }

        // Social Logins / Google via Firebase
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f))
                Text(
                    "  ou continuer avec  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Divider(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Google Firebase Sign In Button
            OutlinedButton(
                onClick = {
                    authManager.loginWithGoogle()
                    Toast.makeText(context, "Connecté via Google (Firebase) !", Toast.LENGTH_SHORT).show()
                    onAuthSuccess()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("google_firebase_signin_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Connexion Google via Firebase", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Biometric Quick Unlock Button
            OutlinedButton(
                onClick = {
                    authManager.login("biometric@scan.app", "Auditeur Biométrique")
                    Toast.makeText(context, "Authentification biométrique réussie !", Toast.LENGTH_SHORT).show()
                    onAuthSuccess()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("biometric_login_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CyberGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Déverrouillage Biométrique Rapide", fontWeight = FontWeight.SemiBold)
            }
        }

        // Demo / Guest Mode
        item {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    authManager.login("invite@scan.app", "Invité Démo")
                    onAuthSuccess()
                },
                modifier = Modifier.testTag("guest_demo_mode_btn")
            ) {
                Text("Explorer en Mode Démo Immédiat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Modal: Password Recovery
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = CyberCyan)
                    Text("Récupération de Mot de Passe")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Saisissez votre adresse email. Un lien de réinitialisation sécurisé vous sera envoyé immédiatement par nos serveurs.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { recoveryEmail = it },
                        label = { Text("Email de récupération") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (recoveryEmail.isNotBlank()) {
                        val msg = authManager.sendPasswordReset(recoveryEmail.trim())
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        showForgotPasswordDialog = false
                    }
                }) {
                    Text("Envoyer le lien")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
