package com.crabtrack.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.domain.usecase.EvaluateThresholdsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

sealed class AlertEvent {
    data class ShowAlert(val message: String) : AlertEvent()
    object NavigateToAlerts : AlertEvent()
}

data class DashboardUiState(
    val latestReading: WaterReading? = null,
    val overallSeverity: AlertSeverity = AlertSeverity.INFO,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val evaluateThresholdsUseCase: EvaluateThresholdsUseCase,
    private val thresholdsFlow: Flow<Thresholds>
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Direct access to repository flows for better performance
    val telemetryWithAlerts = telemetryRepository.readingsWithAlerts

    val latestReading: StateFlow<WaterReading?> = _uiState.map { it.latestReading }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val overallSeverity: StateFlow<AlertSeverity> = _uiState.map { it.overallSeverity }
        .stateIn(viewModelScope, SharingStarted.Lazily, AlertSeverity.INFO)

    // Alert events for UI interactions
    private val _alertEvents = MutableStateFlow<AlertEvent?>(null)
    val alertEvents: StateFlow<AlertEvent?> = _alertEvents.asStateFlow()

    private var currentThresholds: Thresholds? = null

    private fun startTelemetryCollection() {
        viewModelScope.launch {
            // Use the new repository flow directly
            telemetryRepository.readingsWithAlerts
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load telemetry data: ${exception.message}"
                    )
                }
                .collect { (reading, alerts) ->
                    val severity = alerts.maxByOrNull { it.severity.ordinal }?.severity ?: AlertSeverity.INFO
                    _uiState.value = DashboardUiState(
                        latestReading = reading,
                        overallSeverity = severity,
                        isLoading = false,
                        errorMessage = null
                    )
                    
                    // Handle critical alerts
                    val criticalAlerts = alerts.filter { it.severity == AlertSeverity.CRITICAL }
                    if (criticalAlerts.isNotEmpty()) {
                        _alertEvents.value = AlertEvent.ShowAlert(criticalAlerts.first().message)
                    }
                }
        }
    }

    fun refreshData() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    init {
        startTelemetryCollection()
        // Keep track of current thresholds for parameter evaluation
        viewModelScope.launch {
            thresholdsFlow.collect { thresholds ->
                currentThresholds = thresholds
            }
        }
    }
    
    fun getParameterSeverity(parameter: String): AlertSeverity {
        val reading = _uiState.value.latestReading ?: return AlertSeverity.INFO
        val thresholds = currentThresholds ?: return AlertSeverity.INFO
        val alerts = evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
        return alerts.find { it.parameter == parameter }?.severity ?: AlertSeverity.INFO
    }
}