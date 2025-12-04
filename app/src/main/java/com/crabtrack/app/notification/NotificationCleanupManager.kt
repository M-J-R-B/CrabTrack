package com.crabtrack.app.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.crabtrack.app.ui.settings.FeedingAlarmReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for notification lifecycle and cleanup.
 *
 * Responsibilities:
 * - Start/stop alert and molting notifiers
 * - Cancel scheduled feeding reminder alarms
 * - Clear all notifications from system tray
 *
 * Used by:
 * - AuthRepository: cleanup on logout
 * - MainActivity: start/stop notifiers based on auth state
 */
@Singleton
class NotificationCleanupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertsNotifier: AlertsNotifier,
    private val moltingNotifier: MoltingNotifier
) {
    companion object {
        private const val TAG = "NotificationCleanup"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Comprehensive cleanup on logout:
     * 1. Stop all notifiers
     * 2. Clear all notifications
     * 3. Cancel all feeding reminder alarms
     * 4. Stop background alert monitoring service
     */
    suspend fun cleanupAllNotifications() {
        Log.i(TAG, "Starting comprehensive notification cleanup")

        // 1. Stop notifiers
        stopAllNotifiers()

        // 2. Clear all visible notifications
        clearAllNotifications()

        // 3. Cancel all scheduled alarms
        cancelAllFeedingAlarms()

        // 4. Stop foreground monitoring service
        com.crabtrack.app.service.AlertMonitoringService.stop(context)

        Log.i(TAG, "Notification cleanup completed")
    }

    /**
     * Stop alert and molting notifiers
     */
    fun stopAllNotifiers() {
        Log.d(TAG, "Stopping all notifiers")
        alertsNotifier.stopCollection()
        moltingNotifier.stopCollection()
    }

    /**
     * Start notifiers (only when authenticated)
     * Also starts foreground service for 24/7 background monitoring
     */
    fun startAllNotifiers(scope: CoroutineScope) {
        Log.d(TAG, "Starting all notifiers")
        alertsNotifier.startCollection(scope)
        moltingNotifier.startCollection(scope)

        // Start foreground service for background monitoring
        com.crabtrack.app.service.AlertMonitoringService.start(context)
    }

    /**
     * Clear all notifications from system tray
     */
    private fun clearAllNotifications() {
        Log.d(TAG, "Clearing all notifications")
        notificationManager.cancelAll()
        alertsNotifier.clearAllAlerts()
        moltingNotifier.clearAllMoltingNotifications()
    }

    /**
     * Cancel all feeding reminder alarms for current user
     */
    private suspend fun cancelAllFeedingAlarms() {
        Log.d(TAG, "Cancelling all feeding reminder alarms")

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "No user logged in, skipping alarm cancellation")
            return
        }

        try {
            // Fetch all reminders from Firebase
            val remindersSnapshot = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.uid)
                .child("feeding_reminders")
                .get()
                .await()

            val cancelledCount = remindersSnapshot.children.count()

            // Cancel alarm for each reminder
            remindersSnapshot.children.forEach { snapshot ->
                val reminderId = snapshot.key ?: return@forEach
                cancelAlarm(reminderId)
            }

            Log.i(TAG, "Cancelled $cancelledCount feeding reminder alarms")

        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling feeding alarms: ${e.message}", e)
        }
    }

    /**
     * Cancel a specific alarm by reminder ID
     */
    private fun cancelAlarm(reminderId: String) {
        val intent = Intent(context, FeedingAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
