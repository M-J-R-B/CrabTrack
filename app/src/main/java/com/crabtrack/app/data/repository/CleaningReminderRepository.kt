package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.ActionType
import com.crabtrack.app.data.model.CleaningCardStatus
import com.crabtrack.app.data.model.CleaningLog
import com.crabtrack.app.data.model.CleaningStatus
import com.crabtrack.app.data.model.FarmCleaningStatus
import com.crabtrack.app.data.model.RecurrenceType
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
 * Repository for managing cleaning reminders and logs from Firebase.
 *
 * Firebase structure:
 * - Reminders: users/{uid}/cleaning_reminders/{reminderId}/
 *   - actionType: "CLEAN"
 *   - createdAt: Long
 *   - recurrence: "DAILY"
 *   - time: "HH:mm"
 *
 * - Logs: users/{uid}/cleaning_logs/{yyyy-MM-dd}/{logId}/
 *   - confirmed_at: Long?
 *   - date: String
 *   - label: String
 *   - reminder_id: String
 *   - scheduled_time: String
 *   - scheduled_timestamp: Long
 *   - status: "PENDING" | "CONFIRMED" | "OVERDUE" | "SKIPPED"
 *   - tank_id: String
 */
@Singleton
class CleaningReminderRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CleaningReminderRepo"
        private const val CLEANING_REMINDERS_PATH = "cleaning_reminders"
        private const val CLEANING_LOGS_PATH = "cleaning_logs"
        private const val DEFAULT_GRACE_PERIOD_MINUTES = 30
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Observes farm-wide cleaning status based on cleaning_reminders and cleaning_logs.
     */
    val farmCleaningStatus: Flow<FarmCleaningStatus?> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in, emitting null")
            trySend(null)
            return@callbackFlow
        }

        val remindersRef = firebaseDatabase.getReference("users/$uid/$CLEANING_REMINDERS_PATH")
        val today = dateFormat.format(Date())
        val logsRef = firebaseDatabase.getReference("users/$uid/$CLEANING_LOGS_PATH/$today")

        // Listen to both reminders and logs
        val combinedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val remindersSnapshot = remindersRef.get().await()
                        val logsSnapshot = logsRef.get().await()

                        val status = buildCleaningStatus(remindersSnapshot, logsSnapshot, today)
                        trySend(status)
                        Log.i(TAG, "Cleaning status updated: ${status?.todayLogs?.size ?: 0} logs")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building cleaning status", e)
                        trySend(null)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Cleaning status listener cancelled: ${error.message}")
                scope.launch {
                    trySend(null)
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
            Log.i(TAG, "Cleaning status listeners removed")
        }
    }

    /**
     * Gets the current cleaning status for notification worker.
     */
    suspend fun getOverdueCleaningStatus(): FarmCleaningStatus? {
        val uid = getCurrentUserId() ?: return null

        return try {
            val today = dateFormat.format(Date())
            val remindersSnapshot = firebaseDatabase
                .getReference("users/$uid/$CLEANING_REMINDERS_PATH")
                .get()
                .await()
            val logsSnapshot = firebaseDatabase
                .getReference("users/$uid/$CLEANING_LOGS_PATH/$today")
                .get()
                .await()

            buildCleaningStatus(remindersSnapshot, logsSnapshot, today)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cleaning status", e)
            null
        }
    }

    /**
     * Builds cleaning status from reminders and logs snapshots.
     */
    private fun buildCleaningStatus(
        remindersSnapshot: DataSnapshot,
        logsSnapshot: DataSnapshot,
        today: String
    ): FarmCleaningStatus? {
        val now = System.currentTimeMillis()

        // Get daily cleaning reminders (CLEAN + DAILY recurrence)
        val dailyCleaningReminders = mutableListOf<CleaningReminderData>()
        remindersSnapshot.children.forEach { reminderSnapshot ->
            val reminder = parseReminder(reminderSnapshot)
            if (reminder != null &&
                reminder.actionType == ActionType.CLEAN.name &&
                reminder.recurrence == RecurrenceType.DAILY.name) {
                dailyCleaningReminders.add(reminder)
            }
        }

        // If no daily cleaning reminders, return null
        if (dailyCleaningReminders.isEmpty()) {
            return null
        }

        // Sort by time
        dailyCleaningReminders.sortBy { it.time }

        // Parse existing logs for today
        val existingLogs = mutableMapOf<String, CleaningLog>() // keyed by reminder_id
        logsSnapshot.children.forEach { logSnapshot ->
            val logId = logSnapshot.key ?: return@forEach
            val log = parseLog(logId, logSnapshot)
            if (log != null && log.reminderId.isNotEmpty()) {
                existingLogs[log.reminderId] = log
            }
        }

        // Build cleaning logs list (create virtual logs for reminders without logs)
        val todayLogs = mutableListOf<CleaningLog>()
        val calendar = Calendar.getInstance()

        dailyCleaningReminders.forEach { reminder ->
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
                        now > scheduledTimestamp + gracePeriodMs -> CleaningStatus.OVERDUE
                        else -> CleaningStatus.PENDING
                    }

                    todayLogs.add(
                        CleaningLog(
                            id = reminder.id, // Use reminder ID as virtual log ID
                            tankId = "farm",
                            scheduledTime = reminder.time,
                            scheduledTimestamp = scheduledTimestamp,
                            confirmedAt = null,
                            status = status,
                            date = today,
                            label = "Cleaning",
                            reminderId = reminder.id
                        )
                    )
                }
            }
        }

        // Sort logs by scheduled time
        todayLogs.sortBy { it.scheduledTimestamp }

        return FarmCleaningStatus.compute(todayLogs, now)
    }

    /**
     * Confirms a cleaning (marks as complete).
     * Creates or updates the log in Firebase.
     */
    suspend fun confirmCleaning(cleaningLogId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val today = dateFormat.format(Date())
            val logsRef = firebaseDatabase.getReference("users/$uid/$CLEANING_LOGS_PATH/$today")

            // Check if this is a virtual log (ID is the reminder ID) or existing log
            val existingLog = logsRef.child(cleaningLogId).get().await()

            if (existingLog.exists()) {
                // Update existing log
                val updates = mapOf(
                    "confirmed_at" to System.currentTimeMillis(),
                    "status" to CleaningStatus.CONFIRMED.name
                )
                logsRef.child(cleaningLogId).updateChildren(updates).await()
            } else {
                // This is a virtual log - need to get reminder info and create real log
                val reminderRef = firebaseDatabase.getReference("users/$uid/$CLEANING_REMINDERS_PATH/$cleaningLogId")
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
                    val newLogId = logsRef.push().key ?: cleaningLogId
                    val logData = mapOf(
                        "tank_id" to "farm",
                        "reminder_id" to cleaningLogId,
                        "scheduled_time" to time,
                        "scheduled_timestamp" to calendar.timeInMillis,
                        "confirmed_at" to System.currentTimeMillis(),
                        "status" to CleaningStatus.CONFIRMED.name,
                        "date" to today,
                        "label" to "Cleaning"
                    )
                    logsRef.child(newLogId).setValue(logData).await()
                }
            }

            Log.i(TAG, "Confirmed cleaning: $cleaningLogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to confirm cleaning", e)
            Result.failure(e)
        }
    }

    /**
     * Skips a cleaning.
     */
    suspend fun skipCleaning(cleaningLogId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val today = dateFormat.format(Date())
            val logsRef = firebaseDatabase.getReference("users/$uid/$CLEANING_LOGS_PATH/$today")

            val existingLog = logsRef.child(cleaningLogId).get().await()

            if (existingLog.exists()) {
                logsRef.child(cleaningLogId).child("status").setValue(CleaningStatus.SKIPPED.name).await()
            } else {
                // Create new log with SKIPPED status
                val reminderRef = firebaseDatabase.getReference("users/$uid/$CLEANING_REMINDERS_PATH/$cleaningLogId")
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

                    val newLogId = logsRef.push().key ?: cleaningLogId
                    val logData = mapOf(
                        "tank_id" to "farm",
                        "reminder_id" to cleaningLogId,
                        "scheduled_time" to time,
                        "scheduled_timestamp" to calendar.timeInMillis,
                        "confirmed_at" to null,
                        "status" to CleaningStatus.SKIPPED.name,
                        "date" to today,
                        "label" to "Cleaning"
                    )
                    logsRef.child(newLogId).setValue(logData).await()
                }
            }

            Log.i(TAG, "Skipped cleaning: $cleaningLogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to skip cleaning", e)
            Result.failure(e)
        }
    }

    /**
     * Updates overdue status for pending cleanings that have passed the grace period.
     * Called by periodic worker.
     */
    suspend fun updateOverdueStatuses(): Result<Int> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            var updatedCount = 0
            val today = dateFormat.format(Date())
            val now = System.currentTimeMillis()
            val gracePeriodMs = DEFAULT_GRACE_PERIOD_MINUTES * 60 * 1000L

            val logsRef = firebaseDatabase.getReference("users/$uid/$CLEANING_LOGS_PATH/$today")
            val logsSnapshot = logsRef.get().await()

            logsSnapshot.children.forEach { logSnapshot ->
                val status = logSnapshot.child("status").getValue(String::class.java)
                val scheduledTimestamp = logSnapshot.child("scheduled_timestamp").getValue(Long::class.java) ?: 0L

                if (status == CleaningStatus.PENDING.name && now > scheduledTimestamp + gracePeriodMs) {
                    logSnapshot.ref.child("status").setValue(CleaningStatus.OVERDUE.name).await()
                    updatedCount++
                }
            }

            Log.i(TAG, "Updated $updatedCount overdue cleaning statuses")
            Result.success(updatedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overdue statuses", e)
            Result.failure(e)
        }
    }

    /**
     * Data class for parsed reminder.
     */
    private data class CleaningReminderData(
        val id: String,
        val time: String,
        val actionType: String,
        val recurrence: String
    )

    /**
     * Parses a cleaning reminder from Firebase snapshot.
     */
    private fun parseReminder(snapshot: DataSnapshot): CleaningReminderData? {
        val id = snapshot.key ?: return null
        val time = snapshot.child("time").getValue(String::class.java) ?: return null
        val actionType = snapshot.child("actionType").getValue(String::class.java) ?: ActionType.CLEAN.name
        val recurrence = snapshot.child("recurrence").getValue(String::class.java) ?: "NONE"

        return CleaningReminderData(id, time, actionType, recurrence)
    }

    /**
     * Parses a cleaning log from a Firebase snapshot.
     */
    private fun parseLog(logId: String, snapshot: DataSnapshot): CleaningLog? {
        return try {
            CleaningLog(
                id = logId,
                tankId = snapshot.child("tank_id").getValue(String::class.java) ?: "farm",
                scheduledTime = snapshot.child("scheduled_time").getValue(String::class.java) ?: "",
                scheduledTimestamp = snapshot.child("scheduled_timestamp").getValue(Long::class.java) ?: 0L,
                confirmedAt = snapshot.child("confirmed_at").getValue(Long::class.java),
                status = try {
                    CleaningStatus.valueOf(
                        snapshot.child("status").getValue(String::class.java) ?: "PENDING"
                    )
                } catch (e: Exception) {
                    CleaningStatus.PENDING
                },
                date = snapshot.child("date").getValue(String::class.java) ?: "",
                label = snapshot.child("label").getValue(String::class.java) ?: "",
                reminderId = snapshot.child("reminder_id").getValue(String::class.java) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cleaning log: $logId", e)
            null
        }
    }

    private fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }
}
