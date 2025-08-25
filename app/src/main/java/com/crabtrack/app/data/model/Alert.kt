package com.crabtrack.app.data.model

/**
 * Represents an alert generated when water quality parameters exceed acceptable thresholds.
 *
 * @property id Unique identifier for the alert
 * @property tankId Identifier of the tank that generated the alert
 * @property parameter Name of the water quality parameter that triggered the alert
 * @property message Human-readable description of the alert
 * @property severity Severity level of the alert
 * @property timestampMs Timestamp in milliseconds when the alert was generated
 */
data class Alert(
    val id: String,
    val tankId: String,
    val parameter: String,
    val message: String,
    val severity: AlertSeverity,
    val timestampMs: Long
)

/**
 * Enum representing the severity levels of alerts.
 *
 * @property INFO Informational alert - parameters are normal or within acceptable range
 * @property WARNING Warning alert - parameters are outside optimal range but not critical
 * @property CRITICAL Critical alert - parameters are at dangerous levels requiring immediate attention
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}