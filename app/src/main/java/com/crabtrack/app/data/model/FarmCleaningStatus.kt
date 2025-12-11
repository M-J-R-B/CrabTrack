package com.crabtrack.app.data.model

/**
 * Overall cleaning status for the farm's card display.
 */
enum class CleaningCardStatus {
    ALL_CLEAN,  // All scheduled cleanings confirmed (green)
    PENDING,    // Next cleaning approaching or within window (orange)
    OVERDUE     // One or more cleanings overdue (red)
}

/**
 * Aggregated cleaning status for the farm for dashboard display.
 * This is a computed/derived model, not stored in Firebase directly.
 */
data class FarmCleaningStatus(
    val overallStatus: CleaningCardStatus,
    val todayLogs: List<CleaningLog>,         // All cleaning logs for today
    val nextScheduledTime: String?,           // Format: "HH:mm" or null if done for day
    val nextScheduledTimestamp: Long?,        // Epoch ms
    val overdueByMinutes: Int = 0             // If overdue, how many minutes
) {
    /**
     * Returns the next pending cleaning log, if any.
     */
    fun getNextPendingLog(): CleaningLog? {
        return todayLogs.firstOrNull { it.status == CleaningStatus.PENDING }
    }

    /**
     * Returns all overdue cleaning logs.
     */
    fun getOverdueLogs(): List<CleaningLog> {
        return todayLogs.filter { it.status == CleaningStatus.OVERDUE }
    }

    /**
     * Returns the count of confirmed cleanings today.
     */
    fun getConfirmedCount(): Int {
        return todayLogs.count { it.status == CleaningStatus.CONFIRMED }
    }

    /**
     * Returns the total scheduled cleanings for today.
     */
    fun getTotalScheduledCount(): Int {
        return todayLogs.size
    }

    /**
     * Returns a summary text for the card.
     */
    fun getSummaryText(): String {
        val confirmed = getConfirmedCount()
        val total = getTotalScheduledCount()
        val overdue = getOverdueLogs().size

        return when {
            overdue > 0 -> "$overdue overdue"
            confirmed == total && total > 0 -> "All clean"
            total > 0 -> "$confirmed/$total done"
            else -> "No schedule"
        }
    }

    companion object {
        private const val DEFAULT_GRACE_PERIOD_MINUTES = 30

        /**
         * Computes the cleaning status from logs.
         */
        fun compute(
            todayLogs: List<CleaningLog>,
            currentTimeMs: Long = System.currentTimeMillis()
        ): FarmCleaningStatus {
            if (todayLogs.isEmpty()) {
                return FarmCleaningStatus(
                    overallStatus = CleaningCardStatus.ALL_CLEAN,
                    todayLogs = emptyList(),
                    nextScheduledTime = null,
                    nextScheduledTimestamp = null,
                    overdueByMinutes = 0
                )
            }

            // Sort logs by scheduled time
            val sortedLogs = todayLogs.sortedBy { it.scheduledTimestamp }

            // Determine overall status
            val hasOverdue = sortedLogs.any { it.status == CleaningStatus.OVERDUE }
            val hasPending = sortedLogs.any { it.status == CleaningStatus.PENDING }
            val allConfirmedOrSkipped = sortedLogs.isNotEmpty() && sortedLogs.all {
                it.status == CleaningStatus.CONFIRMED || it.status == CleaningStatus.SKIPPED
            }

            val overallStatus = when {
                hasOverdue -> CleaningCardStatus.OVERDUE
                hasPending -> CleaningCardStatus.PENDING
                allConfirmedOrSkipped -> CleaningCardStatus.ALL_CLEAN
                sortedLogs.isEmpty() -> CleaningCardStatus.PENDING
                else -> CleaningCardStatus.PENDING
            }

            // Find next pending cleaning
            val nextPending = sortedLogs.firstOrNull { it.status == CleaningStatus.PENDING }

            // Calculate overdue minutes for the earliest overdue cleaning (time since scheduled)
            val overdueLog = sortedLogs.firstOrNull { it.status == CleaningStatus.OVERDUE }
            val overdueByMinutes = if (overdueLog != null) {
                ((currentTimeMs - overdueLog.scheduledTimestamp) / 60000).toInt().coerceAtLeast(0)
            } else 0

            return FarmCleaningStatus(
                overallStatus = overallStatus,
                todayLogs = sortedLogs,
                nextScheduledTime = nextPending?.scheduledTime,
                nextScheduledTimestamp = nextPending?.scheduledTimestamp,
                overdueByMinutes = overdueByMinutes
            )
        }

        /**
         * Creates an empty status when no cleaning schedule exists.
         */
        fun empty(): FarmCleaningStatus {
            return FarmCleaningStatus(
                overallStatus = CleaningCardStatus.ALL_CLEAN,
                todayLogs = emptyList(),
                nextScheduledTime = null,
                nextScheduledTimestamp = null,
                overdueByMinutes = 0
            )
        }
    }
}
