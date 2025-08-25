package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.core.Defaults
import java.util.UUID
import javax.inject.Inject

class EvaluateThresholdsUseCase @Inject constructor() {
    
    fun evaluate(reading: WaterReading, thresholds: Thresholds = Defaults.createDefaultThresholds()): Alert? {
        val alerts = mutableListOf<Alert>()
        
        evaluatepH(reading, thresholds)?.let { alerts.add(it) }
        evaluateDissolvedOxygen(reading, thresholds)?.let { alerts.add(it) }
        evaluateSalinity(reading, thresholds)?.let { alerts.add(it) }
        evaluateAmmonia(reading, thresholds)?.let { alerts.add(it) }
        evaluateTemperature(reading, thresholds)?.let { alerts.add(it) }
        evaluateWaterLevel(reading, thresholds)?.let { alerts.add(it) }
        
        return alerts.maxByOrNull { it.severity.ordinal }
    }
    
    fun evaluateAll(reading: WaterReading, thresholds: Thresholds = Defaults.createDefaultThresholds()): List<Alert> {
        val alerts = mutableListOf<Alert>()
        
        evaluatepH(reading, thresholds)?.let { alerts.add(it) }
        evaluateDissolvedOxygen(reading, thresholds)?.let { alerts.add(it) }
        evaluateSalinity(reading, thresholds)?.let { alerts.add(it) }
        evaluateAmmonia(reading, thresholds)?.let { alerts.add(it) }
        evaluateTemperature(reading, thresholds)?.let { alerts.add(it) }
        evaluateWaterLevel(reading, thresholds)?.let { alerts.add(it) }
        
        return alerts
    }
    
    private fun evaluatepH(reading: WaterReading, thresholds: Thresholds): Alert? {
        return when {
            reading.pH < thresholds.pHMin -> createAlert(
                reading,
                "pH",
                "pH level (${String.format("%.2f", reading.pH)}) is below minimum threshold (${thresholds.pHMin})",
                AlertSeverity.WARNING
            )
            reading.pH > thresholds.pHMax -> createAlert(
                reading,
                "pH",
                "pH level (${String.format("%.2f", reading.pH)}) exceeds maximum threshold (${thresholds.pHMax})",
                AlertSeverity.WARNING
            )
            else -> null
        }
    }
    
    private fun evaluateDissolvedOxygen(reading: WaterReading, thresholds: Thresholds): Alert? {
        return if (reading.dissolvedOxygenMgL < thresholds.doMin) {
            createAlert(
                reading,
                "Dissolved Oxygen",
                "Dissolved oxygen (${String.format("%.2f", reading.dissolvedOxygenMgL)} mg/L) is below minimum threshold (${thresholds.doMin})",
                AlertSeverity.CRITICAL
            )
        } else null
    }
    
    private fun evaluateSalinity(reading: WaterReading, thresholds: Thresholds): Alert? {
        return when {
            reading.salinityPpt < thresholds.salinityMin -> createAlert(
                reading,
                "Salinity",
                "Salinity (${String.format("%.2f", reading.salinityPpt)} ppt) is below minimum threshold (${thresholds.salinityMin})",
                AlertSeverity.WARNING
            )
            reading.salinityPpt > thresholds.salinityMax -> createAlert(
                reading,
                "Salinity",
                "Salinity (${String.format("%.2f", reading.salinityPpt)} ppt) exceeds maximum threshold (${thresholds.salinityMax})",
                AlertSeverity.WARNING
            )
            else -> null
        }
    }
    
    private fun evaluateAmmonia(reading: WaterReading, thresholds: Thresholds): Alert? {
        return if (reading.ammoniaMgL > thresholds.ammoniaMax) {
            createAlert(
                reading,
                "Ammonia",
                "Ammonia level (${String.format("%.2f", reading.ammoniaMgL)} mg/L) is critically high",
                AlertSeverity.CRITICAL
            )
        } else null
    }
    
    private fun evaluateTemperature(reading: WaterReading, thresholds: Thresholds): Alert? {
        return when {
            reading.temperatureC < thresholds.tempMin -> createAlert(
                reading,
                "Temperature",
                "Temperature (${String.format("%.1f", reading.temperatureC)}°C) is below minimum threshold (${thresholds.tempMin}°C)",
                AlertSeverity.WARNING
            )
            reading.temperatureC > thresholds.tempMax -> createAlert(
                reading,
                "Temperature",
                "Temperature (${String.format("%.1f", reading.temperatureC)}°C) exceeds maximum threshold (${thresholds.tempMax}°C)",
                AlertSeverity.WARNING
            )
            else -> null
        }
    }
    
    private fun evaluateWaterLevel(reading: WaterReading, thresholds: Thresholds): Alert? {
        return when {
            reading.waterLevelCm < thresholds.levelMin -> createAlert(
                reading,
                "Water Level",
                "Water level (${String.format("%.1f", reading.waterLevelCm)} cm) is below minimum threshold (${thresholds.levelMin} cm)",
                AlertSeverity.WARNING
            )
            reading.waterLevelCm > thresholds.levelMax -> createAlert(
                reading,
                "Water Level",
                "Water level (${String.format("%.1f", reading.waterLevelCm)} cm) exceeds maximum threshold (${thresholds.levelMax} cm)",
                AlertSeverity.WARNING
            )
            else -> null
        }
    }
    
    private fun createAlert(
        reading: WaterReading,
        parameter: String,
        message: String,
        severity: AlertSeverity
    ): Alert {
        return Alert(
            id = UUID.randomUUID().toString(),
            tankId = reading.tankId,
            parameter = parameter,
            message = message,
            severity = severity,
            timestampMs = reading.timestampMs
        )
    }
}