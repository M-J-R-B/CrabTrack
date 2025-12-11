package com.crabtrack.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crabtrack.app.data.model.CleaningCardStatus
import com.crabtrack.app.data.repository.CleaningReminderRepository
import com.crabtrack.app.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Background worker that periodically checks cleaning status.
 * - Checks for overdue cleaning reminders
 * - Sends notifications for overdue cleaning
 */
@HiltWorker
class CleaningCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cleaningReminderRepository: CleaningReminderRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "cleaning_check_work"
        const val TAG = "CleaningCheckWorker"
    }

    private val timeFormat12 = SimpleDateFormat("h:mm a", Locale.US)
    private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.US)

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "CleaningCheckWorker starting...")

        return try {
            // Check if user is authenticated
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                android.util.Log.w(TAG, "No user logged in, skipping cleaning check")
                return Result.success()
            }

            // Get cleaning status
            android.util.Log.d(TAG, "Checking cleaning status...")
            val status = cleaningReminderRepository.getOverdueCleaningStatus()

            if (status != null && status.overallStatus == CleaningCardStatus.OVERDUE) {
                val formattedTime = status.nextScheduledTime?.let { formatTime(it) } ?: "scheduled time"
                notificationHelper.showCleaningAlert(
                    isOverdue = true,
                    scheduledTime = formattedTime,
                    overdueMinutes = status.overdueByMinutes
                )
                android.util.Log.i(TAG, "Sent overdue cleaning notification")
            } else if (status != null && status.overallStatus == CleaningCardStatus.PENDING) {
                // Optional: notify when cleaning is due (not overdue yet)
                status.nextScheduledTime?.let { time ->
                    val formattedTime = formatTime(time)
                    notificationHelper.showCleaningAlert(
                        isOverdue = false,
                        scheduledTime = formattedTime,
                        overdueMinutes = 0
                    )
                    android.util.Log.i(TAG, "Sent pending cleaning notification")
                }
            }

            android.util.Log.d(TAG, "CleaningCheckWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "CleaningCheckWorker failed: ${e.message}", e)
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
