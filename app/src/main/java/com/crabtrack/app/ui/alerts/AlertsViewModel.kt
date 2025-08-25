package com.crabtrack.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    // Direct access to repository alerts flow
    val alerts = telemetryRepository.readingsWithAlerts.map { it.second }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val alertsFromFlow: StateFlow<List<Alert>> = _uiState.map { it.alerts }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        startAlertCollection()
    }

    private fun startAlertCollection() {
        viewModelScope.launch {
            // Use the new repository flow for all alerts
            telemetryRepository.allAlerts
                .scan(emptyList<Alert>()) { accumulated, newAlerts ->
                    (accumulated + newAlerts)
                        .distinctBy { it.id }
                        .sortedByDescending { it.timestampMs }
                        .take(50)
                }
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load alerts: ${exception.message}"
                    )
                }
                .collect { alertsList ->
                    _uiState.value = AlertsUiState(
                        alerts = alertsList,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    fun refreshAlerts() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearAllAlerts() {
        _uiState.value = _uiState.value.copy(alerts = emptyList())
    }
}