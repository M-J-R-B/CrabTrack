package com.crabtrack.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crabtrack.app.data.local.database.entities.SensorReadingEntity
import com.crabtrack.app.data.model.SensorType
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorReadingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: SensorReadingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<SensorReadingEntity>)
    
    @Query("SELECT * FROM sensor_readings WHERE sensorType = :sensorType ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReadingForSensor(sensorType: SensorType): Flow<SensorReadingEntity?>
    
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<SensorReadingEntity>>
    
    @Query("SELECT * FROM sensor_readings WHERE sensorType = :sensorType ORDER BY timestamp DESC LIMIT :limit")
    fun getReadingsForSensor(sensorType: SensorType, limit: Int = 100): Flow<List<SensorReadingEntity>>
    
    @Query("DELETE FROM sensor_readings WHERE timestamp < :cutoffTime")
    suspend fun deleteOldReadings(cutoffTime: java.time.LocalDateTime)
}