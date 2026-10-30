package com.example.util

import android.content.Context
import android.os.Build

object BiometricHelper {
    fun isBiometricAvailable(context: Context): Boolean {
        // Safe check for biometric capability on device
        return try {
            val packageManager = context.packageManager
            packageManager.hasSystemFeature("android.hardware.fingerprint") ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            packageManager.hasSystemFeature("android.hardware.biometrics.face"))
        } catch (_: Exception) {
            false
        }
    }
}
