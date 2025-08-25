package com.crabtrack.app.ui.molting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.data.repository.MoltRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MoltingUiEvent {
    data class ShowStateDetails(val state: MoltState, val details: String) : MoltingUiEvent()
    data class ShowRiskActions(val riskLevel: AlertSeverity, val actions: List<String>) : MoltingUiEvent()
    object NavigateToGuidance : MoltingUiEvent()
}

data class CriticalMoltAlert(
    val id: String,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean = false
)

/**
 * ViewModel for the Molting screen.
 * 
 * Collects molt data from MoltRepository and exposes UI state including
 * current molt state, risk level, care window countdown, and recent events.
 */
@HiltViewModel
class MoltingViewModel @Inject constructor(
    private val moltRepository: MoltRepository
) : ViewModel() {
    
    // Expose repository state flows directly
    val state: StateFlow<MoltState> = moltRepository.currentState
    val riskLevel: StateFlow<AlertSeverity> = moltRepository.riskLevel
    val careWindowRemaining: StateFlow<Long?> = moltRepository.careWindowRemaining
    
    // Simplified molting status for basic implementation
    val moltingStatus = moltRepository.currentState
    
    // Recent events state (keep last 20)
    private val _recentEvents = MutableStateFlow<List<MoltEvent>>(emptyList())
    val recentEvents: StateFlow<List<MoltEvent>> = _recentEvents.asStateFlow()
    
    // Formatted care window state
    private val _careWindowFormatted = MutableStateFlow<String?>(null)
    val careWindowFormatted: StateFlow<String?> = _careWindowFormatted.asStateFlow()
    
    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // UI events for interactions
    private val _uiEvents = MutableStateFlow<MoltingUiEvent?>(null)
    val uiEvents: StateFlow<MoltingUiEvent?> = _uiEvents.asStateFlow()
    
    // Critical alerts for high priority notifications
    private val _criticalAlerts = MutableStateFlow<CriticalMoltAlert?>(null)
    val criticalAlerts: StateFlow<CriticalMoltAlert?> = _criticalAlerts.asStateFlow()
    
    init {
        startMonitoring()
        observeCareWindow()
        observeMoltEvents()
    }
    
    private fun startMonitoring() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Start monitoring with default tank ID
                // In a real app, this would come from user selection or app state
                moltRepository.startMonitoring("tank_001")
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start molt monitoring: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    private fun observeCareWindow() {
        careWindowRemaining
            .onEach { remainingMs ->
                _careWindowFormatted.value = formatCareWindow(remainingMs)
            }
            .launchIn(viewModelScope)
    }
    
    private fun observeMoltEvents() {
        moltRepository.events
            .onEach { event ->
                val currentEvents = _recentEvents.value.toMutableList()
                currentEvents.add(0, event) // Add to beginning
                
                // Keep only last 20 events
                if (currentEvents.size > 20) {
                    currentEvents.removeAt(currentEvents.size - 1)
                }
                
                _recentEvents.value = currentEvents
            }
            .launchIn(viewModelScope)
    }
    
    private fun formatCareWindow(remainingMs: Long?): String? {
        remainingMs ?: return null
        
        if (remainingMs <= 0) return "00:00:00"
        
        val hours = remainingMs / (1000 * 60 * 60)
        val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (remainingMs % (1000 * 60)) / 1000
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Gets the display name for a molt state
     */
    fun getMoltStateDisplayName(state: MoltState): String {
        return when (state) {
            MoltState.NONE -> "None"
            MoltState.PREMOLT -> "Pre-molt"
            MoltState.ECDYSIS -> "Ecdysis"
            MoltState.POSTMOLT_RISK -> "Post-molt Risk"
            MoltState.POSTMOLT_SAFE -> "Post-molt Safe"
        }
    }
    
    /**
     * Gets the display name for a risk level
     */
    fun getRiskLevelDisplayName(severity: AlertSeverity): String {
        return when (severity) {
            AlertSeverity.INFO -> "Normal"
            AlertSeverity.WARNING -> "Warning"
            AlertSeverity.CRITICAL -> "Critical"
        }
    }
    
    /**
     * Gets the color resource for a molt state badge
     */
    fun getMoltStateColor(state: MoltState): Int {
        return when (state) {
            MoltState.NONE -> android.R.color.holo_green_light
            MoltState.PREMOLT -> android.R.color.holo_orange_light
            MoltState.ECDYSIS -> android.R.color.holo_red_dark
            MoltState.POSTMOLT_RISK -> android.R.color.holo_red_light
            MoltState.POSTMOLT_SAFE -> android.R.color.holo_blue_light
        }
    }
    
    /**
     * Gets the color resource for a risk level badge
     */
    fun getRiskLevelColor(severity: AlertSeverity): Int {
        return when (severity) {
            AlertSeverity.INFO -> android.R.color.holo_green_light
            AlertSeverity.WARNING -> android.R.color.holo_orange_light
            AlertSeverity.CRITICAL -> android.R.color.holo_red_dark
        }
    }
    
    /**
     * Formats confidence as percentage
     */
    fun formatConfidence(confidence: Double): String {
        return "${(confidence * 100).toInt()}%"
    }
    
    /**
     * Formats timestamp for event display
     */
    fun formatEventTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestampMs
        
        return when {
            diffMs < 60_000 -> "Just now"
            diffMs < 3600_000 -> "${diffMs / 60_000}m ago"
            diffMs < 86400_000 -> "${diffMs / 3600_000}h ago"
            else -> "${diffMs / 86400_000}d ago"
        }
    }
    
    /**
     * Clears any error messages
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Refreshes molt monitoring
     */
    fun refresh() {
        moltRepository.updateRiskEvaluation()
    }
    
    /**
     * Shows detailed information about the current molt state
     */
    fun showStateDetails() {
        val currentState = state.value
        val details = when (currentState) {
            MoltState.ECDYSIS -> "The crab is currently molting. This is a critical period - do not disturb or handle the crab."
            MoltState.POSTMOLT_RISK -> "The crab has recently molted and is vulnerable. Monitor closely for signs of distress."
            MoltState.PREMOLT -> "The crab is preparing to molt. Ensure stable water conditions and avoid stress."
            else -> "The crab is in a normal state."
        }
        _uiEvents.value = MoltingUiEvent.ShowStateDetails(currentState, details)
    }
    
    /**
     * Shows recommended actions for current risk level
     */
    fun showRiskActions() {
        val currentRisk = riskLevel.value
        val actions = when (currentRisk) {
            AlertSeverity.CRITICAL -> listOf(
                "Do not disturb the crab",
                "Maintain stable water conditions",
                "Remove tank mates if present",
                "Monitor constantly"
            )
            AlertSeverity.WARNING -> listOf(
                "Check water parameters",
                "Ensure adequate hiding places",
                "Reduce handling to minimum",
                "Monitor closely"
            )
            else -> listOf(
                "Continue regular care routine",
                "Monitor for changes"
            )
        }
        _uiEvents.value = MoltingUiEvent.ShowRiskActions(currentRisk, actions)
    }
    
    /**
     * Acknowledges a critical molt alert
     */
    fun acknowledgeCriticalAlert(alertId: String) {
        val currentAlert = _criticalAlerts.value
        if (currentAlert?.id == alertId) {
            _criticalAlerts.value = currentAlert.copy(acknowledged = true)
        }
    }
    
    /**
     * Starts real-time updates (for lifecycle management)
     */
    fun startRealTimeUpdates() {
        // Implementation would depend on the repository's capabilities
        // For now, just refresh the data
        refresh()
    }
    
    /**
     * Stops real-time updates (for lifecycle management)
     */
    fun stopRealTimeUpdates() {
        // Stop any real-time update jobs if needed
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        _uiEvents.value = null
        _criticalAlerts.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        moltRepository.stopMonitoring()
        cleanup()
    }
}