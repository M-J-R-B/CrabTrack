package com.crabtrack.app.data.model

/**
 * Represents feedback submitted for a molting alert.
 * Used for ML model improvement and tracking user actions.
 *
 * Destination: /molting_feedback/{feedbackId}
 */
data class MoltingFeedback(
    val alertId: String,
    val userId: String,
    val feedback: String,
    val confidence: Double,
    val detectionClass: String,
    val cameraIp: String,
    val tankId: String,
    val timestamp: Long
) {
    companion object {
        const val FEEDBACK_CONFIRMED_MOLTING = "confirmed_molting"
        const val FEEDBACK_CONFIRMED_STILL_MOLTING = "confirmed_still_molting"
        const val FEEDBACK_FALSE_ALARM = "false_alarm"

        /**
         * Creates a MoltingFeedback from a MoltingAlert and feedback type.
         */
        fun fromAlert(
            alert: MoltingAlert,
            userId: String,
            feedbackType: String
        ): MoltingFeedback {
            return MoltingFeedback(
                alertId = alert.id,
                userId = userId,
                feedback = feedbackType,
                confidence = alert.confidence,
                detectionClass = alert.detectionClass,
                cameraIp = alert.cameraIp,
                tankId = alert.tankId,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Converts to a map for Firebase storage.
     */
    fun toFirebaseMap(): Map<String, Any> = mapOf(
        "alert_id" to alertId,
        "user_id" to userId,
        "feedback" to feedback,
        "confidence" to confidence,
        "detection_class" to detectionClass,
        "camera_ip" to cameraIp,
        "tank_id" to tankId,
        "timestamp" to timestamp
    )
}
