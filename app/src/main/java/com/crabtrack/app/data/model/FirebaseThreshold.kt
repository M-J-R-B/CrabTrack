package com.crabtrack.app.data.model

/**
 * Firebase data model matching the existing web structure at users/{uid}/thresholds/{sensor}/
 * Structure: { enabled: boolean, min: number, max: number }
 */
data class FirebaseThresholdData(
    val enabled: Boolean = true,
    val min: Double? = null,
    val max: Double? = null
)

/**
 * Extension function to convert Android's Threshold model to Firebase format
 * Maps warningMin/Max to simple min/max (critical levels not supported in web yet)
 */
fun Threshold.toFirebaseData() = FirebaseThresholdData(
    enabled = true,
    min = warningMin,
    max = warningMax
)

/**
 * Extension function to convert Firebase data to Android's Threshold model
 * Maps simple min/max to warningMin/Max, critical levels remain null
 */
fun FirebaseThresholdData.toThreshold(sensorType: SensorType) = Threshold(
    sensorType = sensorType,
    warningMin = min,
    warningMax = max,
    criticalMin = null,  // Not supported in web structure yet
    criticalMax = null   // Not supported in web structure yet
)

/**
 * Helper to get lowercase sensor name for Firebase (web convention)
 * Web uses: "ph", "salinity", "tds", "temperature", "turbidity"
 * Android uses: "PH", "SALINITY", "TDS", "TEMPERATURE", "TURBIDITY"
 */
fun SensorType.toFirebaseKey(): String {
    return when (this) {
        SensorType.PH -> "ph"
        SensorType.DISSOLVED_OXYGEN -> "dissolved_oxygen"
        SensorType.SALINITY -> "salinity"
        SensorType.AMMONIA -> "ammonia"
        SensorType.TEMPERATURE -> "temperature"
        SensorType.WATER_LEVEL -> "water_level"
        SensorType.TDS -> "tds"
        SensorType.TURBIDITY -> "turbidity"
    }
}

/**
 * Helper to convert Firebase key to SensorType
 */
fun String.toSensorType(): SensorType? {
    return when (this.lowercase()) {
        "ph" -> SensorType.PH
        "dissolved_oxygen" -> SensorType.DISSOLVED_OXYGEN
        "salinity" -> SensorType.SALINITY
        "ammonia" -> SensorType.AMMONIA
        "temperature" -> SensorType.TEMPERATURE
        "water_level" -> SensorType.WATER_LEVEL
        "tds" -> SensorType.TDS
        "turbidity" -> SensorType.TURBIDITY
        else -> null
    }
}
