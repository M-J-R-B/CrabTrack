package com.crabtrack.app.presentation.utils

import com.crabtrack.app.data.model.WaterReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    
    fun formatpH(value: Double): String = String.format(Locale.US, "%.1f", value)
    
    fun formatDissolvedOxygen(value: Double): String = String.format(Locale.US, "%.1f mg/L", value)
    
    fun formatSalinity(value: Double): String = String.format(Locale.US, "%.1f ppt", value)
    
    fun formatAmmonia(value: Double): String = String.format(Locale.US, "%.1f mg/L", value)
    
    fun formatTemperature(value: Double): String = String.format(Locale.US, "%.1f°C", value)
    
    fun formatWaterLevel(value: Double): String = String.format(Locale.US, "%.1f cm", value)
    
    fun formatTimestamp(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        return formatter.format(Date(timestampMs))
    }
    
    fun formatTimeOnly(timestampMs: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return formatter.format(Date(timestampMs))
    }
    
    fun getParameterLabel(parameter: String): String = when (parameter) {
        "pH" -> "pH"
        "Dissolved Oxygen" -> "DO"
        "Salinity" -> "Salinity"
        "Ammonia" -> "Ammonia"
        "Temperature" -> "Temperature"
        "Water Level" -> "Level"
        else -> parameter
    }
    
    fun getParameterValue(reading: WaterReading, parameter: String): String = when (parameter) {
        "pH" -> formatpH(reading.pH)
        "Dissolved Oxygen" -> formatDissolvedOxygen(reading.dissolvedOxygenMgL)
        "Salinity" -> formatSalinity(reading.salinityPpt)
        "Ammonia" -> formatAmmonia(reading.ammoniaMgL)
        "Temperature" -> formatTemperature(reading.temperatureC)
        "Water Level" -> formatWaterLevel(reading.waterLevelCm)
        else -> ""
    }
}