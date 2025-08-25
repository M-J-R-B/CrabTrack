package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.data.repository.SensorRepository
import com.crabtrack.app.data.repository.ThresholdRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetSensorReadingsUseCase @Inject constructor(
    private val sensorRepository: SensorRepository,
    private val thresholdRepository: ThresholdRepository
) {
    
    operator fun invoke(): Flow<List<SensorReading>> {
        return combine(
            sensorRepository.getLiveSensorReadings(),
            thresholdRepository.getAllThresholds()
        ) { readings, thresholds ->
            readings.map { reading ->
                val threshold = thresholds[reading.sensorType]
                val alertLevel = threshold?.evaluateAlertLevel(reading.value) ?: reading.alertLevel
                
                reading.copy(alertLevel = alertLevel)
            }
        }
    }
}