package com.crabtrack.app.data.model

/**
 * Represents a tank in the crab tracking system.
 * Each tank can have its own camera IP for monitoring.
 * Synchronized with web app using Firebase path: users/{uid}/tanks/{tankId}/
 */
data class Tank(
    val id: String = "",              // e.g., "tank1", "tank2"
    val displayName: String = "",     // e.g., "Tank 1", "Tank 2" (called "name" in web app)
    val number: Int = 1,              // Sequential number for ordering
    val cameraIp: String = DEFAULT_CAMERA_IP,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    // Web app fields (optional)
    val crabInDate: String? = null,   // Date crabs were added to tank
    val crabOutDate: String? = null,  // Date crabs were removed
    val initialWeight: Double? = null // Initial weight of crabs
) {
    companion object {
        const val DEFAULT_CAMERA_IP = "192.168.8.115"

        /**
         * Creates a new tank with the given number.
         * Uses format tank1, tank2, etc. to match web app.
         * @param number The tank number (1, 2, 3, etc.)
         * @param cameraIp Optional camera IP, defaults to Tank 1's IP
         */
        fun create(number: Int, cameraIp: String = DEFAULT_CAMERA_IP): Tank {
            val now = System.currentTimeMillis()
            return Tank(
                id = "tank$number",  // Format: tank1, tank2, etc.
                displayName = "Tank $number",
                number = number,
                cameraIp = cameraIp,
                createdAt = now,
                updatedAt = now
            )
        }

        /**
         * Creates the default Tank 1.
         */
        fun createDefault(): Tank = create(1)

        /**
         * Extracts tank number from tank ID.
         * Handles format: "tank1", "tank2", etc.
         */
        fun extractNumber(tankId: String): Int {
            return tankId.removePrefix("tank").toIntOrNull() ?: 1
        }
    }

    /**
     * Returns a copy with updated timestamp.
     */
    fun withUpdatedTimestamp(): Tank = copy(updatedAt = System.currentTimeMillis())

    /**
     * Returns a copy with new camera IP.
     */
    fun withCameraIp(newIp: String): Tank = copy(
        cameraIp = newIp,
        updatedAt = System.currentTimeMillis()
    )

    /**
     * Converts to a map for Firebase storage.
     * Includes both Android fields and web-compatible "name" field.
     */
    fun toMap(): Map<String, Any?> = buildMap {
        put("id", id)
        put("name", displayName)          // Web app uses "name"
        put("displayName", displayName)   // Android uses "displayName"
        put("number", number)
        put("cameraIp", cameraIp)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        // Include web app fields if present
        crabInDate?.let { put("crab_in_date", it) }
        crabOutDate?.let { put("crab_out_date", it) }
        initialWeight?.let { put("initial_weight", it) }
    }
}
