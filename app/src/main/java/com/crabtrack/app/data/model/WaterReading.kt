package com.crabtrack.app.data.model

/**
 * Represents a water reading from a specific tank containing all water quality parameters
 * measured at a particular point in time.
 *
 * @property tankId Unique identifier for the tank
 * @property timestampMs Timestamp in milliseconds when the reading was taken
 * @property pH pH level of the water (typically 0.0-14.0)
 * @property dissolvedOxygenMgL Dissolved oxygen content in milligrams per liter (nullable for MQTT source)
 * @property salinityPpt Salinity level in parts per thousand
 * @property ammoniaMgL Ammonia content in milligrams per liter (nullable for MQTT source)
 * @property temperatureC Water temperature in degrees Celsius
 * @property waterLevelCm Water level in centimeters (nullable for MQTT source)
 * @property tdsPpm Total dissolved solids in parts per million (from MQTT/ESP32)
 * @property turbidityNTU Turbidity in Nephelometric Turbidity Units (from MQTT/ESP32)
 */
data class WaterReading(
    val tankId: String,
    val timestampMs: Long,
    val pH: Double,
    val dissolvedOxygenMgL: Double? = null,
    val salinityPpt: Double,
    val ammoniaMgL: Double? = null,
    val temperatureC: Double,
    val waterLevelCm: Double? = null,
    val tdsPpm: Double? = null,
    val turbidityNTU: Double? = null
)