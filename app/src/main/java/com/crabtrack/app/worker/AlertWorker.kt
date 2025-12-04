package com.crabtrack.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val telemetryRepository: TelemetryRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "alert_monitoring_work"
        const val TAG = "AlertWorker"
    }

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "AlertWorker starting background check...")

        return try {
            // Check if user is authenticated
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                android.util.Log.w(TAG, "No user logged in, skipping alert check")
                return Result.success()
            }

            // Get current alerts with timeout to prevent hanging
            val alerts = withTimeoutOrNull(30_000L) { // 30 second timeout
                telemetryRepository.allAlerts.firstOrNull()
            }

            if (alerts == null) {
                android.util.Log.w(TAG, "Alert check timed out or returned null")
                return Result.retry()
            }

            android.util.Log.d(TAG, "Received ${alerts.size} alerts")

            // Show notifications for critical and warning alerts
            alerts.forEach { alert ->
                when (alert.severity) {
                    AlertSeverity.CRITICAL, AlertSeverity.WARNING -> {
                        android.util.Log.i(TAG, "Showing alert: ${alert.parameter} - ${alert.message}")
                        notificationHelper.showWaterQualityAlert(alert)
                    }
                    AlertSeverity.INFO -> {
                        // Don't show notifications for info alerts
                        android.util.Log.d(TAG, "Skipping INFO alert: ${alert.parameter}")
                    }
                }
            }

            android.util.Log.d(TAG, "AlertWorker completed successfully")
            Result.success()
        } catch (exception: Exception) {
            android.util.Log.e(TAG, "AlertWorker failed: ${exception.message}", exception)
            Result.retry()
        }
    }
}