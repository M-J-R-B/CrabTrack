package com.crabtrack.app.presentation.utils

import android.text.format.DateFormat
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

    fun formatTDS(value: Double): String = String.format(Locale.US, "%.0f ppm", value)

    fun formatTurbidity(value: Double): String = String.format(Locale.US, "%.1f NTU", value)

    /**
     * Formats timestamp in milliseconds to device local time.
     * Assumes incoming timestamps are in UTC (from server/ESP32).
     * Uses system default timezone for display.
     */
    fun formatTimestamp(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        // SimpleDateFormat automatically converts from UTC epoch to device timezone
        // No need to explicitly set timezone - epochMs is already in UTC
        return formatter.format(Date(timestampMs))
    }

    /**
     * Formats time-only in device local time.
     * Uses 24-hour format (HH:mm:ss).
     */
    fun formatTimeOnly(timestampMs: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    /**
     * Formats timestamp with explicit timezone indicator.
     * Useful for debugging timezone issues.
     */
    fun formatTimestampWithZone(timestampMs: Long): String {
        val formatter = SimpleDateFormat("MMM dd, HH:mm z", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }
    
    fun getParameterLabel(parameter: String): String = when (parameter) {
        "pH" -> "pH"
        "Dissolved Oxygen" -> "DO"
        "Salinity" -> "Salinity"
        "Ammonia" -> "Ammonia"
        "Temperature" -> "Temperature"
        "Water Level" -> "Level"
        "TDS" -> "TDS"
        "Turbidity" -> "Turbidity"
        else -> parameter
    }

    fun getParameterValue(reading: WaterReading, parameter: String): String = when (parameter) {
        "pH" -> formatpH(reading.pH)
        "Dissolved Oxygen" -> reading.dissolvedOxygenMgL?.let { formatDissolvedOxygen(it) } ?: "N/A"
        "Salinity" -> formatSalinity(reading.salinityPpt)
        "Ammonia" -> reading.ammoniaMgL?.let { formatAmmonia(it) } ?: "N/A"
        "Temperature" -> formatTemperature(reading.temperatureC)
        "Water Level" -> reading.waterLevelCm?.let { formatWaterLevel(it) } ?: "N/A"
        "TDS" -> reading.tdsPpm?.let { formatTDS(it) } ?: "N/A"
        "Turbidity" -> reading.turbidityNTU?.let { formatTurbidity(it) } ?: "N/A"
        else -> ""
    }
}