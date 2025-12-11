package com.crabtrack.app.data.model

/**
 * Represents a single feeding time slot within a schedule.
 */
data class FeedingTimeSlot(
    val id: String = "",
    val time: String = "",  // Format: "HH:mm" (24-hour)
    val label: String = ""  // e.g., "Morning", "Evening"
)

/**
 * Represents a per-tank feeding schedule configured by the farmer.
 * Firebase path: users/{uid}/tanks/{tankId}/feeding_schedule
 */
data class FeedingSchedule(
    val tankId: String = "",
    val enabled: Boolean = true,
    val feedingTimes: List<FeedingTimeSlot> = emptyList(),
    val gracePeriodMinutes: Int = DEFAULT_GRACE_PERIOD,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    companion object {
        const val DEFAULT_GRACE_PERIOD = 30 // minutes before marking overdue

        /**
         * Creates a default feeding schedule with morning and evening feedings.
         */
        fun createDefault(tankId: String): FeedingSchedule {
            return FeedingSchedule(
                tankId = tankId,
                enabled = true,
                feedingTimes = listOf(
                    FeedingTimeSlot(id = "morning", time = "07:00", label = "Morning"),
                    FeedingTimeSlot(id = "evening", time = "18:00", label = "Evening")
                ),
                gracePeriodMinutes = DEFAULT_GRACE_PERIOD,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "tank_id" to tankId,
        "enabled" to enabled,
        "feeding_times" to feedingTimes.map { slot ->
            mapOf(
                "id" to slot.id,
                "time" to slot.time,
                "label" to slot.label
            )
        },
        "grace_period_minutes" to gracePeriodMinutes,
        "created_at" to createdAt,
        "updated_at" to updatedAt
    )
}
