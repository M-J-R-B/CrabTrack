package com.crabtrack.app.data.model

enum class RecurrenceType {
    NONE,    // One-time reminder
    DAILY,   // Repeats every day
    WEEKLY   // Repeats every week
}

data class FeedingReminder(
    val id: String = "",
    val date: String = "",        // Format: yyyy-MM-dd
    val time: String = "",        // Format: HH:mm
    val timestamp: Long = 0L,     // Unix timestamp in milliseconds (UTC)
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val status: String = "scheduled",  // scheduled, completed, cancelled
    val createdAt: Long = 0L,  // Don't use System.currentTimeMillis() in data class
    val actionType: String = ActionType.FEED.name
) {
    // Helper function to get display text for recurrence
    fun getRecurrenceDisplayText(): String {
        return when (recurrence) {
            RecurrenceType.NONE -> "One-time"
            RecurrenceType.DAILY -> "Daily"
            RecurrenceType.WEEKLY -> "Weekly"
        }
    }

    // Helper function to check if reminder is active
    // Note: Both timestamp and System.currentTimeMillis() are in UTC epoch ms
    fun isActive(): Boolean {
        return status == "scheduled" && timestamp > System.currentTimeMillis()
    }

    companion object {
        /**
         * Creates a new FeedingReminder with current timestamp.
         * Use this factory method instead of default values.
         */
        fun create(
            id: String,
            date: String,
            time: String,
            timestamp: Long,
            recurrence: RecurrenceType = RecurrenceType.NONE,
            actionType: String = ActionType.FEED.name
        ): FeedingReminder {
            return FeedingReminder(
                id = id,
                date = date,
                time = time,
                timestamp = timestamp,
                recurrence = recurrence,
                status = "scheduled",
                createdAt = System.currentTimeMillis(), // Set createdAt when creating
                actionType = actionType
            )
        }
    }
}
