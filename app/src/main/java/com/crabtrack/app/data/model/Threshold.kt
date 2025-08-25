package com.crabtrack.app.data.model

data class Threshold(
    val sensorType: SensorType,
    val warningMin: Double?,
    val warningMax: Double?,
    val criticalMin: Double?,
    val criticalMax: Double?
) {
    fun evaluateAlertLevel(value: Double): AlertLevel {
        return when {
            criticalMin != null && value < criticalMin -> AlertLevel.CRITICAL
            criticalMax != null && value > criticalMax -> AlertLevel.CRITICAL
            warningMin != null && value < warningMin -> AlertLevel.WARNING
            warningMax != null && value > warningMax -> AlertLevel.WARNING
            else -> AlertLevel.NORMAL
        }
    }
}