package com.crabtrack.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.domain.model.SensorAlert
import com.crabtrack.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID_WARNING = "crabtrack_warnings"
        const val CHANNEL_ID_CRITICAL = "crabtrack_critical"
        const val CHANNEL_ID_ALERTS = "CRABTRACK_ALERTS"
        const val CHANNEL_ID_MOLTING = "CRABTRACK_MOLTING"
        const val NOTIFICATION_ID_BASE = 1000
        const val NOTIFICATION_ID_ALERTS_BASE = 2000
        const val NOTIFICATION_ID_MOLTING_BASE = 3000
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Sensor Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Warning alerts for sensor readings"
            }
            
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for sensor readings"
                enableVibration(true)
            }
            
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "CrabTrack Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High importance alerts for water quality parameters"
                enableVibration(true)
                enableLights(true)
            }
            
            val moltingChannel = NotificationChannel(
                CHANNEL_ID_MOLTING,
                "Molting Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for hermit crab molting events"
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(moltingChannel)
        }
    }
    
    fun showSensorAlert(alert: SensorAlert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val channelId = when (alert.alertLevel) {
            AlertLevel.WARNING -> CHANNEL_ID_WARNING
            AlertLevel.CRITICAL -> CHANNEL_ID_CRITICAL
            AlertLevel.NORMAL -> return // Don't show notifications for normal readings
        }
        
        val priority = when (alert.alertLevel) {
            AlertLevel.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            AlertLevel.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            AlertLevel.NORMAL -> NotificationCompat.PRIORITY_LOW
        }
        
        val iconRes = when (alert.alertLevel) {
            AlertLevel.WARNING -> R.drawable.ic_warning
            AlertLevel.CRITICAL -> R.drawable.ic_critical
            AlertLevel.NORMAL -> R.drawable.ic_dashboard
        }
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle("${alert.alertLevel.name}: ${alert.sensorType.displayName}")
            .setContentText(alert.message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${alert.message}\nCurrent value: ${alert.currentValue} ${alert.sensorType.unit}")
            )
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE + alert.sensorType.ordinal
        
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
            // In a production app, you might want to log this or handle gracefully
        }
    }
    
    fun clearSensorAlert(sensorType: com.crabtrack.app.data.model.SensorType) {
        val notificationId = NOTIFICATION_ID_BASE + sensorType.ordinal
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
    fun clearAllAlerts() {
        com.crabtrack.app.data.model.SensorType.values().forEach { sensorType ->
            clearSensorAlert(sensorType)
        }
    }
}