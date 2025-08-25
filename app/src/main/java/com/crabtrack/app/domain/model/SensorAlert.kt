package com.crabtrack.app.domain.model

import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.data.model.SensorType

data class SensorAlert(
    val sensorType: SensorType,
    val alertLevel: AlertLevel,
    val currentValue: Double,
    val thresholdType: String, // "warning_min", "warning_max", "critical_min", "critical_max"
    val thresholdValue: Double,
    val message: String
) {
    companion object {
        fun fromReading(
            reading: SensorReading,
            thresholdType: String,
            thresholdValue: Double
        ): SensorAlert {
            val message = when (reading.alertLevel) {
                AlertLevel.WARNING -> "Warning: ${reading.sensorType.displayName} is ${formatThresholdMessage(thresholdType, reading.value, thresholdValue)}"
                AlertLevel.CRITICAL -> "Critical: ${reading.sensorType.displayName} is ${formatThresholdMessage(thresholdType, reading.value, thresholdValue)}"
                AlertLevel.NORMAL -> ""
            }
            
            return SensorAlert(
                sensorType = reading.sensorType,
                alertLevel = reading.alertLevel,
                currentValue = reading.value,
                thresholdType = thresholdType,
                thresholdValue = thresholdValue,
                message = message
            )
        }
        
        private fun formatThresholdMessage(thresholdType: String, currentValue: Double, thresholdValue: Double): String {
            return when (thresholdType) {
                "warning_min", "critical_min" -> "too low (${currentValue} < ${thresholdValue})"
                "warning_max", "critical_max" -> "too high (${currentValue} > ${thresholdValue})"
                else -> "out of range"
            }
        }
    }
}