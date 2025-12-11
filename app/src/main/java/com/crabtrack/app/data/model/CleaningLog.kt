package com.crabtrack.app.data.model

/**
 * Status of a cleaning event.
 */
enum class CleaningStatus {
    PENDING,    // Cleaning time approaching or within window
    CONFIRMED,  // Farmer confirmed cleaning complete
    OVERDUE,    // Cleaning time passed without confirmation
    SKIPPED     // Farmer explicitly skipped this cleaning
}

/**
 * Represents a single cleaning event record.
 * Firebase path: users/{uid}/cleaning_logs/{date}/{logId}
 */
data class CleaningLog(
    val id: String = "",
    val tankId: String = "",
    val scheduledTime: String = "",     // Format: "HH:mm" - the scheduled cleaning time
    val scheduledTimestamp: Long = 0L,  // Full scheduled datetime as epoch ms
    val confirmedAt: Long? = null,      // When farmer confirmed cleaning (null if not confirmed)
    val status: CleaningStatus = CleaningStatus.PENDING,
    val date: String = "",              // Format: "yyyy-MM-dd"
    val label: String = "",             // e.g., "Cleaning"
    val reminderId: String = ""         // Link to original cleaning_reminder
) {
    /**
     * Returns true if this cleaning has been confirmed.
     */
    fun isConfirmed(): Boolean = status == CleaningStatus.CONFIRMED

    /**
     * Returns true if this cleaning is overdue.
     */
    fun isOverdue(): Boolean = status == CleaningStatus.OVERDUE

    /**
     * Returns true if this cleaning is still pending.
     */
    fun isPending(): Boolean = status == CleaningStatus.PENDING

    /**
     * Converts to AlertSeverity for UI styling.
     */
    fun toSeverity(): AlertSeverity = when (status) {
        CleaningStatus.CONFIRMED -> AlertSeverity.INFO
        CleaningStatus.PENDING -> AlertSeverity.WARNING
        CleaningStatus.OVERDUE -> AlertSeverity.CRITICAL
        CleaningStatus.SKIPPED -> AlertSeverity.INFO
    }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "tank_id" to tankId,
        "reminder_id" to reminderId,
        "scheduled_time" to scheduledTime,
        "scheduled_timestamp" to scheduledTimestamp,
        "confirmed_at" to confirmedAt,
        "status" to status.name,
        "date" to date,
        "label" to label
    )

    companion object {
        fun fromFirebaseMap(id: String, map: Map<String, Any?>): CleaningLog {
            return CleaningLog(
                id = id,
                tankId = map["tank_id"] as? String ?: "",
                scheduledTime = map["scheduled_time"] as? String ?: "",
                scheduledTimestamp = (map["scheduled_timestamp"] as? Number)?.toLong() ?: 0L,
                confirmedAt = (map["confirmed_at"] as? Number)?.toLong(),
                status = try {
                    CleaningStatus.valueOf(map["status"] as? String ?: "PENDING")
                } catch (e: Exception) {
                    CleaningStatus.PENDING
                },
                date = map["date"] as? String ?: "",
                label = map["label"] as? String ?: "",
                reminderId = map["reminder_id"] as? String ?: ""
            )
        }
    }
}
