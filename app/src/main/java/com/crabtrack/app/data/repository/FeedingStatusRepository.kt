package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.ActionType
import com.crabtrack.app.data.model.FeedingCardStatus
import com.crabtrack.app.data.model.FeedingLog
import com.crabtrack.app.data.model.FeedingSchedule
import com.crabtrack.app.data.model.FeedingStatus
import com.crabtrack.app.data.model.FeedingTimeSlot
import com.crabtrack.app.data.model.RecurrenceType
import com.crabtrack.app.data.model.TankFeedingStatus
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing farm-wide feeding status using existing feeding_reminders.
 *
 * Firebase structure:
 * - Source (read): users/{uid}/feeding_reminders/{reminderId}/
 * - Logs (write): users/{uid}/feeding_logs/{yyyy-MM-dd}/{logId}/
 */
@Singleton
class FeedingStatusRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FeedingStatusRepo"
        private const val FEEDING_REMINDERS_PATH = "feeding_reminders"
        private const val FEEDING_LOGS_PATH = "feeding_logs"
        private const val DEFAULT_GRACE_PERIOD_MINUTES = 30
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Observes farm-wide feeding status based on existing feeding_reminders.
     * Returns a list with a single TankFeedingStatus for the whole farm.
     */
    val allTankFeedingStatuses: Flow<List<TankFeedingStatus>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in, emitting empty list")
            trySend(emptyList())
            return@callbackFlow
        }

        val remindersRef = firebaseDatabase.getReference("users/$uid/$FEEDING_REMINDERS_PATH")
        val today = dateFormat.format(Date())
        val logsRef = firebaseDatabase.getReference("users/$uid/$FEEDING_LOGS_PATH/$today")

        // Listen to both reminders and logs
        val combinedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // This will be called for either reminders or logs changes
                // We need to fetch both to build the status
                scope.launch {
                    try {
                        val remindersSnapshot = remindersRef.get().await()
                        val logsSnapshot = logsRef.get().await()

                        val status = buildFarmFeedingStatus(remindersSnapshot, logsSnapshot, today)

                        if (status != null) {
                            trySend(listOf(status))
                            Log.i(TAG, "Farm feeding status updated: ${status.todayLogs.size} logs")
                        } else {
                            trySend(emptyList())
                            Log.i(TAG, "No daily feeding reminders found")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building feeding status", e)
                        trySend(emptyList())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Feeding status listener cancelled: ${error.message}")
                scope.launch {
                    trySend(emptyList())
                }
            }
        }

        // Listen to reminders for schedule changes
        remindersRef.addValueEventListener(combinedListener)
        // Also listen to logs for status changes
        logsRef.addValueEventListener(combinedListener)

        awaitClose {
            remindersRef.removeEventListener(combinedListener)
            logsRef.removeEventListener(combinedListener)
            Log.i(TAG, "Feeding status listeners removed")
        }
    }

    /**
     * Builds a farm-wide feeding status from reminders and logs.
     */
    private suspend fun buildFarmFeedingStatus(
        remindersSnapshot: DataSnapshot,
        logsSnapshot: DataSnapshot,
        today: String
    ): TankFeedingStatus? {
        val now = System.currentTimeMillis()

        // Get daily feeding reminders (FEED + DAILY recurrence)
        val dailyFeedingReminders = mutableListOf<FeedingReminderData>()
        remindersSnapshot.children.forEach { reminderSnapshot ->
            val reminder = parseReminder(reminderSnapshot)
            if (reminder != null &&
                reminder.actionType == ActionType.FEED.name &&
                reminder.recurrence == RecurrenceType.DAILY.name) {
                dailyFeedingReminders.add(reminder)
            }
        }

        // If no daily feeding reminders, return null
        if (dailyFeedingReminders.isEmpty()) {
            return null
        }

        // Sort by time
        dailyFeedingReminders.sortBy { it.time }

        // Parse existing logs for today
        val existingLogs = mutableMapOf<String, FeedingLog>() // keyed by reminder_id
        logsSnapshot.children.forEach { logSnapshot ->
            val logId = logSnapshot.key ?: return@forEach
            val log = parseLog(logId, logSnapshot)
            if (log != null && log.reminderId.isNotEmpty()) {
                existingLogs[log.reminderId] = log
            }
        }

        // Build feeding logs list (create virtual logs for reminders without logs)
        val todayLogs = mutableListOf<FeedingLog>()
        val calendar = Calendar.getInstance()

        dailyFeedingReminders.forEach { reminder ->
            val existingLog = existingLogs[reminder.id]

            if (existingLog != null) {
                // Use existing log
                todayLogs.add(existingLog)
            } else {
                // Create a virtual pending log for display
                val timeParts = reminder.time.split(":")
                if (timeParts.size == 2) {
                    calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                    calendar.set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    val scheduledTimestamp = calendar.timeInMillis
                    val gracePeriodMs = DEFAULT_GRACE_PERIOD_MINUTES * 60 * 1000L

                    // Determine status based on time
                    val status = when {
                        now > scheduledTimestamp + gracePeriodMs -> FeedingStatus.OVERDUE
                        else -> FeedingStatus.PENDING
                    }

                    val label = deriveLabel(reminder.time)

                    todayLogs.add(
                        FeedingLog(
                            id = reminder.id, // Use reminder ID as virtual log ID
                            tankId = "farm",
                            scheduledTime = reminder.time,
                            scheduledTimestamp = scheduledTimestamp,
                            confirmedAt = null,
                            status = status,
                            date = today,
                            label = label,
                            reminderId = reminder.id
                        )
                    )
                }
            }
        }

        // Sort logs by scheduled time
        todayLogs.sortBy { it.scheduledTimestamp }

        // Compute overall status
        return TankFeedingStatus.compute("farm", null, todayLogs, now).copy(
            tankName = "Farm Feeding"
        )
    }

    /**
     * Derives a label (Morning/Afternoon/Evening) from time.
     */
    private fun deriveLabel(time: String): String {
        val hour = time.split(":").firstOrNull()?.toIntOrNull() ?: 12
        return when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
    }

    /**
     * Data class for parsed reminder.
     */
    private data class FeedingReminderData(
        val id: String,
        val time: String,
        val actionType: String,
        val recurrence: String
    )

    /**
     * Parses a feeding reminder from Firebase snapshot.
     */
    private fun parseReminder(snapshot: DataSnapshot): FeedingReminderData? {
        val id = snapshot.key ?: return null
        val time = snapshot.child("time").getValue(String::class.java) ?: return null
        val actionType = snapshot.child("actionType").getValue(String::class.java) ?: return null
        val recurrence = snapshot.child("recurrence").getValue(String::class.java) ?: "NONE"

        return FeedingReminderData(id, time, actionType, recurrence)
    }

    /**
     * Parses a feeding log from a Firebase snapshot.
     */
    private fun parseLog(logId: String, snapshot: DataSnapshot): FeedingLog? {
        return try {
            FeedingLog(
                id = logId,
                tankId = snapshot.child("tank_id").getValue(String::class.java) ?: "farm",
                scheduledTime = snapshot.child("scheduled_time").getValue(String::class.java) ?: "",
                scheduledTimestamp = snapshot.child("scheduled_timestamp").getValue(Long::class.java) ?: 0L,
                confirmedAt = snapshot.child("confirmed_at").getValue(Long::class.java),
                status = try {
                    FeedingStatus.valueOf(
                        snapshot.child("status").getValue(String::class.java) ?: "PENDING"
                    )
                } catch (e: Exception) {
                    FeedingStatus.PENDING
                },
                date = snapshot.child("date").getValue(String::class.java) ?: "",
                label = snapshot.child("label").getValue(String::class.java) ?: "",
                reminderId = snapshot.child("reminder_id").getValue(String::class.java) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing feeding log: $logId", e)
            null
        }
    }

    /**
     * Confirms a feeding (marks as complete).
     * Creates or updates the log in Firebase.
     */
    suspend fun confirmFeeding(tankId: String, feedingLogId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val today = dateFormat.format(Date())
            val logsRef = firebaseDatabase.getReference("users/$uid/$FEEDING_LOGS_PATH/$today")

            // Check if this is a virtual log (ID is the reminder ID) or existing log
            val existingLog = logsRef.child(feedingLogId).get().await()

            if (existingLog.exists()) {
                // Update existing log
                val updates = mapOf(
                    "confirmed_at" to System.currentTimeMillis(),
                    "status" to FeedingStatus.CONFIRMED.name
                )
                logsRef.child(feedingLogId).updateChildren(updates).await()
            } else {
                // This is a virtual log - need to get reminder info and create real log
                val reminderRef = firebaseDatabase.getReference("users/$uid/$FEEDING_REMINDERS_PATH/$feedingLogId")
                val reminderSnapshot = reminderRef.get().await()

                if (reminderSnapshot.exists()) {
                    val time = reminderSnapshot.child("time").getValue(String::class.java) ?: ""
                    val calendar = Calendar.getInstance()
                    val timeParts = time.split(":")
                    if (timeParts.size == 2) {
                        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                        calendar.set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                        calendar.set(Calendar.SECOND, 0)
                    }

                    // Create new log
                    val newLogId = logsRef.push().key ?: feedingLogId
                    val logData = mapOf(
                        "tank_id" to "farm",
                        "reminder_id" to feedingLogId,
                        "scheduled_time" to time,
                        "scheduled_timestamp" to calendar.timeInMillis,
                        "confirmed_at" to System.currentTimeMillis(),
                        "status" to FeedingStatus.CONFIRMED.name,
                        "date" to today,
                        "label" to deriveLabel(time)
                    )
                    logsRef.child(newLogId).setValue(logData).await()
                }
            }

            Log.i(TAG, "Confirmed feeding: $feedingLogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm feeding", e)
            Result.failure(e)
        }
    }

    /**
     * Skips a feeding.
     */
    suspend fun skipFeeding(tankId: String, feedingLogId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val today = dateFormat.format(Date())
            val logsRef = firebaseDatabase.getReference("users/$uid/$FEEDING_LOGS_PATH/$today")

            val existingLog = logsRef.child(feedingLogId).get().await()

            if (existingLog.exists()) {
                logsRef.child(feedingLogId).child("status").setValue(FeedingStatus.SKIPPED.name).await()
            } else {
                // Create new log with SKIPPED status
                val reminderRef = firebaseDatabase.getReference("users/$uid/$FEEDING_REMINDERS_PATH/$feedingLogId")
                val reminderSnapshot = reminderRef.get().await()

                if (reminderSnapshot.exists()) {
                    val time = reminderSnapshot.child("time").getValue(String::class.java) ?: ""
                    val calendar = Calendar.getInstance()
                    val timeParts = time.split(":")
                    if (timeParts.size == 2) {
                        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                        calendar.set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                        calendar.set(Calendar.SECOND, 0)
                    }

                    val newLogId = logsRef.push().key ?: feedingLogId
                    val logData = mapOf(
                        "tank_id" to "farm",
                        "reminder_id" to feedingLogId,
                        "scheduled_time" to time,
                        "scheduled_timestamp" to calendar.timeInMillis,
                        "confirmed_at" to null,
                        "status" to FeedingStatus.SKIPPED.name,
                        "date" to today,
                        "label" to deriveLabel(time)
                    )
                    logsRef.child(newLogId).setValue(logData).await()
                }
            }

            Log.i(TAG, "Skipped feeding: $feedingLogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to skip feeding", e)
            Result.failure(e)
        }
    }

    /**
     * Generates daily feeding logs for all daily reminders.
     * Called by the periodic worker.
     */
    suspend fun generateAllDailyFeedingLogs(): Result<Int> {
        // Not needed with the new approach - logs are created on-demand when confirmed
        // But we can use this to pre-create logs if desired
        return Result.success(0)
    }

    /**
     * Updates overdue status for pending feedings that have passed the grace period.
     * Called by periodic worker.
     */
    suspend fun updateOverdueStatuses(): Result<Int> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            var updatedCount = 0
            val today = dateFormat.format(Date())
            val now = System.currentTimeMillis()
            val gracePeriodMs = DEFAULT_GRACE_PERIOD_MINUTES * 60 * 1000L

            val logsRef = firebaseDatabase.getReference("users/$uid/$FEEDING_LOGS_PATH/$today")
            val logsSnapshot = logsRef.get().await()

            logsSnapshot.children.forEach { logSnapshot ->
                val status = logSnapshot.child("status").getValue(String::class.java)
                val scheduledTimestamp = logSnapshot.child("scheduled_timestamp").getValue(Long::class.java) ?: 0L

                if (status == FeedingStatus.PENDING.name && now > scheduledTimestamp + gracePeriodMs) {
                    logSnapshot.ref.child("status").setValue(FeedingStatus.OVERDUE.name).await()
                    updatedCount++
                }
            }

            Log.i(TAG, "Updated $updatedCount overdue feeding statuses")
            Result.success(updatedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overdue statuses", e)
            Result.failure(e)
        }
    }

    /**
     * Gets farm feeding status if overdue (for notifications).
     */
    suspend fun getOverdueTanks(): List<TankFeedingStatus> {
        val uid = getCurrentUserId() ?: return emptyList()

        return try {
            val today = dateFormat.format(Date())
            val remindersRef = firebaseDatabase.getReference("users/$uid/$FEEDING_REMINDERS_PATH")
            val logsRef = firebaseDatabase.getReference("users/$uid/$FEEDING_LOGS_PATH/$today")

            val remindersSnapshot = remindersRef.get().await()
            val logsSnapshot = logsRef.get().await()

            val status = buildFarmFeedingStatus(remindersSnapshot, logsSnapshot, today)

            if (status != null && status.overallStatus == FeedingCardStatus.OVERDUE) {
                listOf(status)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get overdue status", e)
            emptyList()
        }
    }

    // Keep these for compatibility but they won't be used with farm-wide approach
    suspend fun generateDailyFeedingLogs(tankId: String): Result<Unit> = Result.success(Unit)
    suspend fun saveFeedingSchedule(schedule: FeedingSchedule): Result<Unit> = Result.success(Unit)
    suspend fun getFeedingSchedule(tankId: String): Result<FeedingSchedule?> = Result.success(null)
    suspend fun deleteFeedingSchedule(tankId: String): Result<Unit> = Result.success(Unit)

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
