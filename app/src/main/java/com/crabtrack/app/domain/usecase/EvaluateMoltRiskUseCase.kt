package com.crabtrack.app.domain.usecase

import com.crabtrack.app.core.MoltDefaults
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.data.model.WaterReading
import javax.inject.Inject

/**
 * Use case for evaluating molt-related risk levels based on current state, timing, and water conditions.
 * 
 * Determines the severity level and remaining care window for hermit crab molting events,
 * taking into account molt state, elapsed time, and water quality parameters.
 */
class EvaluateMoltRiskUseCase @Inject constructor() {
    
    /**
     * Result of molt risk evaluation
     */
    data class MoltRiskResult(
        val riskLevel: AlertSeverity,
        val careWindowRemainingMs: Long?
    )
    
    /**
     * Evaluates molt risk based on current conditions
     * 
     * @param state Current molt state
     * @param event Current molt event (null if no active molting)
     * @param nowMs Current timestamp in milliseconds
     * @param waterReading Optional water quality reading to factor into risk assessment
     * @return Risk evaluation result
     */
    operator fun invoke(
        state: MoltState,
        event: MoltEvent?,
        nowMs: Long,
        waterReading: WaterReading? = null
    ): MoltRiskResult {
        val baseRisk = evaluateStateRisk(state, event, nowMs)
        val waterRisk = evaluateWaterQualityRisk(state, waterReading)
        val careWindow = calculateCareWindowRemaining(state, event, nowMs)
        
        // Take the higher of base risk and water quality risk
        val finalRisk = if (waterRisk.ordinal > baseRisk.ordinal) waterRisk else baseRisk
        
        return MoltRiskResult(
            riskLevel = finalRisk,
            careWindowRemainingMs = careWindow
        )
    }
    
    private fun evaluateStateRisk(
        state: MoltState,
        event: MoltEvent?,
        nowMs: Long
    ): AlertSeverity {
        return when (state) {
            MoltState.NONE -> AlertSeverity.INFO
            
            MoltState.PREMOLT -> AlertSeverity.WARNING
            
            MoltState.ECDYSIS -> AlertSeverity.CRITICAL
            
            MoltState.POSTMOLT_RISK -> {
                // Check if still within critical 6-hour window
                event?.let { evt ->
                    val elapsedMs = nowMs - evt.startedAtMs
                    if (elapsedMs <= MoltDefaults.POSTMOLT_RISK_WINDOW_MS) {
                        AlertSeverity.CRITICAL
                    } else {
                        AlertSeverity.WARNING
                    }
                } ?: AlertSeverity.CRITICAL // Default to critical if no event data
            }
            
            MoltState.POSTMOLT_SAFE -> AlertSeverity.WARNING
        }
    }
    
    private fun evaluateWaterQualityRisk(
        state: MoltState,
        waterReading: WaterReading?
    ): AlertSeverity {
        waterReading ?: return AlertSeverity.INFO
        
        // During critical molt phases, water quality is more important
        val isCriticalPhase = state == MoltState.ECDYSIS || state == MoltState.POSTMOLT_RISK
        
        var waterRisk = AlertSeverity.INFO
        
        // Dissolved Oxygen - critical during molting
        if (waterReading.dissolvedOxygenMgL < 5.0) {
            waterRisk = if (isCriticalPhase) AlertSeverity.CRITICAL else AlertSeverity.WARNING
        }
        
        // Ammonia - toxic during vulnerable phases
        if (waterReading.ammoniaMgL > 0.1) {
            waterRisk = if (isCriticalPhase) AlertSeverity.CRITICAL else AlertSeverity.WARNING
        }
        
        // pH - important for shell hardening
        if (waterReading.pH < 7.5 || waterReading.pH > 8.5) {
            val phRisk = if (state == MoltState.POSTMOLT_SAFE || state == MoltState.POSTMOLT_RISK) {
                AlertSeverity.WARNING
            } else {
                AlertSeverity.INFO
            }
            if (phRisk.ordinal > waterRisk.ordinal) {
                waterRisk = phRisk
            }
        }
        
        // Temperature - affects metabolism during molting
        if (waterReading.temperatureC < 22.0 || waterReading.temperatureC > 28.0) {
            val tempRisk = if (isCriticalPhase) AlertSeverity.WARNING else AlertSeverity.INFO
            if (tempRisk.ordinal > waterRisk.ordinal) {
                waterRisk = tempRisk
            }
        }
        
        return waterRisk
    }
    
    private fun calculateCareWindowRemaining(
        state: MoltState,
        event: MoltEvent?,
        nowMs: Long
    ): Long? {
        event ?: return null
        
        val elapsedMs = nowMs - event.startedAtMs
        
        return when (state) {
            MoltState.ECDYSIS -> {
                // No specific window for ecdysis - it ends when it ends
                null
            }
            
            MoltState.POSTMOLT_RISK -> {
                val remainingRiskWindow = MoltDefaults.POSTMOLT_RISK_WINDOW_MS - elapsedMs
                if (remainingRiskWindow > 0) remainingRiskWindow else 0L
            }
            
            MoltState.POSTMOLT_SAFE -> {
                val remainingSafeWindow = MoltDefaults.TOTAL_POSTMOLT_WINDOW_MS - elapsedMs
                if (remainingSafeWindow > 0) remainingSafeWindow else 0L
            }
            
            MoltState.PREMOLT -> {
                // Pre-molt can last variable time - no specific window
                null
            }
            
            MoltState.NONE -> null
        }
    }
}