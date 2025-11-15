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
    val timestamp: Long = 0L,     // Unix timestamp in milliseconds
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val status: String = "scheduled",  // scheduled, completed, cancelled
    val createdAt: Long = System.currentTimeMillis()
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
    fun isActive(): Boolean {
        return status == "scheduled" && timestamp > System.currentTimeMillis()
    }
}
