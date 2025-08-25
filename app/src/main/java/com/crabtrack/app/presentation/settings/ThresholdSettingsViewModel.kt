package com.crabtrack.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import com.crabtrack.app.data.repository.ThresholdRepository
import com.crabtrack.app.domain.usecase.UpdateThresholdsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThresholdSettingsUiState(
    val thresholds: Map<SensorType, Threshold> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val editingThreshold: Threshold? = null
)

@HiltViewModel
class ThresholdSettingsViewModel @Inject constructor(
    private val thresholdRepository: ThresholdRepository,
    private val updateThresholdsUseCase: UpdateThresholdsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThresholdSettingsUiState())
    val uiState: StateFlow<ThresholdSettingsUiState> = _uiState.asStateFlow()

    init {
        loadThresholds()
    }

    private fun loadThresholds() {
        viewModelScope.launch {
            thresholdRepository.getAllThresholds()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load thresholds: ${exception.message}"
                    )
                }
                .collect { thresholds ->
                    _uiState.value = _uiState.value.copy(
                        thresholds = thresholds,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    fun startEditingThreshold(sensorType: SensorType) {
        val threshold = _uiState.value.thresholds[sensorType]
        _uiState.value = _uiState.value.copy(
            editingThreshold = threshold,
            errorMessage = null,
            successMessage = null
        )
    }

    fun updateEditingThreshold(threshold: Threshold) {
        _uiState.value = _uiState.value.copy(editingThreshold = threshold)
    }

    fun saveThreshold(threshold: Threshold) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                // Validate threshold values
                val validationError = validateThreshold(threshold)
                if (validationError != null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = validationError
                    )
                    return@launch
                }
                
                updateThresholdsUseCase(threshold)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    editingThreshold = null,
                    successMessage = "Threshold updated successfully",
                    errorMessage = null
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to save threshold: ${exception.message}"
                )
            }
        }
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            editingThreshold = null,
            errorMessage = null,
            successMessage = null
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    private fun validateThreshold(threshold: Threshold): String? {
        // Validate that critical values are more restrictive than warning values
        val warningMin = threshold.warningMin
        val warningMax = threshold.warningMax
        val criticalMin = threshold.criticalMin
        val criticalMax = threshold.criticalMax

        if (warningMin != null && warningMax != null && warningMin >= warningMax) {
            return "Warning minimum must be less than warning maximum"
        }

        if (criticalMin != null && criticalMax != null && criticalMin >= criticalMax) {
            return "Critical minimum must be less than critical maximum"
        }

        if (warningMin != null && criticalMin != null && warningMin <= criticalMin) {
            return "Warning minimum must be greater than critical minimum"
        }

        if (warningMax != null && criticalMax != null && warningMax >= criticalMax) {
            return "Warning maximum must be less than critical maximum"
        }

        return null
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                val defaultThresholds = SensorType.values().map { sensorType ->
                    com.crabtrack.app.data.local.datastore.ThresholdPreferences.getDefaultThreshold(sensorType)
                }
                
                updateThresholdsUseCase(defaultThresholds)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "All thresholds reset to defaults",
                    errorMessage = null
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to reset thresholds: ${exception.message}"
                )
            }
        }
    }
}