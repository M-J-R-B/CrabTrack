package com.crabtrack.app.data.model

/**
 * Data class representing an alert as stored in Firebase Realtime Database.
 * Path: users/{uid}/alerts/{alertId}
 *
 * Note: Firebase may send numeric fields as either Long or String, so we use Any
 * for fields that can be numeric and convert them safely.
 */
data class FirebaseAlert(
    val created_at: Any = 0L,
    val message: String = "",
    val parameter: String = "",
    val parameter_name: String = "",
    val severity: String = "info",
    val status: String = "",
    val threshold: Any = 0,
    val threshold_type: String = "",
    val threshold_value: Any = 0,
    val type: String = "",
    val value: Any = 0
) {
    /**
     * Converts this Firebase alert to the app's Alert model.
     * @param alertId The Firebase key for this alert
     */
    fun toAlert(alertId: String): Alert {
        return Alert(
            id = alertId,
            tankId = "", // Firebase alerts don't have tankId, use empty string
            parameter = parameter_name.ifEmpty { parameter },
            message = message,
            severity = parseSeverity(severity),
            timestampMs = toLong(created_at)
        )
    }

    /**
     * Gets created_at as a String for compatibility.
     */
    fun getCreatedAtString(): String = created_at.toString()

    companion object {
        fun parseSeverity(severity: String): AlertSeverity {
            return when (severity.lowercase()) {
                "critical" -> AlertSeverity.CRITICAL
                "warning" -> AlertSeverity.WARNING
                "info" -> AlertSeverity.INFO
                else -> AlertSeverity.INFO
            }
        }

        /**
         * Safely converts Any to Long, handling both String and numeric types.
         */
        fun toLong(value: Any?): Long {
            return when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Double -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                is Number -> value.toLong()
                else -> 0L
            }
        }
    }
}
