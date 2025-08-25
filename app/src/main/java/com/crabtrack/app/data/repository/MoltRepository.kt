package com.crabtrack.app.data.repository

import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.molt.MoltSource
import com.crabtrack.app.domain.usecase.EvaluateMoltRiskUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing molt-related data streams and state.
 * 
 * Composes data from MoltSource and evaluates risk levels using domain logic,
 * providing reactive state flows for UI consumption.
 * 
 * @param moltSource Source of molt state and event data
 * @param evaluateMoltRiskUseCase Use case for evaluating molt risk levels
 * @param coroutineScope Scope for managing flow subscriptions
 */
@Singleton
class MoltRepository @Inject constructor(
    private val moltSource: MoltSource,
    private val evaluateMoltRiskUseCase: EvaluateMoltRiskUseCase,
    private val coroutineScope: CoroutineScope
) {
    
    // Internal state flows
    private val _currentState = MutableStateFlow(MoltState.NONE)
    private val _riskLevel = MutableStateFlow(AlertSeverity.INFO)
    private val _careWindowRemaining = MutableStateFlow<Long?>(null)
    private val _currentEvent = MutableStateFlow<MoltEvent?>(null)
    private val _latestWaterReading = MutableStateFlow<WaterReading?>(null)
    
    // Public state flows
    val currentState: StateFlow<MoltState> = _currentState.asStateFlow()
    val riskLevel: StateFlow<AlertSeverity> = _riskLevel.asStateFlow()
    val careWindowRemaining: StateFlow<Long?> = _careWindowRemaining.asStateFlow()
    val currentEvent: StateFlow<MoltEvent?> = _currentEvent.asStateFlow()
    
    // Public event flow (direct from source)
    val events: Flow<MoltEvent> get() = moltSource.streamEvents(_activeTankId)
    
    private var _activeTankId: String = ""
    
    init {
        // Initialize with default tank if needed
        // This would typically be injected or configured elsewhere
    }
    
    /**
     * Starts monitoring molt data for a specific tank
     * 
     * @param tankId ID of the tank to monitor
     */
    fun startMonitoring(tankId: String) {
        _activeTankId = tankId
        
        // Subscribe to molt states
        moltSource.streamStates(tankId)
            .onEach { state ->
                _currentState.value = state
                evaluateRisk()
            }
            .launchIn(coroutineScope)
        
        // Subscribe to molt events
        moltSource.streamEvents(tankId)
            .onEach { event ->
                // Update current event if it's for the current state
                if (event.state == _currentState.value && event.endedAtMs == null) {
                    _currentEvent.value = event
                } else if (event.endedAtMs != null && event.state == _currentEvent.value?.state) {
                    // Clear current event when it ends
                    _currentEvent.value = null
                }
                evaluateRisk()
            }
            .launchIn(coroutineScope)
    }
    
    /**
     * Updates the latest water reading for risk evaluation
     * 
     * @param waterReading Latest water quality reading
     */
    fun updateWaterReading(waterReading: WaterReading) {
        _latestWaterReading.value = waterReading
        evaluateRisk()
    }
    
    /**
     * Gets current molt state for a specific tank
     * 
     * @param tankId ID of the tank to query
     * @return Flow of molt states for the specified tank
     */
    fun getMoltStates(tankId: String): Flow<MoltState> {
        return moltSource.streamStates(tankId)
    }
    
    /**
     * Gets molt events for a specific tank
     * 
     * @param tankId ID of the tank to query
     * @return Flow of molt events for the specified tank
     */
    fun getMoltEvents(tankId: String): Flow<MoltEvent> {
        return moltSource.streamEvents(tankId)
    }
    
    /**
     * Gets combined risk assessment for a specific tank
     * 
     * @param tankId ID of the tank to assess
     * @param waterReadingFlow Flow of water readings for the tank
     * @return Flow of risk assessment results
     */
    fun getRiskAssessment(
        tankId: String,
        waterReadingFlow: Flow<WaterReading?>
    ): Flow<EvaluateMoltRiskUseCase.MoltRiskResult> {
        return combine(
            moltSource.streamStates(tankId),
            moltSource.streamEvents(tankId),
            waterReadingFlow
        ) { state, event, waterReading ->
            val currentEvent = if (event.state == state && event.endedAtMs == null) event else null
            evaluateMoltRiskUseCase(
                state = state,
                event = currentEvent,
                nowMs = System.currentTimeMillis(),
                waterReading = waterReading
            )
        }
    }
    
    /**
     * Stops monitoring the current tank
     */
    fun stopMonitoring() {
        _currentState.value = MoltState.NONE
        _riskLevel.value = AlertSeverity.INFO
        _careWindowRemaining.value = null
        _currentEvent.value = null
        _activeTankId = ""
    }
    
    private fun evaluateRisk() {
        if (getActiveTankId().isEmpty()) return
        
        val riskResult = evaluateMoltRiskUseCase(
            state = _currentState.value,
            event = _currentEvent.value,
            nowMs = System.currentTimeMillis(),
            waterReading = _latestWaterReading.value
        )
        
        _riskLevel.value = riskResult.riskLevel
        _careWindowRemaining.value = riskResult.careWindowRemainingMs
    }
    
    /**
     * Forces a risk evaluation update (useful for periodic updates)
     */
    fun updateRiskEvaluation() {
        evaluateRisk()
    }
    
    /**
     * Gets the current risk level synchronously
     */
    fun getCurrentRiskLevel(): AlertSeverity = _riskLevel.value
    
    /**
     * Gets the current care window remaining synchronously
     */
    fun getCurrentCareWindowRemaining(): Long? = _careWindowRemaining.value
    
    /**
     * Checks if there's an active molt event
     */
    fun hasActiveMolt(): Boolean {
        return _currentEvent.value != null && _currentState.value != MoltState.NONE
    }
    
    /**
     * Gets the active tank ID being monitored
     */
    fun getActiveTankId(): String = _activeTankId
}