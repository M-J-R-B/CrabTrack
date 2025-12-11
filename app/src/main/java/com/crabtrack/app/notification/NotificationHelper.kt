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
        const val CHANNEL_ID_FEEDING = "CRABTRACK_FEEDING"
        const val CHANNEL_ID_CLEANING = "CRABTRACK_CLEANING"
        const val NOTIFICATION_ID_BASE = 1000
        const val NOTIFICATION_ID_ALERTS_BASE = 2000
        const val NOTIFICATION_ID_MOLTING_BASE = 3000
        const val NOTIFICATION_ID_FEEDING_BASE = 4000
        const val NOTIFICATION_ID_CLEANING_BASE = 5000
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

            val feedingChannel = NotificationChannel(
                CHANNEL_ID_FEEDING,
                "Feeding Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for scheduled feeding times and overdue alerts"
                enableVibration(true)
                enableLights(true)
            }

            val cleaningChannel = NotificationChannel(
                CHANNEL_ID_CLEANING,
                "Cleaning Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for scheduled cleaning times and overdue alerts"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(criticalChannel)
            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(moltingChannel)
            notificationManager.createNotificationChannel(feedingChannel)
            notificationManager.createNotificationChannel(cleaningChannel)
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

    /**
     * Show water quality alert notification.
     * Used by AlertWorker for background monitoring.
     */
    fun showWaterQualityAlert(alert: com.crabtrack.app.data.model.Alert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.crabtrack.app.OPEN_ALERTS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when (alert.severity) {
            com.crabtrack.app.data.model.AlertSeverity.CRITICAL -> CHANNEL_ID_CRITICAL
            com.crabtrack.app.data.model.AlertSeverity.WARNING -> CHANNEL_ID_WARNING
            com.crabtrack.app.data.model.AlertSeverity.INFO -> CHANNEL_ID_WARNING
        }

        val priority = when (alert.severity) {
            com.crabtrack.app.data.model.AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            com.crabtrack.app.data.model.AlertSeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            com.crabtrack.app.data.model.AlertSeverity.INFO -> NotificationCompat.PRIORITY_LOW
        }

        val iconRes = when (alert.severity) {
            com.crabtrack.app.data.model.AlertSeverity.CRITICAL -> R.drawable.ic_critical
            com.crabtrack.app.data.model.AlertSeverity.WARNING -> R.drawable.ic_warning
            com.crabtrack.app.data.model.AlertSeverity.INFO -> R.drawable.ic_dashboard
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle("🦀 ${alert.severity.name}: ${alert.parameter}")
            .setContentText(alert.message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${alert.message}\n\nTank: ${alert.tankId}\n\nTap to view details")
            )
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_ALERTS_BASE + alert.id.hashCode() % 1000

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Notification permission denied", e)
        }
    }

    /**
     * Show feeding alert notification.
     * Used by FeedingCheckWorker for overdue feeding reminders.
     */
    fun showFeedingAlert(
        tankName: String,
        isOverdue: Boolean,
        scheduledTime: String,
        overdueMinutes: Int = 0
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.crabtrack.app.OPEN_DASHBOARD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            tankName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message) = if (isOverdue) {
            val overdueText = if (overdueMinutes >= 60) {
                val hours = overdueMinutes / 60
                val mins = overdueMinutes % 60
                if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
            } else {
                "$overdueMinutes min"
            }
            "Feeding Overdue!" to "$tankName feeding was scheduled for $scheduledTime (overdue by $overdueText)"
        } else {
            "Feeding Time" to "$tankName is due for feeding at $scheduledTime"
        }

        val iconRes = if (isOverdue) R.drawable.ic_warning else R.drawable.ic_time

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FEEDING)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\nTap to view feeding status")
            )
            .setPriority(if (isOverdue) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_FEEDING_BASE + tankName.hashCode() % 1000

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
            android.util.Log.i("NotificationHelper", "Feeding alert shown for $tankName (overdue=$isOverdue)")
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Notification permission denied", e)
        }
    }

    /**
     * Clear feeding notification for a specific tank.
     */
    fun clearFeedingAlert(tankName: String) {
        val notificationId = NOTIFICATION_ID_FEEDING_BASE + tankName.hashCode() % 1000
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Show cleaning alert notification.
     * Used by CleaningCheckWorker for overdue cleaning reminders.
     */
    fun showCleaningAlert(
        isOverdue: Boolean,
        scheduledTime: String,
        overdueMinutes: Int = 0
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.crabtrack.app.OPEN_DASHBOARD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            "cleaning".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message) = if (isOverdue) {
            val overdueText = if (overdueMinutes >= 60) {
                val hours = overdueMinutes / 60
                val mins = overdueMinutes % 60
                if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
            } else {
                "$overdueMinutes min"
            }
            "Cleaning Overdue!" to "Tank cleaning was scheduled for $scheduledTime (overdue by $overdueText)"
        } else {
            "Cleaning Time" to "Tank cleaning is due at $scheduledTime"
        }

        val iconRes = if (isOverdue) R.drawable.ic_warning else R.drawable.ic_time

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CLEANING)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\nTap to view cleaning status")
            )
            .setPriority(if (isOverdue) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_CLEANING_BASE

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
            android.util.Log.i("NotificationHelper", "Cleaning alert shown (overdue=$isOverdue)")
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Notification permission denied", e)
        }
    }

    /**
     * Clear cleaning notification.
     */
    fun clearCleaningAlert() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CLEANING_BASE)
    }

    /**
     * Show molting alert notification.
     * Used by AlertMonitoringService for real-time molting detection alerts.
     */
    fun showMoltingAlert(
        tankName: String,
        confidence: Double,
        detectionClass: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.crabtrack.app.OPEN_MOLTING"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "molting")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            tankName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confidencePercent = (confidence * 100).toInt()
        val title = "Molting Detected!"
        val message = "$detectionClass detected in $tankName ($confidencePercent% confidence)"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MOLTING)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\nTap to view molting alerts and take action")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = NOTIFICATION_ID_MOLTING_BASE + tankName.hashCode() % 1000

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
            android.util.Log.i("NotificationHelper", "Molting alert shown for $tankName (confidence=$confidencePercent%)")
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "Notification permission denied", e)
        }
    }

    /**
     * Clear molting notification for a specific tank.
     */
    fun clearMoltingAlert(tankName: String) {
        val notificationId = NOTIFICATION_ID_MOLTING_BASE + tankName.hashCode() % 1000
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}