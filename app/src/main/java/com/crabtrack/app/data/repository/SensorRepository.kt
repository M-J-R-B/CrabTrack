package com.crabtrack.app.data.repository

import com.crabtrack.app.data.local.MockTelemetryDataSource
import com.crabtrack.app.data.local.database.dao.SensorReadingDao
import com.crabtrack.app.data.local.database.entities.SensorReadingEntity
import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.data.model.SensorType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorRepository @Inject constructor(
    private val mockDataSource: MockTelemetryDataSource,
    private val sensorReadingDao: SensorReadingDao
) {
    
    fun getLiveSensorReadings(): Flow<List<SensorReading>> {
        return mockDataSource.getSensorReadingsFlow()
    }
    
    fun getLiveSensorReading(sensorType: SensorType): Flow<SensorReading> {
        return mockDataSource.getSensorReadingFlow(sensorType)
    }
    
    fun getLatestReadingFromDatabase(sensorType: SensorType): Flow<SensorReading?> {
        return sensorReadingDao.getLatestReadingForSensor(sensorType)
            .map { entity -> entity?.toDomainModel() }
    }
    
    fun getAllReadingsFromDatabase(): Flow<List<SensorReading>> {
        return sensorReadingDao.getAllReadings()
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    fun getReadingsForSensor(sensorType: SensorType, limit: Int = 100): Flow<List<SensorReading>> {
        return sensorReadingDao.getReadingsForSensor(sensorType, limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    suspend fun saveReading(reading: SensorReading) {
        sensorReadingDao.insertReading(reading.toEntity())
    }
    
    suspend fun saveReadings(readings: List<SensorReading>) {
        sensorReadingDao.insertReadings(readings.map { it.toEntity() })
    }
    
    suspend fun deleteOldReadings(cutoffTime: java.time.LocalDateTime) {
        sensorReadingDao.deleteOldReadings(cutoffTime)
    }
}

// Extension functions for mapping between domain and entity models
private fun SensorReading.toEntity(): SensorReadingEntity {
    return SensorReadingEntity(
        sensorType = sensorType,
        value = value,
        timestamp = timestamp,
        alertLevel = alertLevel
    )
}

private fun SensorReadingEntity.toDomainModel(): SensorReading {
    return SensorReading(
        sensorType = sensorType,
        value = value,
        timestamp = timestamp,
        alertLevel = alertLevel
    )
}