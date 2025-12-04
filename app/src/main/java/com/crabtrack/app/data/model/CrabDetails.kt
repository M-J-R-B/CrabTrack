package com.crabtrack.app.data.model

/**
 * Represents crab details for a specific tank.
 *
 * @property tankId Unique identifier for the tank
 * @property crabName Optional name/identifier for the crab
 * @property placedDate Date crab was placed in tank (format: yyyy-MM-dd)
 * @property placedDateMs Timestamp in milliseconds when crab was placed
 * @property initialWeightGrams Initial weight of crab in grams
 * @property removalDate Date crab was/will be removed (format: yyyy-MM-dd)
 * @property removalDateMs Timestamp in milliseconds for removal
 * @property removalWeightGrams Weight of crab when removed in grams
 * @property updatedAt Last modification timestamp
 * @property createdAt Creation timestamp
 */
data class CrabDetails(
    val tankId: String = "",
    val crabName: String? = null,
    val placedDate: String? = null,
    val placedDateMs: Long? = null,
    val initialWeightGrams: Double? = null,
    val removalDate: String? = null,
    val removalDateMs: Long? = null,
    val removalWeightGrams: Double? = null,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
) {
    /**
     * Check if any crab details have been filled in
     */
    fun hasDetails(): Boolean {
        return crabName != null || placedDate != null || initialWeightGrams != null
    }

    /**
     * Check if the crab has removal information
     */
    fun hasRemovalInfo(): Boolean {
        return removalDate != null || removalWeightGrams != null
    }

    /**
     * Calculate weight change if both initial and removal weights exist
     * @return Weight change in grams (positive = gain, negative = loss), or null if not calculable
     */
    fun getWeightChange(): Double? {
        return if (initialWeightGrams != null && removalWeightGrams != null) {
            removalWeightGrams - initialWeightGrams
        } else null
    }

    companion object {
        /**
         * Create a new CrabDetails instance with timestamps auto-populated
         */
        fun create(
            tankId: String,
            crabName: String? = null,
            placedDate: String? = null,
            placedDateMs: Long? = null,
            initialWeightGrams: Double? = null,
            removalDate: String? = null,
            removalDateMs: Long? = null,
            removalWeightGrams: Double? = null,
            existingCreatedAt: Long? = null
        ): CrabDetails {
            val now = System.currentTimeMillis()
            return CrabDetails(
                tankId = tankId,
                crabName = crabName,
                placedDate = placedDate,
                placedDateMs = placedDateMs,
                initialWeightGrams = initialWeightGrams,
                removalDate = removalDate,
                removalDateMs = removalDateMs,
                removalWeightGrams = removalWeightGrams,
                updatedAt = now,
                createdAt = existingCreatedAt ?: now
            )
        }

        /**
         * Create an empty CrabDetails for a tank (used after reset)
         */
        fun empty(tankId: String): CrabDetails {
            return CrabDetails(tankId = tankId)
        }
    }
}
