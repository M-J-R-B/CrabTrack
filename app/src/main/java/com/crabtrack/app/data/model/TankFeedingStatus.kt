package com.crabtrack.app.data.model

/**
 * Overall feeding status for a tank's card display.
 */
enum class FeedingCardStatus {
    ALL_FED,    // All scheduled feedings confirmed (green)
    PENDING,    // Next feeding approaching or within window (orange)
    OVERDUE     // One or more feedings overdue (red)
}

/**
 * Aggregated feeding status for a single tank for dashboard display.
 * This is a computed/derived model, not stored in Firebase directly.
 */
data class TankFeedingStatus(
    val tankId: String,
    val tankName: String,                     // e.g., "Tank 1"
    val overallStatus: FeedingCardStatus,     // Fed, Pending, Overdue
    val todayLogs: List<FeedingLog>,          // All feeding logs for today
    val nextScheduledTime: String?,           // Format: "HH:mm" or null if done for day
    val nextScheduledTimestamp: Long?,        // Epoch ms
    val overdueByMinutes: Int = 0,            // If overdue, how many minutes
    val schedule: FeedingSchedule?            // The tank's configured schedule
) {
    /**
     * Returns the next pending feeding log, if any.
     */
    fun getNextPendingLog(): FeedingLog? {
        return todayLogs.firstOrNull { it.status == FeedingStatus.PENDING }
    }

    /**
     * Returns all overdue feeding logs.
     */
    fun getOverdueLogs(): List<FeedingLog> {
        return todayLogs.filter { it.status == FeedingStatus.OVERDUE }
    }

    /**
     * Returns the count of confirmed feedings today.
     */
    fun getConfirmedCount(): Int {
        return todayLogs.count { it.status == FeedingStatus.CONFIRMED }
    }

    /**
     * Returns the total scheduled feedings for today.
     */
    fun getTotalScheduledCount(): Int {
        return todayLogs.size
    }

    /**
     * Returns a summary text for the card.
     * e.g., "1/2 fed" or "All fed" or "2 overdue"
     */
    fun getSummaryText(): String {
        val confirmed = getConfirmedCount()
        val total = getTotalScheduledCount()
        val overdue = getOverdueLogs().size

        return when {
            overdue > 0 -> "$overdue overdue"
            confirmed == total && total > 0 -> "All fed"
            total > 0 -> "$confirmed/$total fed"
            else -> "No schedule"
        }
    }

    /**
     * Converts to AlertSeverity for UI styling.
     */
    fun toSeverity(): AlertSeverity = when (overallStatus) {
        FeedingCardStatus.ALL_FED -> AlertSeverity.INFO
        FeedingCardStatus.PENDING -> AlertSeverity.WARNING
        FeedingCardStatus.OVERDUE -> AlertSeverity.CRITICAL
    }

    companion object {
        /**
         * Computes the feeding status for a tank from its schedule and logs.
         */
        fun compute(
            tankId: String,
            schedule: FeedingSchedule?,
            todayLogs: List<FeedingLog>,
            currentTimeMs: Long = System.currentTimeMillis()
        ): TankFeedingStatus {
            val tankNumber = tankId.replace("tank", "").trim()
            val tankName = "Tank $tankNumber"

            // Sort logs by scheduled time
            val sortedLogs = todayLogs.sortedBy { it.scheduledTimestamp }

            // Determine overall status
            val hasOverdue = sortedLogs.any { it.status == FeedingStatus.OVERDUE }
            val hasPending = sortedLogs.any { it.status == FeedingStatus.PENDING }
            val allConfirmedOrSkipped = sortedLogs.isNotEmpty() && sortedLogs.all {
                it.status == FeedingStatus.CONFIRMED || it.status == FeedingStatus.SKIPPED
            }

            val overallStatus = when {
                hasOverdue -> FeedingCardStatus.OVERDUE
                hasPending -> FeedingCardStatus.PENDING
                allConfirmedOrSkipped -> FeedingCardStatus.ALL_FED
                sortedLogs.isEmpty() -> FeedingCardStatus.PENDING // No schedule set up
                else -> FeedingCardStatus.PENDING
            }

            // Find next pending feeding
            val nextPending = sortedLogs.firstOrNull { it.status == FeedingStatus.PENDING }

            // Calculate overdue minutes for the earliest overdue feeding (time since scheduled)
            val overdueLog = sortedLogs.firstOrNull { it.status == FeedingStatus.OVERDUE }
            val overdueByMinutes = if (overdueLog != null) {
                ((currentTimeMs - overdueLog.scheduledTimestamp) / 60000).toInt().coerceAtLeast(0)
            } else 0

            return TankFeedingStatus(
                tankId = tankId,
                tankName = tankName,
                overallStatus = overallStatus,
                todayLogs = sortedLogs,
                nextScheduledTime = nextPending?.scheduledTime,
                nextScheduledTimestamp = nextPending?.scheduledTimestamp,
                overdueByMinutes = overdueByMinutes,
                schedule = schedule
            )
        }

        /**
         * Creates an empty status for a tank with no schedule.
         */
        fun empty(tankId: String): TankFeedingStatus {
            val tankNumber = tankId.replace("tank", "").trim()
            return TankFeedingStatus(
                tankId = tankId,
                tankName = "Tank $tankNumber",
                overallStatus = FeedingCardStatus.PENDING,
                todayLogs = emptyList(),
                nextScheduledTime = null,
                nextScheduledTimestamp = null,
                overdueByMinutes = 0,
                schedule = null
            )
        }
    }
}
