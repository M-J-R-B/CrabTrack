package com.crabtrack.app.data.local.datastore

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.Preferences
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold

object ThresholdPreferences {
    
    // Default thresholds for each sensor type
    private val defaultThresholds = mapOf(
        SensorType.PH to Threshold(
            sensorType = SensorType.PH,
            warningMin = 6.8,
            warningMax = 8.2,
            criticalMin = 6.5,
            criticalMax = 8.5
        ),
        SensorType.DISSOLVED_OXYGEN to Threshold(
            sensorType = SensorType.DISSOLVED_OXYGEN,
            warningMin = 6.0,
            warningMax = 10.0,
            criticalMin = 5.0,
            criticalMax = 12.0
        ),
        SensorType.SALINITY to Threshold(
            sensorType = SensorType.SALINITY,
            warningMin = 18.0,
            warningMax = 32.0,
            criticalMin = 15.0,
            criticalMax = 35.0
        ),
        SensorType.AMMONIA to Threshold(
            sensorType = SensorType.AMMONIA,
            warningMin = null,
            warningMax = 0.25,
            criticalMin = null,
            criticalMax = 0.5
        ),
        SensorType.TEMPERATURE to Threshold(
            sensorType = SensorType.TEMPERATURE,
            warningMin = 22.0,
            warningMax = 28.0,
            criticalMin = 20.0,
            criticalMax = 30.0
        ),
        SensorType.WATER_LEVEL to Threshold(
            sensorType = SensorType.WATER_LEVEL,
            warningMin = 60.0,
            warningMax = 90.0,
            criticalMin = 50.0,
            criticalMax = 100.0
        )
    )
    
    fun getDefaultThreshold(sensorType: SensorType): Threshold {
        return defaultThresholds[sensorType] ?: Threshold(
            sensorType = sensorType,
            warningMin = null,
            warningMax = null,
            criticalMin = null,
            criticalMax = null
        )
    }
    
    // Preference keys for each sensor type and threshold value
    fun getWarningMinKey(sensorType: SensorType): Preferences.Key<Double> =
        doublePreferencesKey("${sensorType.name}_warning_min")
    
    fun getWarningMaxKey(sensorType: SensorType): Preferences.Key<Double> =
        doublePreferencesKey("${sensorType.name}_warning_max")
    
    fun getCriticalMinKey(sensorType: SensorType): Preferences.Key<Double> =
        doublePreferencesKey("${sensorType.name}_critical_min")
    
    fun getCriticalMaxKey(sensorType: SensorType): Preferences.Key<Double> =
        doublePreferencesKey("${sensorType.name}_critical_max")
}