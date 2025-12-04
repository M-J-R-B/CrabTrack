package com.crabtrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.notification.NotificationHelper
import com.crabtrack.app.presentation.MainActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service for 24/7 water quality monitoring.
 *
 * This service runs continuously in the background, monitoring Firebase for threshold violations
 * and showing notifications when water parameters exceed safe limits.
 *
 * Requirements:
 * - Shows persistent notification (Android requirement for foreground services)
 * - Monitors Firebase Realtime Database for water quality data
 * - Sends alerts immediately when thresholds are violated
 * - Runs even when app is closed (but not force-stopped)
 * - Automatically stops when user logs out
 */
@AndroidEntryPoint
class AlertMonitoringService : Service() {

    companion object {
        private const val TAG = "AlertMonitorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "alert_monitoring_channel"
        private const val CHANNEL_NAME = "Water Quality Monitoring"

        const val ACTION_START = "com.crabtrack.app.action.START_MONITORING"
        const val ACTION_STOP = "com.crabtrack.app.action.STOP_MONITORING"

        /**
         * Start the monitoring service
         */
        fun start(context: Context) {
            val intent = Intent(context, AlertMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.i(TAG, "Service start requested")
        }

        /**
         * Stop the monitoring service
         */
        fun stop(context: Context) {
            val intent = Intent(context, AlertMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            android.util.Log.i(TAG, "Service stop requested")
        }

        /**
         * Check if service is running
         */
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == AlertMonitoringService::class.java.name }
        }
    }

    @Inject
    lateinit var telemetryRepository: TelemetryRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    private val notifiedAlerts = mutableSetOf<String>()
    private var lastNotificationTime = 0L
    private val notificationCooldown = 30_000L // 30 seconds between similar alerts

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                // Check if user is authenticated
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    android.util.Log.w(TAG, "Cannot start monitoring - no user logged in")
                    stopSelf()
                    return START_NOT_STICKY
                }

                android.util.Log.i(TAG, "Starting foreground service for user: ${currentUser.uid}")

                // Start as foreground service with persistent notification
                val notification = createPersistentNotification()
                startForeground(NOTIFICATION_ID, notification)

                // Start monitoring Firebase
                startMonitoring()

                android.util.Log.i(TAG, "Foreground service started successfully")
            }
            ACTION_STOP -> {
                android.util.Log.i(TAG, "Stopping monitoring service")
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // START_STICKY: Service will be restarted if killed by system
        // This ensures monitoring continues even if Android kills the service
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding
        return null
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "Service destroyed")
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Start monitoring Firebase for threshold violations
     */
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            android.util.Log.d(TAG, "Monitoring already active")
            return
        }

        android.util.Log.i(TAG, "Starting Firebase monitoring...")

        monitoringJob = serviceScope.launch {
            telemetryRepository.allAlerts
                .filter { it.isNotEmpty() }
                .catch { e ->
                    android.util.Log.e(TAG, "Error in monitoring flow: ${e.message}", e)
                }
                .collect { alerts ->
                    android.util.Log.d(TAG, "Received ${alerts.size} alerts")
                    processAlerts(alerts)
                }
        }

        android.util.Log.i(TAG, "Monitoring job launched")
    }

    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        android.util.Log.i(TAG, "Stopping monitoring")
        monitoringJob?.cancel()
        monitoringJob = null
        notifiedAlerts.clear()
    }

    /**
     * Process alerts and show notifications
     */
    private fun processAlerts(alerts: List<com.crabtrack.app.data.model.Alert>) {
        val currentTime = System.currentTimeMillis()

        alerts.forEach { alert ->
            // Only process CRITICAL and WARNING alerts
            if (alert.severity == AlertSeverity.CRITICAL || alert.severity == AlertSeverity.WARNING) {

                // Check if we already notified for this alert recently
                val alertKey = "${alert.parameter}_${alert.severity}"
                val shouldNotify = !notifiedAlerts.contains(alertKey) ||
                                   (currentTime - lastNotificationTime) > notificationCooldown

                if (shouldNotify) {
                    android.util.Log.i(TAG, "Showing alert: ${alert.parameter} - ${alert.message}")
                    notificationHelper.showWaterQualityAlert(alert)
                    notifiedAlerts.add(alertKey)
                    lastNotificationTime = currentTime

                    // Update persistent notification to show latest alert
                    updatePersistentNotification(alert)
                } else {
                    android.util.Log.d(TAG, "Skipping duplicate alert: $alertKey")
                }
            }
        }

        // Clean up old notified alerts (keep last 50)
        if (notifiedAlerts.size > 50) {
            val toRemove = notifiedAlerts.size - 50
            notifiedAlerts.toList().take(toRemove).forEach { notifiedAlerts.remove(it) }
        }
    }

    /**
     * Create notification channel for the foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW  // Low importance for persistent notification
            ).apply {
                description = "Persistent notification for water quality monitoring service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the persistent notification that shows while service is running
     */
    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrabTrack Monitoring Active")
            .setContentText("Monitoring water quality 24/7")
            .setSmallIcon(R.drawable.ic_dashboard)
            .setOngoing(true)  // Cannot be dismissed by user
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * Update persistent notification to show latest alert info
     */
    private fun updatePersistentNotification(latestAlert: com.crabtrack.app.data.model.Alert) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "alerts")
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrabTrack Monitoring Active")
            .setContentText("Latest: ${latestAlert.parameter} - ${latestAlert.severity.name}")
            .setSmallIcon(R.drawable.ic_dashboard)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
