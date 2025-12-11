package com.crabtrack.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crabtrack.app.data.model.FeedingCardStatus
import com.crabtrack.app.data.model.FeedingStatus
import com.crabtrack.app.data.repository.FeedingStatusRepository
import com.crabtrack.app.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Background worker that periodically checks feeding status.
 * - Generates daily feeding logs based on schedules
 * - Updates pending feedings to overdue after grace period
 * - Sends notifications for overdue feedings
 */
@HiltWorker
class FeedingCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val feedingStatusRepository: FeedingStatusRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "feeding_check_work"
        const val TAG = "FeedingCheckWorker"
    }

    private val timeFormat12 = SimpleDateFormat("h:mm a", Locale.US)
    private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.US)

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "FeedingCheckWorker starting...")

        return try {
            // Check if user is authenticated
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                android.util.Log.w(TAG, "No user logged in, skipping feeding check")
                return Result.success()
            }

            // Step 1: Generate daily feeding logs for all tanks (idempotent)
            android.util.Log.d(TAG, "Generating daily feeding logs...")
            feedingStatusRepository.generateAllDailyFeedingLogs()

            // Step 2: Update overdue statuses
            android.util.Log.d(TAG, "Updating overdue statuses...")
            val updateResult = feedingStatusRepository.updateOverdueStatuses()
            updateResult.onSuccess { count ->
                android.util.Log.i(TAG, "Updated $count feedings to overdue status")
            }

            // Step 3: Send notifications for overdue tanks
            android.util.Log.d(TAG, "Checking for overdue tanks...")
            val overdueTanks = feedingStatusRepository.getOverdueTanks()

            overdueTanks.forEach { status ->
                if (status.overallStatus == FeedingCardStatus.OVERDUE) {
                    // Find the first overdue feeding log
                    val overdueLog = status.todayLogs.firstOrNull {
                        it.status == FeedingStatus.OVERDUE
                    }

                    overdueLog?.let { log ->
                        val formattedTime = formatTime(log.scheduledTime)
                        notificationHelper.showFeedingAlert(
                            tankName = status.tankName,
                            isOverdue = true,
                            scheduledTime = formattedTime,
                            overdueMinutes = status.overdueByMinutes
                        )
                        android.util.Log.i(TAG, "Sent overdue notification for ${status.tankName}")
                    }
                }
            }

            android.util.Log.d(TAG, "FeedingCheckWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "FeedingCheckWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Converts 24-hour time format to 12-hour format with AM/PM.
     */
    private fun formatTime(time24: String): String {
        return try {
            val date = timeFormat24.parse(time24) ?: return time24
            timeFormat12.format(date)
        } catch (e: Exception) {
            time24
        }
    }
}
