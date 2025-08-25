package com.crabtrack.app.data.model

/**
 * Represents a water reading from a specific tank containing all water quality parameters
 * measured at a particular point in time.
 *
 * @property tankId Unique identifier for the tank
 * @property timestampMs Timestamp in milliseconds when the reading was taken
 * @property pH pH level of the water (typically 0.0-14.0)
 * @property dissolvedOxygenMgL Dissolved oxygen content in milligrams per liter
 * @property salinityPpt Salinity level in parts per thousand
 * @property ammoniaMgL Ammonia content in milligrams per liter
 * @property temperatureC Water temperature in degrees Celsius
 * @property waterLevelCm Water level in centimeters
 */
data class WaterReading(
    val tankId: String,
    val timestampMs: Long,
    val pH: Double,
    val dissolvedOxygenMgL: Double,
    val salinityPpt: Double,
    val ammoniaMgL: Double,
    val temperatureC: Double,
    val waterLevelCm: Double
)