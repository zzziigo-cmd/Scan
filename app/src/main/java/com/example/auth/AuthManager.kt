package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val email: String,
    val displayName: String,
    val organization: String = "Cyber Defense Lab",
    val phone: String = "+212646898915",
    val role: String = "Auditeur Sécurité Certifié",
    val subscriptionPlan: String = "Pro Sécurité",
    val is2FAEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val isCloudSyncEnabled: Boolean = true,
    val isDarkMode: Boolean = true,
    val totalScansCount: Int = 14,
    val memberSince: String = "Octobre 2026"
)

class AuthManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("scan_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserProfile?>(loadSavedUser())
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(_currentUser.value != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("key_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private fun loadSavedUser(): UserProfile? {
        val email = prefs.getString("user_email", null) ?: return null
        val name = prefs.getString("user_name", "Auditeur Sécurité") ?: "Auditeur Sécurité"
        val is2fa = prefs.getBoolean("user_2fa", false)
        val isBio = prefs.getBoolean("user_bio", true)
        val isCloud = prefs.getBoolean("user_cloud", true)
        val dark = prefs.getBoolean("key_dark_mode", true)
        val plan = prefs.getString("user_plan", "Pro Sécurité") ?: "Pro Sécurité"

        return UserProfile(
            email = email,
            displayName = name,
            is2FAEnabled = is2fa,
            isBiometricEnabled = isBio,
            isCloudSyncEnabled = isCloud,
            isDarkMode = dark,
            subscriptionPlan = plan
        )
    }

    fun login(email: String, name: String = "Auditeur Sécurité"): Boolean {
        prefs.edit()
            .putString("user_email", email)
            .putString("user_name", name)
            .apply()

        val user = UserProfile(
            email = email,
            displayName = name,
            isDarkMode = _isDarkMode.value
        )
        _currentUser.value = user
        _isAuthenticated.value = true
        return true
    }

    fun loginWithGoogle(): Boolean {
        return login("zzziigo@gmail.com", "Redouane Oulaati")
    }

    fun register(email: String, name: String): Boolean {
        return login(email, name)
    }

    fun updateProfile(name: String, organization: String, phone: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            displayName = name,
            organization = organization,
            phone = phone
        )
        _currentUser.value = updated
        prefs.edit().putString("user_name", name).apply()
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("key_dark_mode", enabled).apply()
        _currentUser.value?.let {
            _currentUser.value = it.copy(isDarkMode = enabled)
        }
    }

    fun toggle2FA(enabled: Boolean) {
        val current = _currentUser.value ?: return
        val updated = current.copy(is2FAEnabled = enabled)
        _currentUser.value = updated
        prefs.edit().putBoolean("user_2fa", enabled).apply()
    }

    fun toggleBiometric(enabled: Boolean) {
        val current = _currentUser.value ?: return
        val updated = current.copy(isBiometricEnabled = enabled)
        _currentUser.value = updated
        prefs.edit().putBoolean("user_bio", enabled).apply()
    }

    fun toggleCloudSync(enabled: Boolean) {
        val current = _currentUser.value ?: return
        val updated = current.copy(isCloudSyncEnabled = enabled)
        _currentUser.value = updated
        prefs.edit().putBoolean("user_cloud", enabled).apply()
    }

    fun upgradePlan(newPlan: String) {
        val current = _currentUser.value ?: return
        val updated = current.copy(subscriptionPlan = newPlan)
        _currentUser.value = updated
        prefs.edit().putString("user_plan", newPlan).apply()
    }

    fun sendPasswordReset(email: String): String {
        return "Un lien de réinitialisation sécurisé a été transmis avec succès à $email. Vérifiez votre boîte de réception."
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _isAuthenticated.value = false
    }

    fun deleteAccount(): Boolean {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _isAuthenticated.value = false
        return true
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AuthManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
