package com.crabtrack.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.local.ThresholdsStore
import com.crabtrack.app.data.model.Thresholds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SaveState {
    Idle, Saving, Success, Error
}

data class SettingsUiState(
    val thresholds: Thresholds? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val thresholdsStore: ThresholdsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // StateFlows for individual form fields (for real-time validation)
    private val _phMin = MutableStateFlow("")
    private val _phMax = MutableStateFlow("")
    private val _doMin = MutableStateFlow("")
    private val _salinityMin = MutableStateFlow("")
    private val _salinityMax = MutableStateFlow("")
    private val _ammoniaMax = MutableStateFlow("")
    private val _tempMin = MutableStateFlow("")
    private val _tempMax = MutableStateFlow("")
    private val _levelMin = MutableStateFlow("")
    private val _levelMax = MutableStateFlow("")
    
    // Save state for UI feedback
    private val _saveState = MutableStateFlow(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    // Real-time validation errors
    val validationErrors = combine(
        _phMin, _phMax, _doMin, _salinityMin, _salinityMax, 
        _ammoniaMax, _tempMin, _tempMax, _levelMin, _levelMax
    ) { values ->
        validateFormFields(values[0], values[1], values[2], values[3], values[4], 
                          values[5], values[6], values[7], values[8], values[9])
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )
    
    // Form validity check
    val formValid = combine(
        _phMin, _phMax, _doMin, _salinityMin, _salinityMax,
        _ammoniaMax, _tempMin, _tempMax, _levelMin, _levelMax,
        validationErrors
    ) { values ->
        val errors = values.last() as Map<String, String>
        val fieldValues = values.dropLast(1)
        
        val allFieldsValid = fieldValues.all { value ->
            value is String && value.isNotEmpty() && (value as String).toDoubleOrNull() != null
        }
        
        allFieldsValid && errors.isEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        loadThresholds()
    }

    private fun loadThresholds() {
        viewModelScope.launch {
            thresholdsStore.getThresholds()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load settings: ${exception.message}"
                    )
                }
                .collect { thresholds ->
                    _uiState.value = _uiState.value.copy(
                        thresholds = thresholds,
                        isLoading = false,
                        errorMessage = null
                    )
                    // Populate form fields with loaded values
                    populateFormFields(thresholds)
                }
        }
    }
    
    private fun populateFormFields(thresholds: Thresholds) {
        _phMin.value = thresholds.pHMin.toString()
        _phMax.value = thresholds.pHMax.toString()
        _doMin.value = thresholds.doMin.toString()
        _salinityMin.value = thresholds.salinityMin.toString()
        _salinityMax.value = thresholds.salinityMax.toString()
        _ammoniaMax.value = thresholds.ammoniaMax.toString()
        _tempMin.value = thresholds.tempMin.toString()
        _tempMax.value = thresholds.tempMax.toString()
        _levelMin.value = thresholds.levelMin.toString()
        _levelMax.value = thresholds.levelMax.toString()
    }
    
    // Update methods for real-time StateFlow binding
    fun updatePhMin(value: String) { _phMin.value = value }
    fun updatePhMax(value: String) { _phMax.value = value }
    fun updateDoMin(value: String) { _doMin.value = value }
    fun updateSalinityMin(value: String) { _salinityMin.value = value }
    fun updateSalinityMax(value: String) { _salinityMax.value = value }
    fun updateAmmoniaMax(value: String) { _ammoniaMax.value = value }
    fun updateTempMin(value: String) { _tempMin.value = value }
    fun updateTempMax(value: String) { _tempMax.value = value }
    fun updateLevelMin(value: String) { _levelMin.value = value }
    fun updateLevelMax(value: String) { _levelMax.value = value }

    fun saveThresholds() {
        if (!formValid.value) {
            _saveState.value = SaveState.Error
            return
        }
        
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            _uiState.value = _uiState.value.copy(
                isSaving = true, 
                errorMessage = null,
                validationErrors = emptyMap()
            )
            
            try {
                val thresholds = Thresholds(
                    pHMin = _phMin.value.toDouble(),
                    pHMax = _phMax.value.toDouble(),
                    doMin = _doMin.value.toDouble(),
                    salinityMin = _salinityMin.value.toDouble(),
                    salinityMax = _salinityMax.value.toDouble(),
                    ammoniaMax = _ammoniaMax.value.toDouble(),
                    tempMin = _tempMin.value.toDouble(),
                    tempMax = _tempMax.value.toDouble(),
                    levelMin = _levelMin.value.toDouble(),
                    levelMax = _levelMax.value.toDouble()
                )
                
                val validationErrors = validateThresholds(thresholds)
                if (validationErrors.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        validationErrors = validationErrors
                    )
                    _saveState.value = SaveState.Error
                    return@launch
                }
                
                thresholdsStore.saveThresholds(thresholds)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Settings saved successfully",
                    errorMessage = null,
                    validationErrors = emptyMap()
                )
                _saveState.value = SaveState.Success
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to save settings: ${exception.message}"
                )
                _saveState.value = SaveState.Error
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                thresholdsStore.resetToDefaults()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Settings reset to defaults",
                    errorMessage = null,
                    validationErrors = emptyMap()
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to reset settings: ${exception.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            validationErrors = emptyMap()
        )
    }
    
    private fun validateFormFields(
        phMin: String, phMax: String, doMin: String, 
        salinityMin: String, salinityMax: String, ammoniaMax: String,
        tempMin: String, tempMax: String, levelMin: String, levelMax: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        
        // pH validation
        val phMinDouble = phMin.toDoubleOrNull()
        val phMaxDouble = phMax.toDoubleOrNull()
        if (phMinDouble == null && phMin.isNotEmpty()) {
            errors["phMin"] = "Invalid number"
        }
        if (phMaxDouble == null && phMax.isNotEmpty()) {
            errors["phMax"] = "Invalid number"
        }
        if (phMinDouble != null && phMaxDouble != null && phMinDouble >= phMaxDouble) {
            errors["phMin"] = "Min must be less than max"
        }
        if (phMinDouble != null && (phMinDouble < 0.0 || phMinDouble > 14.0)) {
            errors["phMin"] = "Must be between 0-14"
        }
        if (phMaxDouble != null && (phMaxDouble < 0.0 || phMaxDouble > 14.0)) {
            errors["phMax"] = "Must be between 0-14"
        }
        
        // Dissolved Oxygen validation
        val doMinDouble = doMin.toDoubleOrNull()
        if (doMinDouble == null && doMin.isNotEmpty()) {
            errors["doMin"] = "Invalid number"
        }
        if (doMinDouble != null && doMinDouble < 0.0) {
            errors["doMin"] = "Must be positive"
        }
        
        // Salinity validation
        val salinityMinDouble = salinityMin.toDoubleOrNull()
        val salinityMaxDouble = salinityMax.toDoubleOrNull()
        if (salinityMinDouble == null && salinityMin.isNotEmpty()) {
            errors["salinityMin"] = "Invalid number"
        }
        if (salinityMaxDouble == null && salinityMax.isNotEmpty()) {
            errors["salinityMax"] = "Invalid number"
        }
        if (salinityMinDouble != null && salinityMaxDouble != null && salinityMinDouble >= salinityMaxDouble) {
            errors["salinityMin"] = "Min must be less than max"
        }
        if (salinityMinDouble != null && (salinityMinDouble < 0.0 || salinityMinDouble > 50.0)) {
            errors["salinityMin"] = "Must be between 0-50 ppt"
        }
        if (salinityMaxDouble != null && (salinityMaxDouble < 0.0 || salinityMaxDouble > 50.0)) {
            errors["salinityMax"] = "Must be between 0-50 ppt"
        }
        
        // Ammonia validation
        val ammoniaMaxDouble = ammoniaMax.toDoubleOrNull()
        if (ammoniaMaxDouble == null && ammoniaMax.isNotEmpty()) {
            errors["ammoniaMax"] = "Invalid number"
        }
        if (ammoniaMaxDouble != null && ammoniaMaxDouble < 0.0) {
            errors["ammoniaMax"] = "Must be positive"
        }
        if (ammoniaMaxDouble != null && ammoniaMaxDouble > 10.0) {
            errors["ammoniaMax"] = "Seems too high (>10 mg/L)"
        }
        
        // Temperature validation
        val tempMinDouble = tempMin.toDoubleOrNull()
        val tempMaxDouble = tempMax.toDoubleOrNull()
        if (tempMinDouble == null && tempMin.isNotEmpty()) {
            errors["tempMin"] = "Invalid number"
        }
        if (tempMaxDouble == null && tempMax.isNotEmpty()) {
            errors["tempMax"] = "Invalid number"
        }
        if (tempMinDouble != null && tempMaxDouble != null && tempMinDouble >= tempMaxDouble) {
            errors["tempMin"] = "Min must be less than max"
        }
        if (tempMinDouble != null && (tempMinDouble < 0.0 || tempMinDouble > 50.0)) {
            errors["tempMin"] = "Must be between 0-50°C"
        }
        if (tempMaxDouble != null && (tempMaxDouble < 0.0 || tempMaxDouble > 50.0)) {
            errors["tempMax"] = "Must be between 0-50°C"
        }
        
        // Level validation
        val levelMinDouble = levelMin.toDoubleOrNull()
        val levelMaxDouble = levelMax.toDoubleOrNull()
        if (levelMinDouble == null && levelMin.isNotEmpty()) {
            errors["levelMin"] = "Invalid number"
        }
        if (levelMaxDouble == null && levelMax.isNotEmpty()) {
            errors["levelMax"] = "Invalid number"
        }
        if (levelMinDouble != null && levelMaxDouble != null && levelMinDouble >= levelMaxDouble) {
            errors["levelMin"] = "Min must be less than max"
        }
        if (levelMinDouble != null && (levelMinDouble < 0.0 || levelMinDouble > 200.0)) {
            errors["levelMin"] = "Must be between 0-200 cm"
        }
        if (levelMaxDouble != null && (levelMaxDouble < 0.0 || levelMaxDouble > 200.0)) {
            errors["levelMax"] = "Must be between 0-200 cm"
        }
        
        return errors
    }

    private fun validateThresholds(thresholds: Thresholds): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // pH validation
        if (thresholds.pHMin >= thresholds.pHMax) {
            errors["ph"] = "pH minimum must be less than maximum"
        }
        if (thresholds.pHMin < 0.0 || thresholds.pHMax > 14.0) {
            errors["ph"] = "pH values must be between 0.0 and 14.0"
        }

        // Dissolved Oxygen validation
        if (thresholds.doMin < 0.0) {
            errors["do"] = "Dissolved oxygen must be positive"
        }

        // Salinity validation
        if (thresholds.salinityMin >= thresholds.salinityMax) {
            errors["salinity"] = "Salinity minimum must be less than maximum"
        }
        if (thresholds.salinityMin < 0.0 || thresholds.salinityMax > 50.0) {
            errors["salinity"] = "Salinity values must be between 0.0 and 50.0 ppt"
        }

        // Ammonia validation
        if (thresholds.ammoniaMax < 0.0) {
            errors["ammonia"] = "Ammonia level must be positive"
        }
        if (thresholds.ammoniaMax > 10.0) {
            errors["ammonia"] = "Ammonia level seems too high (>10 mg/L)"
        }

        // Temperature validation
        if (thresholds.tempMin >= thresholds.tempMax) {
            errors["temperature"] = "Temperature minimum must be less than maximum"
        }
        if (thresholds.tempMin < 0.0 || thresholds.tempMax > 50.0) {
            errors["temperature"] = "Temperature values must be between 0°C and 50°C"
        }

        // Water level validation
        if (thresholds.levelMin >= thresholds.levelMax) {
            errors["level"] = "Water level minimum must be less than maximum"
        }
        if (thresholds.levelMin < 0.0 || thresholds.levelMax > 200.0) {
            errors["level"] = "Water level values must be between 0 and 200 cm"
        }

        return errors
    }
}