package com.crabtrack.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crabtrack.app.R
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertsNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telemetryRepository: TelemetryRepository
) {
    
    companion object {
        private const val CHANNEL_ID = "alerts"
        private const val NOTIFICATION_ID_BASE = 1001
        private const val ACTION_OPEN_ALERTS = "com.crabtrack.app.OPEN_ALERTS"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var monitoringJob: Job? = null
    private val notifiedAlerts = mutableSetOf<String>()
    private var lastNotificationTime = 0L
    private val notificationCooldown = 5000L // 5 seconds between notifications
    
    fun startCollection(scope: CoroutineScope) {
        stopCollection()
        
        monitoringJob = telemetryRepository
            .allAlerts
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .onEach { alertsList ->
                val currentTime = System.currentTimeMillis()
                
                // Process critical alerts first
                val criticalAlerts = alertsList.filter { it.severity == AlertSeverity.CRITICAL }
                criticalAlerts.forEach { alert ->
                    if (!notifiedAlerts.contains(alert.id)) {
                        showCriticalNotification(alert)
                        notifiedAlerts.add(alert.id)
                        lastNotificationTime = currentTime
                    }
                }
                
                // Process other alerts with cooldown
                val otherAlerts = alertsList.filter { it.severity != AlertSeverity.CRITICAL }
                if (otherAlerts.isNotEmpty() && 
                    currentTime - lastNotificationTime > notificationCooldown) {
                    
                    val newAlerts = otherAlerts.filter { !notifiedAlerts.contains(it.id) }
                    if (newAlerts.isNotEmpty()) {
                        showAlertNotification(newAlerts.first())
                        newAlerts.forEach { notifiedAlerts.add(it.id) }
                        lastNotificationTime = currentTime
                    }
                }
            }
            .launchIn(scope)
    }
    
    fun startMonitoring() {
        // Legacy method - delegates to startCollection with default scope
        // This should be replaced with proper scope injection
        stopMonitoring()
        monitoringJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            telemetryRepository.allAlerts
                .filter { it.isNotEmpty() }
                .collect { alertsList ->
                    val criticalAlerts = alertsList.filter { it.severity == AlertSeverity.CRITICAL }
                    if (criticalAlerts.isNotEmpty()) {
                        val alert = criticalAlerts.first()
                        if (!notifiedAlerts.contains(alert.id)) {
                            showCriticalNotification(alert)
                            notifiedAlerts.add(alert.id)
                        }
                    }
                }
        }
    }
    
    fun stopCollection() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    private fun showCriticalNotification(alert: Alert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERTS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_critical)
            .setContentTitle("🦀 Critical Alert")
            .setContentText(alert.message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${alert.message}\n\nParameter: ${alert.parameter}\nTank: ${alert.tankId}\n\nImmediate attention required!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 300, 300))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
        }
    }
    
    private fun showAlertNotification(alert: Alert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERTS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val priority = when (alert.severity) {
            AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            AlertSeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            AlertSeverity.INFO -> NotificationCompat.PRIORITY_LOW
        }
        
        val iconRes = when (alert.severity) {
            AlertSeverity.CRITICAL -> R.drawable.ic_critical
            AlertSeverity.WARNING -> R.drawable.ic_warning
            AlertSeverity.INFO -> R.drawable.ic_dashboard
        }
        
        val title = "${alert.severity.name}: ${alert.parameter}"
        val text = "${alert.message} (Tank: ${alert.tankId})"
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\nTap to view all alerts")
            )
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE + alert.id.hashCode() % 1000
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
        }
    }
    
    fun clearAlert(alertId: String) {
        val notificationId = NOTIFICATION_ID_BASE + alertId.hashCode() % 1000
        notificationManager.cancel(notificationId)
        notifiedAlerts.remove(alertId)
    }
    
    fun clearAllAlerts() {
        notifiedAlerts.clear()
        // Clear notifications by canceling all with our ID range
        for (i in 0..999) {
            notificationManager.cancel(NOTIFICATION_ID_BASE + i)
        }
        // Also clear the critical notification
        notificationManager.cancel(NOTIFICATION_ID_BASE)
    }
    
    fun getNotifiedAlertsCount(): Int = notifiedAlerts.size
    
    fun hasNotifiedAlerts(): Boolean = notifiedAlerts.isNotEmpty()
    
    fun resetNotificationThrottling() {
        lastNotificationTime = 0L
    }
}