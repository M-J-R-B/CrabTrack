package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.repository.SensorRepository
import com.crabtrack.app.data.repository.ThresholdRepository
import com.crabtrack.app.domain.model.SensorAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CheckAlertsUseCase @Inject constructor(
    private val sensorRepository: SensorRepository,
    private val thresholdRepository: ThresholdRepository
) {
    
    operator fun invoke(): Flow<List<SensorAlert>> {
        return combine(
            sensorRepository.getLiveSensorReadings(),
            thresholdRepository.getAllThresholds()
        ) { readings, thresholds ->
            readings.mapNotNull { reading ->
                val threshold = thresholds[reading.sensorType]
                threshold?.let { evaluateAlert(reading, it) }
            }.filter { it.alertLevel != AlertLevel.NORMAL }
        }
    }
    
    fun invoke(sensorType: SensorType): Flow<SensorAlert?> {
        return combine(
            sensorRepository.getLiveSensorReading(sensorType),
            thresholdRepository.getThreshold(sensorType)
        ) { reading, threshold ->
            evaluateAlert(reading, threshold).takeIf { it.alertLevel != AlertLevel.NORMAL }
        }
    }
    
    private fun evaluateAlert(reading: SensorReading, threshold: com.crabtrack.app.data.model.Threshold): SensorAlert {
        val alertLevel = threshold.evaluateAlertLevel(reading.value)
        val (thresholdType, thresholdValue) = when {
            threshold.criticalMin != null && reading.value < threshold.criticalMin -> "critical_min" to threshold.criticalMin
            threshold.criticalMax != null && reading.value > threshold.criticalMax -> "critical_max" to threshold.criticalMax
            threshold.warningMin != null && reading.value < threshold.warningMin -> "warning_min" to threshold.warningMin
            threshold.warningMax != null && reading.value > threshold.warningMax -> "warning_max" to threshold.warningMax
            else -> "normal" to 0.0
        }
        
        return SensorAlert.fromReading(
            reading.copy(alertLevel = alertLevel),
            thresholdType,
            thresholdValue
        )
    }
}