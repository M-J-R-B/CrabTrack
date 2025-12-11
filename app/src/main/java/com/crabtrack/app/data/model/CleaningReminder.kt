package com.crabtrack.app.data.model

data class CleaningReminder(
    val id: String = "",
    val time: String = "",              // Format: HH:mm
    val recurrence: RecurrenceType = RecurrenceType.DAILY,
    val createdAt: Long = 0L,
    val actionType: String = ActionType.CLEAN.name
) {
    fun getRecurrenceDisplayText(): String {
        return when (recurrence) {
            RecurrenceType.ONE_TIME -> "One-time"
            RecurrenceType.DAILY -> "Daily"
            RecurrenceType.WEEKLY -> "Weekly"
        }
    }

    companion object {
        fun create(
            id: String,
            time: String,
            recurrence: RecurrenceType = RecurrenceType.DAILY
        ): CleaningReminder {
            return CleaningReminder(
                id = id,
                time = time,
                recurrence = recurrence,
                createdAt = System.currentTimeMillis(),
                actionType = ActionType.CLEAN.name
            )
        }
    }
}
