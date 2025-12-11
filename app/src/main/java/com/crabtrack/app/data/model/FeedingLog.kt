package com.crabtrack.app.data.model

/**
 * Status of a feeding event.
 */
enum class FeedingStatus {
    PENDING,    // Feeding time approaching or within window
    CONFIRMED,  // Farmer confirmed feeding complete
    OVERDUE,    // Feeding time passed without confirmation
    SKIPPED     // Farmer explicitly skipped this feeding
}

/**
 * Represents a single feeding event record for a tank.
 * Firebase path: users/{uid}/tanks/{tankId}/feeding_logs/{date}/{logId}
 */
data class FeedingLog(
    val id: String = "",
    val tankId: String = "",
    val scheduledTime: String = "",     // Format: "HH:mm" - the scheduled feeding time
    val scheduledTimestamp: Long = 0L,  // Full scheduled datetime as epoch ms
    val confirmedAt: Long? = null,      // When farmer confirmed feeding (null if not confirmed)
    val status: FeedingStatus = FeedingStatus.PENDING,
    val date: String = "",              // Format: "yyyy-MM-dd"
    val label: String = "",             // e.g., "Morning", "Evening"
    val notes: String = "",             // Optional notes
    val reminderId: String = ""         // Link to original feeding_reminder
) {
    /**
     * Returns true if this feeding has been confirmed.
     */
    fun isConfirmed(): Boolean = status == FeedingStatus.CONFIRMED

    /**
     * Returns true if this feeding is overdue.
     */
    fun isOverdue(): Boolean = status == FeedingStatus.OVERDUE

    /**
     * Returns true if this feeding is still pending.
     */
    fun isPending(): Boolean = status == FeedingStatus.PENDING

    /**
     * Converts to AlertSeverity for UI styling.
     */
    fun toSeverity(): AlertSeverity = when (status) {
        FeedingStatus.CONFIRMED -> AlertSeverity.INFO
        FeedingStatus.PENDING -> AlertSeverity.WARNING
        FeedingStatus.OVERDUE -> AlertSeverity.CRITICAL
        FeedingStatus.SKIPPED -> AlertSeverity.INFO
    }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "tank_id" to tankId,
        "reminder_id" to reminderId,
        "scheduled_time" to scheduledTime,
        "scheduled_timestamp" to scheduledTimestamp,
        "confirmed_at" to confirmedAt,
        "status" to status.name,
        "date" to date,
        "label" to label,
        "notes" to notes
    )

    companion object {
        fun fromFirebaseMap(id: String, map: Map<String, Any?>): FeedingLog {
            return FeedingLog(
                id = id,
                tankId = map["tank_id"] as? String ?: "",
                scheduledTime = map["scheduled_time"] as? String ?: "",
                scheduledTimestamp = (map["scheduled_timestamp"] as? Number)?.toLong() ?: 0L,
                confirmedAt = (map["confirmed_at"] as? Number)?.toLong(),
                status = try {
                    FeedingStatus.valueOf(map["status"] as? String ?: "PENDING")
                } catch (e: Exception) {
                    FeedingStatus.PENDING
                },
                date = map["date"] as? String ?: "",
                label = map["label"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
                reminderId = map["reminder_id"] as? String ?: ""
            )
        }
    }
}
