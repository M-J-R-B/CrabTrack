package com.crabtrack.app.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context
import com.crabtrack.app.data.local.database.dao.SensorReadingDao
import com.crabtrack.app.data.local.database.entities.SensorReadingEntity
import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.data.model.SensorType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Database(
    entities = [SensorReadingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun sensorReadingDao(): SensorReadingDao
    
    companion object {
        const val DATABASE_NAME = "crabtrack_database"
    }
}

class Converters {
    
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, formatter) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.format(formatter)
    }
    
    @TypeConverter
    fun fromSensorType(value: String): SensorType {
        return SensorType.valueOf(value)
    }
    
    @TypeConverter
    fun sensorTypeToString(sensorType: SensorType): String {
        return sensorType.name
    }
    
    @TypeConverter
    fun fromAlertLevel(value: String): AlertLevel {
        return AlertLevel.valueOf(value)
    }
    
    @TypeConverter
    fun alertLevelToString(alertLevel: AlertLevel): String {
        return alertLevel.name
    }
}