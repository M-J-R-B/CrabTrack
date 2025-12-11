package com.crabtrack.app.ui.molting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.data.repository.MoltingAlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoltingUiState(
    val moltingAlerts: List<MoltingAlert> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

sealed class MoltingNavigationEvent {
    data class NavigateToCamera(val tankId: String) : MoltingNavigationEvent()
}

@HiltViewModel
class MoltingViewModel @Inject constructor(
    private val moltingAlertRepository: MoltingAlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoltingUiState())
    val uiState: StateFlow<MoltingUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MoltingNavigationEvent>()
    val navigationEvents: SharedFlow<MoltingNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        android.util.Log.i("MoltingViewModel", "=== MOLTING VIEWMODEL INITIALIZED ===")
        startMoltingAlertMonitoring()
        collectMoltingAlerts()
    }

    /**
     * Starts monitoring global /molting_alerts and copying high-confidence alerts to user path.
     */
    private fun startMoltingAlertMonitoring() {
        moltingAlertRepository.startMonitoring()
        android.util.Log.i("MoltingViewModel", "Started molting alert monitoring")
    }

    /**
     * Collects molting alerts from user's path and updates UI state.
     */
    private fun collectMoltingAlerts() {
        viewModelScope.launch {
            moltingAlertRepository.userAlerts
                .catch { exception ->
                    android.util.Log.e("MoltingViewModel", "Error collecting molting alerts: ${exception.message}", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load molting alerts: ${exception.message}"
                    )
                }
                .collect { alerts ->
                    android.util.Log.i("MoltingViewModel", "Molting alerts received: ${alerts.size}")
                    _uiState.value = _uiState.value.copy(
                        moltingAlerts = alerts,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    /**
     * Called when a molting alert card is clicked.
     * Marks the alert as read (dialog will handle navigation separately).
     */
    fun onMoltingAlertClicked(alert: MoltingAlert) {
        viewModelScope.launch {
            // Mark alert as read
            moltingAlertRepository.markAsRead(alert.id)
            android.util.Log.i("MoltingViewModel", "Molting alert clicked and marked as read: ${alert.id}")
        }
    }

    /**
     * Called when user taps "Go to Tank" in the detail dialog.
     * Emits navigation event to camera view.
     */
    fun navigateToTank(tankId: String) {
        viewModelScope.launch {
            _navigationEvents.emit(MoltingNavigationEvent.NavigateToCamera(tankId))
            android.util.Log.i("MoltingViewModel", "Navigating to tank: $tankId")
        }
    }

    /**
     * Dismisses a molting alert.
     * - Removes alert from user's path
     * - Resets threshold so new detections can trigger alerts again
     * - Logs feedback if marked as false alarm
     */
    fun dismissMoltingAlert(alert: MoltingAlert, isFalseAlarm: Boolean) {
        viewModelScope.launch {
            moltingAlertRepository.dismissAsFalseAlarm(alert, isFalseAlarm)
            android.util.Log.i("MoltingViewModel", "Dismissed molting alert: ${alert.id}, falseAlarm=$isFalseAlarm")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refreshAlerts() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        // The alerts flow will automatically update
    }
}
