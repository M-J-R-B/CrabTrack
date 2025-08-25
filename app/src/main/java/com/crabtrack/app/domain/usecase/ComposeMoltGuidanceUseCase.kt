package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltState
import javax.inject.Inject

/**
 * Use case for composing operator guidance strings based on molt state and risk level.
 * 
 * Provides concise, actionable care instructions for hermit crab operators during
 * different molting phases and risk conditions.
 */
class ComposeMoltGuidanceUseCase @Inject constructor() {
    
    /**
     * Composes guidance text based on current molt conditions
     * 
     * @param state Current molt state
     * @param riskLevel Current risk severity level
     * @param careWindowRemainingMs Remaining care window in milliseconds (null if not applicable)
     * @return Short, actionable guidance string for operators
     */
    operator fun invoke(
        state: MoltState,
        riskLevel: AlertSeverity,
        careWindowRemainingMs: Long? = null
    ): String {
        val baseGuidance = getBaseGuidanceForState(state)
        val riskModifiers = getRiskModifiers(riskLevel, state)
        val timeGuidance = getTimeGuidance(careWindowRemainingMs)
        
        return buildString {
            append(baseGuidance)
            if (riskModifiers.isNotEmpty()) {
                append(" • ")
                append(riskModifiers)
            }
            if (timeGuidance.isNotEmpty()) {
                append(" • ")
                append(timeGuidance)
            }
        }
    }
    
    private fun getBaseGuidanceForState(state: MoltState): String {
        return when (state) {
            MoltState.NONE -> "Monitor normally, maintain stable conditions"
            
            MoltState.PREMOLT -> "Dim lighting, increase humidity, provide isolation substrate"
            
            MoltState.ECDYSIS -> "DO NOT DISTURB - Isolate completely, suspend feeding, maintain aeration"
            
            MoltState.POSTMOLT_RISK -> "Maintain isolation, dim lights, no handling or feeding"
            
            MoltState.POSTMOLT_SAFE -> "Continue isolation, offer calcium sources, monitor shell hardening"
        }
    }
    
    private fun getRiskModifiers(riskLevel: AlertSeverity, state: MoltState): String {
        return when (riskLevel) {
            AlertSeverity.INFO -> ""
            
            AlertSeverity.WARNING -> when (state) {
                MoltState.NONE -> "Check water quality"
                MoltState.PREMOLT -> "Monitor closely for state change"
                MoltState.ECDYSIS -> "Increase monitoring frequency"
                MoltState.POSTMOLT_RISK -> "Extra vigilance required"
                MoltState.POSTMOLT_SAFE -> "Monitor for complications"
            }
            
            AlertSeverity.CRITICAL -> when (state) {
                MoltState.NONE -> "URGENT: Address water quality immediately"
                MoltState.PREMOLT -> "URGENT: Prepare isolation immediately"
                MoltState.ECDYSIS -> "CRITICAL: Absolute isolation essential"
                MoltState.POSTMOLT_RISK -> "CRITICAL: Maximum protection required"
                MoltState.POSTMOLT_SAFE -> "URGENT: Check for molt complications"
            }
        }
    }
    
    private fun getTimeGuidance(careWindowRemainingMs: Long?): String {
        careWindowRemainingMs ?: return ""
        
        val remainingHours = careWindowRemainingMs / (1000 * 60 * 60)
        val remainingMinutes = (careWindowRemainingMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            careWindowRemainingMs <= 0 -> "Care window expired"
            remainingHours >= 1 -> "${remainingHours}h ${remainingMinutes}m remaining"
            remainingMinutes >= 1 -> "${remainingMinutes}m remaining"
            else -> "< 1m remaining"
        }
    }
    
    /**
     * Gets emergency action guidance for critical situations
     * 
     * @param state Current molt state
     * @return Emergency action string for immediate operator response
     */
    fun getEmergencyGuidance(state: MoltState): String {
        return when (state) {
            MoltState.NONE -> "Stabilize water parameters immediately"
            
            MoltState.PREMOLT -> "Prepare isolation chamber now - molting imminent"
            
            MoltState.ECDYSIS -> "EMERGENCY: Complete isolation required - no disturbance whatsoever"
            
            MoltState.POSTMOLT_RISK -> "EMERGENCY: Crab extremely vulnerable - maintain absolute isolation"
            
            MoltState.POSTMOLT_SAFE -> "Check for stuck molt or injury - may need intervention"
        }
    }
    
    /**
     * Gets specific care actions for the current state
     * 
     * @param state Current molt state
     * @return List of specific actionable care steps
     */
    fun getCareActions(state: MoltState): List<String> {
        return when (state) {
            MoltState.NONE -> listOf(
                "Maintain stable water parameters",
                "Provide varied diet with calcium",
                "Monitor for pre-molt signs"
            )
            
            MoltState.PREMOLT -> listOf(
                "Reduce lighting to minimum",
                "Increase humidity to 80-90%",
                "Provide deep substrate for digging",
                "Remove other crabs from vicinity",
                "Stop handling completely"
            )
            
            MoltState.ECDYSIS -> listOf(
                "Absolute isolation - no disturbance",
                "Maintain optimal temperature (75-80°F)",
                "Ensure adequate aeration without direct flow",
                "Do not feed",
                "Monitor from distance only"
            )
            
            MoltState.POSTMOLT_RISK -> listOf(
                "Continue complete isolation",
                "Keep lighting very dim",
                "No feeding for first 24-48 hours",
                "Avoid any vibrations or noise",
                "Check aeration is gentle"
            )
            
            MoltState.POSTMOLT_SAFE -> listOf(
                "Maintain isolation from other crabs",
                "Offer calcium-rich foods",
                "Monitor shell hardening progress",
                "Gradually increase normal lighting",
                "Watch for signs of incomplete molt"
            )
        }
    }
}