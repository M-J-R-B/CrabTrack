package com.crabtrack.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.data.model.SensorType
import java.time.LocalDateTime

@Entity(tableName = "sensor_readings")
data class SensorReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sensorType: SensorType,
    val value: Double,
    val timestamp: LocalDateTime,
    val alertLevel: AlertLevel
)