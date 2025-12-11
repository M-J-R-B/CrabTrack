package com.crabtrack.app.data.model

/**
 * Represents a molting alert from the ML detection system.
 *
 * Source: /molting_alerts/{alertId} (global, from ML)
 * Destination: users/{uid}/molting_alerts/{alertId} (user-scoped)
 */
data class MoltingAlert(
    val id: String,
    val cameraIp: String,
    val confidence: Double,
    val detectionClass: String,
    val status: String,           // "unread" / "read" / "dismissed"
    val timestamp: String,        // ISO 8601 format
    val tankId: String,           // Derived from cameraIp mapping
    val imagePath: String? = null // Optional URL to detection image
) {
    companion object {
        const val STATUS_UNREAD = "unread"
        const val STATUS_READ = "read"
        const val STATUS_DISMISSED = "dismissed"
        const val CONFIDENCE_THRESHOLD = 0.80
    }

    /**
     * Returns true if confidence is >= 80% (threshold for copying to user's alerts)
     */
    fun isHighConfidence(): Boolean = confidence >= CONFIDENCE_THRESHOLD

    /**
     * Maps detection to alert severity.
     * Molting-Crab detection is always CRITICAL.
     */
    fun toSeverity(): AlertSeverity = when {
        detectionClass.contains("Molting", ignoreCase = true) -> AlertSeverity.CRITICAL
        else -> AlertSeverity.WARNING
    }

    /**
     * Converts to a map for Firebase storage.
     */
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "camera_ip" to cameraIp,
        "confidence" to confidence,
        "detection_class" to detectionClass,
        "status" to status,
        "timestamp" to timestamp,
        "tank_id" to tankId,
        "image_path" to imagePath
    )
}
