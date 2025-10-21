package com.crabtrack.app.data.local

import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.data.model.SensorType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MockTelemetryDataSource @Inject constructor() {
    
    private val sensorRanges = mapOf(
        SensorType.PH to (6.5 to 8.5),
        SensorType.DISSOLVED_OXYGEN to (5.0 to 12.0),
        SensorType.SALINITY to (15.0 to 35.0),
        SensorType.AMMONIA to (0.0 to 0.5),
        SensorType.TEMPERATURE to (20.0 to 30.0),
        SensorType.WATER_LEVEL to (50.0 to 100.0),
        SensorType.TDS to (12000.0 to 28000.0),
        SensorType.TURBIDITY to (0.0 to 45.0)
    )
    
    fun getSensorReadingsFlow(): Flow<List<SensorReading>> = flow {
        while (true) {
            val readings = SensorType.values().map { sensorType ->
                val (min, max) = sensorRanges[sensorType] ?: (0.0 to 100.0)
                val baseValue = Random.nextDouble(min, max)
                
                // Add some realistic variation
                val variation = (max - min) * 0.05 // 5% variation
                val value = baseValue + Random.nextDouble(-variation, variation)
                
                SensorReading(
                    sensorType = sensorType,
                    value = value.coerceIn(min, max)
                )
            }
            
            emit(readings)
            delay(2000) // Emit new readings every 2 seconds
        }
    }
    
    fun getSensorReadingFlow(sensorType: SensorType): Flow<SensorReading> = flow {
        val (min, max) = sensorRanges[sensorType] ?: (0.0 to 100.0)
        
        while (true) {
            val baseValue = Random.nextDouble(min, max)
            val variation = (max - min) * 0.05
            val value = baseValue + Random.nextDouble(-variation, variation)
            
            emit(
                SensorReading(
                    sensorType = sensorType,
                    value = value.coerceIn(min, max)
                )
            )
            
            delay(2000)
        }
    }
}