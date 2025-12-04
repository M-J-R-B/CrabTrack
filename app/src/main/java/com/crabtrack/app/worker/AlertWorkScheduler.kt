package com.crabtrack.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlertWorkScheduler"
        private const val CHECK_INTERVAL_MINUTES = 15L
    }

    /**
     * Schedule periodic alert monitoring work.
     * This will check water quality thresholds every 15 minutes (minimum allowed by Android).
     */
    fun scheduleAlertMonitoring() {
        android.util.Log.i(TAG, "Scheduling periodic alert monitoring (every $CHECK_INTERVAL_MINUTES minutes)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // Need internet for Firebase
            .setRequiresBatteryNotLow(false)  // Run even on low battery (water quality is critical!)
            .build()

        val alertWorkRequest = PeriodicWorkRequestBuilder<AlertWorker>(
            CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(AlertWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already running
            alertWorkRequest
        )

        android.util.Log.i(TAG, "Alert monitoring work scheduled successfully")
    }

    /**
     * Cancel alert monitoring work.
     * Call this on logout to stop background checks.
     */
    fun cancelAlertMonitoring() {
        android.util.Log.i(TAG, "Cancelling alert monitoring work")
        WorkManager.getInstance(context).cancelUniqueWork(AlertWorker.WORK_NAME)
    }

    /**
     * Check if alert monitoring is currently scheduled.
     */
    fun isAlertMonitoringScheduled(): Boolean {
        // This is async, but we can check work info
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(AlertWorker.WORK_NAME)
                .get()
            workInfos.any { !it.state.isFinished }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking work status", e)
            false
        }
    }
}
