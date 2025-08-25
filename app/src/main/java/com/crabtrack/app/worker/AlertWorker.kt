package com.crabtrack.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.domain.usecase.CheckAlertsUseCase
import com.crabtrack.app.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkAlertsUseCase: CheckAlertsUseCase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "alert_monitoring_work"
        const val TAG = "AlertWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            // Get current alerts
            val alerts = checkAlertsUseCase().first()
            
            // Show notifications for active alerts
            alerts.forEach { alert ->
                when (alert.alertLevel) {
                    AlertLevel.WARNING, AlertLevel.CRITICAL -> {
                        notificationHelper.showSensorAlert(alert)
                    }
                    AlertLevel.NORMAL -> {
                        // Clear any existing notifications for this sensor
                        notificationHelper.clearSensorAlert(alert.sensorType)
                    }
                }
            }
            
            Result.success()
        } catch (exception: Exception) {
            // Log the error in a production app
            Result.retry()
        }
    }
}