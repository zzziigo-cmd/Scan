package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

data class SecurityAlertItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val domain: String,
    val isCritical: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object NotificationHelper {
    private const val CHANNEL_ID = "security_scan_alerts"
    private const val CHANNEL_NAME = "Alertes de Sécurité Scan"
    private const val CHANNEL_DESC = "Notifications push d'alertes de sécurité et de détection de vulnérabilités"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendSecurityAlert(context: Context, domain: String, title: String, message: String, isCritical: Boolean) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val iconRes = android.R.drawable.ic_dialog_alert

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(if (isCritical) "🚨 $title" else "🛡️ $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        } catch (_: SecurityException) {
            // Handled when notification permission is pending
        }
    }
}
