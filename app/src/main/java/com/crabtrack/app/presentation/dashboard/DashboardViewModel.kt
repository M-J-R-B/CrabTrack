package com.crabtrack.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.CleaningCardStatus
import com.crabtrack.app.data.model.FarmCleaningStatus
import com.crabtrack.app.data.model.FeedingCardStatus
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.data.model.TankFeedingStatus
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.repository.CleaningReminderRepository
import com.crabtrack.app.data.repository.FeedingStatusRepository
import com.crabtrack.app.data.repository.MoltingAlertRepository
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
    val moltingAlerts: List<MoltingAlert> = emptyList(),
    val hasMoltingAlerts: Boolean = false,
    val feedingStatuses: List<TankFeedingStatus> = emptyList(),
    val hasFeedingAlerts: Boolean = false,
    val cleaningStatus: FarmCleaningStatus? = null,
    val hasCleaningAlerts: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val telemetryRepository: TelemetryRepository,
    private val moltingAlertRepository: MoltingAlertRepository,
    private val feedingStatusRepository: FeedingStatusRepository,
    private val cleaningReminderRepository: CleaningReminderRepository,
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
        android.util.Log.i("DashboardViewModel", "=== STARTING TELEMETRY COLLECTION ===")
        viewModelScope.launch {
            android.util.Log.i("DashboardViewModel", "Collecting from telemetryRepository.readingsWithAlerts")
            // Use the new repository flow directly
            telemetryRepository.readingsWithAlerts
                .catch { exception ->
                    android.util.Log.e("DashboardViewModel", "✗ Error in telemetry flow: ${exception.message}", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load telemetry data: ${exception.message}"
                    )
                }
                .collect { (reading, alerts) ->
                    android.util.Log.i("DashboardViewModel", "=== RECEIVED READING ===")
                    android.util.Log.i("DashboardViewModel", "Reading: pH=${reading.pH}, temp=${reading.temperatureC}°C, alerts=${alerts.size}")

                    val severity = alerts.maxByOrNull { it.severity.ordinal }?.severity ?: AlertSeverity.INFO
                    _uiState.value = _uiState.value.copy(
                        latestReading = reading,
                        overallSeverity = severity,
                        isLoading = false,
                        errorMessage = null
                    )

                    android.util.Log.i("DashboardViewModel", "✓ UI state updated with reading")

                    // Handle critical alerts
                    val criticalAlerts = alerts.filter { it.severity == AlertSeverity.CRITICAL }
                    if (criticalAlerts.isNotEmpty()) {
                        _alertEvents.value = AlertEvent.ShowAlert(criticalAlerts.first().message)
                    }
                }
        }
    }

    fun refreshData() {
        // Don't set loading state - the stream is already running
        // Just clear any error messages
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    init {
        android.util.Log.i("DashboardViewModel", "=== DASHBOARD VIEWMODEL INITIALIZED ===")
        startTelemetryCollection()
        startMoltingAlertMonitoring()
        collectMoltingAlerts()
        collectFeedingStatuses()
        collectCleaningStatus()
        // Keep track of current thresholds for parameter evaluation
        viewModelScope.launch {
            android.util.Log.i("DashboardViewModel", "Collecting thresholds flow")
            thresholdsFlow.collect { thresholds ->
                android.util.Log.d("DashboardViewModel", "Thresholds received: pH=${thresholds.pHMin}-${thresholds.pHMax}")
                currentThresholds = thresholds
            }
        }
    }

    /**
     * Starts monitoring global /molting_alerts and copying high-confidence alerts to user path.
     */
    private fun startMoltingAlertMonitoring() {
        moltingAlertRepository.startMonitoring()
        android.util.Log.i("DashboardViewModel", "Started molting alert monitoring")
    }

    /**
     * Collects molting alerts from user's path and updates UI state.
     */
    private fun collectMoltingAlerts() {
        viewModelScope.launch {
            moltingAlertRepository.userAlerts
                .catch { exception ->
                    android.util.Log.e("DashboardViewModel", "Error collecting molting alerts: ${exception.message}", exception)
                }
                .collect { alerts ->
                    android.util.Log.i("DashboardViewModel", "Molting alerts received: ${alerts.size}")
                    _uiState.value = _uiState.value.copy(
                        moltingAlerts = alerts,
                        hasMoltingAlerts = alerts.isNotEmpty()
                    )
                }
        }
    }

    fun getParameterSeverity(parameter: String): AlertSeverity {
        val reading = _uiState.value.latestReading ?: return AlertSeverity.INFO
        val thresholds = currentThresholds ?: return AlertSeverity.INFO
        val alerts = evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
        return alerts.find { it.parameter == parameter }?.severity ?: AlertSeverity.INFO
    }

    /**
     * Collects feeding statuses for all tanks and updates UI state.
     */
    private fun collectFeedingStatuses() {
        viewModelScope.launch {
            feedingStatusRepository.allTankFeedingStatuses
                .catch { exception ->
                    android.util.Log.e("DashboardViewModel", "Error collecting feeding statuses: ${exception.message}", exception)
                }
                .collect { statuses ->
                    val hasAlerts = statuses.any { it.overallStatus == FeedingCardStatus.OVERDUE }
                    _uiState.value = _uiState.value.copy(
                        feedingStatuses = statuses,
                        hasFeedingAlerts = hasAlerts
                    )
                    android.util.Log.i("DashboardViewModel", "Feeding statuses updated: ${statuses.size} tanks, hasAlerts=$hasAlerts")
                }
        }
    }

    /**
     * Confirms a feeding for a specific tank.
     */
    fun confirmFeeding(tankId: String, feedingLogId: String) {
        viewModelScope.launch {
            feedingStatusRepository.confirmFeeding(tankId, feedingLogId)
                .onSuccess {
                    android.util.Log.i("DashboardViewModel", "Feeding confirmed: $feedingLogId for tank $tankId")
                }
                .onFailure { e ->
                    android.util.Log.e("DashboardViewModel", "Failed to confirm feeding: ${e.message}", e)
                }
        }
    }

    /**
     * Called when a feeding card is clicked (for schedule configuration).
     */
    fun onFeedingCardClicked(tankId: String) {
        android.util.Log.i("DashboardViewModel", "Feeding card clicked for tank: $tankId")
        // TODO: Navigate to feeding schedule configuration screen
    }

    /**
     * Collects cleaning status and updates UI state.
     */
    private fun collectCleaningStatus() {
        viewModelScope.launch {
            cleaningReminderRepository.farmCleaningStatus
                .catch { exception ->
                    android.util.Log.e("DashboardViewModel", "Error collecting cleaning status: ${exception.message}", exception)
                }
                .collect { status ->
                    val hasAlerts = status?.overallStatus == CleaningCardStatus.OVERDUE
                    _uiState.value = _uiState.value.copy(
                        cleaningStatus = status,
                        hasCleaningAlerts = hasAlerts
                    )
                    android.util.Log.i("DashboardViewModel", "Cleaning status updated: ${status?.todayLogs?.size ?: 0} logs, hasAlerts=$hasAlerts")
                }
        }
    }

    /**
     * Confirms a cleaning (marks as complete).
     */
    fun confirmCleaning(cleaningLogId: String) {
        viewModelScope.launch {
            cleaningReminderRepository.confirmCleaning(cleaningLogId)
                .onSuccess {
                    android.util.Log.i("DashboardViewModel", "Cleaning confirmed: $cleaningLogId")
                }
                .onFailure { e ->
                    android.util.Log.e("DashboardViewModel", "Failed to confirm cleaning: ${e.message}", e)
                }
        }
    }
}