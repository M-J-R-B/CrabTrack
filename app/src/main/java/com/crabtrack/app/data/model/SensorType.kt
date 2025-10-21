package com.crabtrack.app.data.model

enum class SensorType(
    val displayName: String,
    val unit: String,
    val iconRes: Int? = null
) {
    PH("pH", "", null),
    DISSOLVED_OXYGEN("Dissolved Oxygen", "mg/L", null),
    SALINITY("Salinity", "ppt", null),
    AMMONIA("Ammonia", "mg/L", null),
    TEMPERATURE("Temperature", "°C", null),
    WATER_LEVEL("Water Level", "cm", null),
    TDS("TDS", "ppm", null),
    TURBIDITY("Turbidity", "NTU", null)
}