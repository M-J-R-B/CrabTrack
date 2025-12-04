package com.crabtrack.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.local.ThresholdsStore
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.FeedingReminder
import com.crabtrack.app.data.model.RecurrenceType
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import com.crabtrack.app.core.Defaults


enum class SaveState {
    Idle, Saving, Success, Error
}

data class SettingsUiState(
    val thresholds: Thresholds? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val thresholdsStore: ThresholdsStore
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // StateFlows for individual form fields (for real-time validation)
    private val _phMin = MutableStateFlow("")
    private val _phMax = MutableStateFlow("")
    private val _salinityMin = MutableStateFlow("")
    private val _salinityMax = MutableStateFlow("")
    private val _tempMin = MutableStateFlow("")
    private val _tempMax = MutableStateFlow("")
    private val _tdsMin = MutableStateFlow("")
    private val _tdsMax = MutableStateFlow("")
    private val _turbidityMax = MutableStateFlow("")
    
    // Save state for UI feedback
    private val _saveState = MutableStateFlow(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    // Real-time validation errors
    val validationErrors = combine(
        _phMin, _phMax, _salinityMin, _salinityMax,
        _tempMin, _tempMax, _tdsMin, _tdsMax, _turbidityMax
    ) { values ->
        validateFormFields(values[0], values[1], values[2], values[3],
                          values[4], values[5], values[6], values[7], values[8])
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Form validity check
    val formValid = combine(
        _phMin, _phMax, _salinityMin, _salinityMax,
        _tempMin, _tempMax, _tdsMin, _tdsMax, _turbidityMax,
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

    // Feeding reminders management
    private val _feedingReminders = MutableStateFlow<List<FeedingReminder>>(emptyList())
    val feedingReminders: StateFlow<List<FeedingReminder>> = _feedingReminders.asStateFlow()

    private val _reminderMessage = MutableStateFlow<String?>(null)
    val reminderMessage: StateFlow<String?> = _reminderMessage.asStateFlow()

    init {
        Log.d(TAG, "SettingsViewModel initialized")
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d(TAG, "Current user: ${currentUser?.uid ?: "NONE"}")
        loadThresholds()
        loadFeedingReminders()
    }

    private fun loadThresholds() {
        Log.d(TAG, "loadThresholds() called - INITIAL LOAD")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadThresholdsFromFirebase()  // ✅ Use same source as refresh
        }
    }
    
    private fun populateFormFields(thresholds: Thresholds) {
        _phMin.value = thresholds.pHMin.toString()
        _phMax.value = thresholds.pHMax.toString()
        _salinityMin.value = thresholds.salinityMin.toString()
        _salinityMax.value = thresholds.salinityMax.toString()
        _tempMin.value = thresholds.tempMin.toString()
        _tempMax.value = thresholds.tempMax.toString()
        _tdsMin.value = thresholds.tdsMin.toString()
        _tdsMax.value = thresholds.tdsMax.toString()
        _turbidityMax.value = thresholds.turbidityMax.toString()
    }
    
    // Update methods for real-time StateFlow binding
    fun updatePhMin(value: String) { _phMin.value = value }
    fun updatePhMax(value: String) { _phMax.value = value }
    fun updateSalinityMin(value: String) { _salinityMin.value = value }
    fun updateSalinityMax(value: String) { _salinityMax.value = value }
    fun updateTempMin(value: String) { _tempMin.value = value }
    fun updateTempMax(value: String) { _tempMax.value = value }
    fun updateTdsMin(value: String) { _tdsMin.value = value }
    fun updateTdsMax(value: String) { _tdsMax.value = value }
    fun updateTurbidityMax(value: String) { _turbidityMax.value = value }

    fun saveThresholds() {
        val fields = listOf(
            _phMin.value, _phMax.value, _salinityMin.value, _salinityMax.value,
            _tempMin.value, _tempMax.value, _tdsMin.value, _tdsMax.value, _turbidityMax.value
        )

        // 🔹 Step 1: Check if all fields are filled and numeric
        val allFilled = fields.all { it.isNotBlank() && it.toDoubleOrNull() != null }
        if (!allFilled) {
            _uiState.value = _uiState.value.copy(
            )
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
                // 🔹 Step 2: Build thresholds object
                val thresholds = Thresholds(
                    pHMin = _phMin.value.toDouble(),
                    pHMax = _phMax.value.toDouble(),
                    doMin = 0.0, // Not used in alerts
                    salinityMin = _salinityMin.value.toDouble(),
                    salinityMax = _salinityMax.value.toDouble(),
                    ammoniaMax = 0.0, // Not used in alerts
                    tempMin = _tempMin.value.toDouble(),
                    tempMax = _tempMax.value.toDouble(),
                    levelMin = 0.0, // Not used in alerts
                    levelMax = 0.0, // Not used in alerts
                    tdsMin = _tdsMin.value.toDouble(),
                    tdsMax = _tdsMax.value.toDouble(),
                    turbidityMax = _turbidityMax.value.toDouble()
                )

                // 🔹 Step 3: Run detailed validation
                val validationErrors = validateThresholds(thresholds)
                if (validationErrors.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        validationErrors = validationErrors,
                    )
                    _saveState.value = SaveState.Error
                    return@launch
                }

                // 🔹 Step 4: Save locally
                thresholdsStore.saveThresholds(thresholds)
                android.util.Log.d("SettingsDebug", "✅ Local thresholds saved: $thresholds")

                // 🔹 Step 5: Ensure Firebase Auth
                val auth = FirebaseAuth.getInstance()
                var user = auth.currentUser
                if (user == null) {
                    android.util.Log.w("SettingsDebug", "⚠️ No user logged in. Signing in anonymously...")
                    val result = auth.signInAnonymously().await()
                    user = result.user
                    android.util.Log.d("SettingsDebug", "✅ Anonymous sign-in successful: ${user?.uid}")
                }

                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = "User authentication failed. Please try again."
                    )
                    _saveState.value = SaveState.Error
                    return@launch
                }

                // 🔹 Step 6: Save to Firebase (to /thresholds to match web)
                val dbRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.uid)
                    .child("thresholds")

                // Save each sensor separately with enabled flag (matching web structure)
                val updates = mutableMapOf<String, Any>()

                // pH
                updates["ph/enabled"] = true
                updates["ph/min"] = thresholds.pHMin
                updates["ph/max"] = thresholds.pHMax

                // Salinity
                updates["salinity/enabled"] = true
                updates["salinity/min"] = thresholds.salinityMin
                updates["salinity/max"] = thresholds.salinityMax

                // Temperature
                updates["temperature/enabled"] = true
                updates["temperature/min"] = thresholds.tempMin
                updates["temperature/max"] = thresholds.tempMax

                // TDS
                updates["tds/enabled"] = true
                updates["tds/min"] = thresholds.tdsMin
                updates["tds/max"] = thresholds.tdsMax

                // Turbidity
                updates["turbidity/enabled"] = true
                updates["turbidity/min"] = 0.0
                updates["turbidity/max"] = thresholds.turbidityMax

                dbRef.updateChildren(updates)
                    .addOnSuccessListener {
                        android.util.Log.d("SettingsDebug", "✅ Firebase save successful.")
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            validationErrors = emptyMap()
                        )
                        _saveState.value = SaveState.Success
                    }
                    .addOnFailureListener { e ->
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            errorMessage = "Failed to save to Firebase: ${e.message}"
                        )
                        _saveState.value = SaveState.Error
                    }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to save settings: ${e.message}"
                )
                _saveState.value = SaveState.Error
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }



    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                thresholdsStore.resetToDefaults()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
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

    // 🔹 SAVE THRESHOLDS TO FIREBASE
//    private fun saveThresholdsToFirebase(thresholds: Thresholds) {
//        val user = FirebaseAuth.getInstance().currentUser
//        if (user == null) {
//            _uiState.value = _uiState.value.copy(
//                errorMessage = "User not authenticated. Please log in again."
//            )
//            _saveState.value = SaveState.Error
//            return
//        }
//
//        val uid = user.uid
//        val dbRef = FirebaseDatabase.getInstance()
//            .getReference("users")
//            .child(uid)
//            .child("settings")
//
//        val dataMap = mapOf(
//            "ph" to mapOf("min" to thresholds.pHMin, "max" to thresholds.pHMax),
//            "do" to mapOf("min" to thresholds.doMin),
//            "salinity" to mapOf("min" to thresholds.salinityMin, "max" to thresholds.salinityMax),
//            "ammonia" to mapOf("max" to thresholds.ammoniaMax),
//            "temperature" to mapOf("min" to thresholds.tempMin, "max" to thresholds.tempMax),
//            "waterLevel" to mapOf("min" to thresholds.levelMin, "max" to thresholds.levelMax),
//            "version" to 1,
//            "updatedAt" to ServerValue.TIMESTAMP
//        )
//
//        _saveState.value = SaveState.Saving
//
//        dbRef.setValue(dataMap)
//            .addOnSuccessListener {
//                _saveState.value = SaveState.Success
//                _uiState.value = _uiState.value.copy(
//                    successMessage = "Settings saved successfully!",
//                    isSaving = false
//                )
//            }
//            .addOnFailureListener { e ->
//                _saveState.value = SaveState.Error
//                _uiState.value = _uiState.value.copy(
//                    errorMessage = "Failed to save to Firebase: ${e.message}",
//                    isSaving = false
//                )
//            }
//    }


    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            validationErrors = emptyMap()
        )
    }

    private fun validateFormFields(
        phMin: String, phMax: String,
        salinityMin: String, salinityMax: String,
        tempMin: String, tempMax: String,
        tdsMin: String, tdsMax: String, turbidityMax: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // pH validation
        val phMinDouble = phMin.toDoubleOrNull()
        val phMaxDouble = phMax.toDoubleOrNull()
        if (phMinDouble == null && phMin.isNotEmpty()) errors["phMin"] = "Invalid number"
        if (phMaxDouble == null && phMax.isNotEmpty()) errors["phMax"] = "Invalid number"
        if (phMinDouble != null && phMaxDouble != null && phMaxDouble <= phMinDouble) {
            errors["phMin"] = "Min must be less than max"
            errors["phMax"] = "Max must be greater than min"
        }
        if (phMinDouble != null && (phMinDouble < 0.0 || phMinDouble > 14.0))
            errors["phMin"] = "Must be between 0–14"
        if (phMaxDouble != null && (phMaxDouble < 0.0 || phMaxDouble > 14.0))
            errors["phMax"] = "Must be between 0–14"

        // Salinity validation
        val salinityMinDouble = salinityMin.toDoubleOrNull()
        val salinityMaxDouble = salinityMax.toDoubleOrNull()
        if (salinityMinDouble == null && salinityMin.isNotEmpty()) errors["salinityMin"] = "Invalid number"
        if (salinityMaxDouble == null && salinityMax.isNotEmpty()) errors["salinityMax"] = "Invalid number"
        if (salinityMinDouble != null && salinityMaxDouble != null && salinityMaxDouble <= salinityMinDouble) {
            errors["salinityMin"] = "Min must be less than max"
            errors["salinityMax"] = "Max must be greater than min"
        }
        if (salinityMinDouble != null && (salinityMinDouble < 0.0 || salinityMinDouble > 50.0))
            errors["salinityMin"] = "Must be between 0–50 ppt"
        if (salinityMaxDouble != null && (salinityMaxDouble < 0.0 || salinityMaxDouble > 50.0))
            errors["salinityMax"] = "Must be between 0–50 ppt"

        // Temperature validation
        val tempMinDouble = tempMin.toDoubleOrNull()
        val tempMaxDouble = tempMax.toDoubleOrNull()
        if (tempMinDouble == null && tempMin.isNotEmpty()) errors["tempMin"] = "Invalid number"
        if (tempMaxDouble == null && tempMax.isNotEmpty()) errors["tempMax"] = "Invalid number"
        if (tempMinDouble != null && tempMaxDouble != null && tempMaxDouble <= tempMinDouble) {
            errors["tempMin"] = "Min must be less than max"
            errors["tempMax"] = "Max must be greater than min"
        }
        if (tempMinDouble != null && (tempMinDouble < 0.0 || tempMinDouble > 50.0))
            errors["tempMin"] = "Must be between 0–50°C"
        if (tempMaxDouble != null && (tempMaxDouble < 0.0 || tempMaxDouble > 50.0))
            errors["tempMax"] = "Must be between 0–50°C"

        // TDS validation
        val tdsMinDouble = tdsMin.toDoubleOrNull()
        val tdsMaxDouble = tdsMax.toDoubleOrNull()
        if (tdsMinDouble == null && tdsMin.isNotEmpty()) errors["tdsMin"] = "Invalid number"
        if (tdsMaxDouble == null && tdsMax.isNotEmpty()) errors["tdsMax"] = "Invalid number"
        if (tdsMinDouble != null && tdsMaxDouble != null && tdsMaxDouble <= tdsMinDouble) {
            errors["tdsMin"] = "Min must be less than max"
            errors["tdsMax"] = "Max must be greater than min"
        }
        if (tdsMinDouble != null && (tdsMinDouble < 0.0 || tdsMinDouble > 50000.0))
            errors["tdsMin"] = "Must be between 0–50,000 ppm"
        if (tdsMaxDouble != null && (tdsMaxDouble < 0.0 || tdsMaxDouble > 50000.0))
            errors["tdsMax"] = "Must be between 0–50,000 ppm"

        // Turbidity validation
        val turbidityMaxDouble = turbidityMax.toDoubleOrNull()
        if (turbidityMaxDouble == null && turbidityMax.isNotEmpty()) errors["turbidityMax"] = "Invalid number"
        if (turbidityMaxDouble != null && turbidityMaxDouble < 0.0)
            errors["turbidityMax"] = "Must be positive"
        if (turbidityMaxDouble != null && turbidityMaxDouble > 1000.0)
            errors["turbidityMax"] = "Seems too high (>1000 NTU)"

        return errors
    }


    private fun validateThresholds(thresholds: Thresholds): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // 🔹 pH validation
        if (thresholds.pHMin >= thresholds.pHMax) {
            errors["phMin"] = "Min must be less than max"
            errors["phMax"] = "Max must be greater than min"
        }
        if (thresholds.pHMin < 0.0 || thresholds.pHMax > 14.0) {
            errors["phMin"] = "Must be between 0.0 and 14.0"
            errors["phMax"] = "Must be between 0.0 and 14.0"
        }

        // 🔹 Salinity validation
        if (thresholds.salinityMin >= thresholds.salinityMax) {
            errors["salinityMin"] = "Min must be less than max"
            errors["salinityMax"] = "Max must be greater than min"
        }
        if (thresholds.salinityMin < 0.0 || thresholds.salinityMax > 50.0) {
            errors["salinityMin"] = "Must be between 0.0 and 50.0 ppt"
            errors["salinityMax"] = "Must be between 0.0 and 50.0 ppt"
        }

        // 🔹 Temperature validation
        if (thresholds.tempMin >= thresholds.tempMax) {
            errors["tempMin"] = "Min must be less than max"
            errors["tempMax"] = "Max must be greater than min"
        }
        if (thresholds.tempMin < 0.0 || thresholds.tempMax > 50.0) {
            errors["tempMin"] = "Must be between 0°C and 50°C"
            errors["tempMax"] = "Must be between 0°C and 50°C"
        }

        // 🔹 TDS validation
        if (thresholds.tdsMin >= thresholds.tdsMax) {
            errors["tdsMin"] = "Min must be less than max"
            errors["tdsMax"] = "Max must be greater than min"
        }
        if (thresholds.tdsMin < 0.0 || thresholds.tdsMax > 50000.0) {
            errors["tdsMin"] = "Must be between 0 and 50,000 ppm"
            errors["tdsMax"] = "Must be between 0 and 50,000 ppm"
        }

        // 🔹 Turbidity validation
        if (thresholds.turbidityMax < 0.0) {
            errors["turbidityMax"] = "Must be positive"
        } else if (thresholds.turbidityMax > 1000.0) {
            errors["turbidityMax"] = "Seems too high (>1000 NTU)"
        }

        return errors
    }



    fun loadThresholdsFromFirebase() {
        // ✅ AUTH GUARD: Don't proceed if no user is logged in
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "Cannot load thresholds - no user logged in")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Please log in to view thresholds"
            )
            return
        }

        val uid = user.uid
        Log.d(TAG, "Fetching thresholds from Firebase for user: $uid")

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("thresholds")

        Log.d(TAG, "Firebase path: users/$uid/thresholds")

        _uiState.value = _uiState.value.copy(isLoading = true)

        dbRef.get().addOnSuccessListener { snapshot ->
            Log.d(TAG, "Firebase fetch SUCCESS")
            Log.d(TAG, "Snapshot exists: ${snapshot.exists()}")

            if (snapshot.exists()) {
                // Log sample values for debugging
                Log.d(TAG, "pH min from Firebase: ${snapshot.child("ph/min").getValue(Double::class.java)}")
                Log.d(TAG, "pH max from Firebase: ${snapshot.child("ph/max").getValue(Double::class.java)}")

                // Parse the web structure (enabled, min, max per sensor)
                val thresholds = Thresholds(
                    pHMin = snapshot.child("ph/min").getValue(Double::class.java) ?: Defaults.PH_MIN,
                    pHMax = snapshot.child("ph/max").getValue(Double::class.java) ?: Defaults.PH_MAX,
                    doMin = snapshot.child("dissolved_oxygen/min").getValue(Double::class.java) ?: Defaults.DISSOLVED_OXYGEN_MIN,
                    salinityMin = snapshot.child("salinity/min").getValue(Double::class.java) ?: Defaults.SALINITY_MIN,
                    salinityMax = snapshot.child("salinity/max").getValue(Double::class.java) ?: Defaults.SALINITY_MAX,
                    ammoniaMax = snapshot.child("ammonia/max").getValue(Double::class.java) ?: Defaults.AMMONIA_MAX,
                    tempMin = snapshot.child("temperature/min").getValue(Double::class.java) ?: Defaults.TEMPERATURE_MIN,
                    tempMax = snapshot.child("temperature/max").getValue(Double::class.java) ?: Defaults.TEMPERATURE_MAX,
                    levelMin = snapshot.child("water_level/min").getValue(Double::class.java) ?: Defaults.WATER_LEVEL_MIN,
                    levelMax = snapshot.child("water_level/max").getValue(Double::class.java) ?: Defaults.WATER_LEVEL_MAX,
                    tdsMin = snapshot.child("tds/min").getValue(Double::class.java) ?: Defaults.TDS_MIN,
                    tdsMax = snapshot.child("tds/max").getValue(Double::class.java) ?: Defaults.TDS_MAX,
                    turbidityMax = snapshot.child("turbidity/max").getValue(Double::class.java) ?: Defaults.TURBIDITY_MAX
                )

                Log.d(TAG, "Parsed thresholds: pHMin=${thresholds.pHMin}, pHMax=${thresholds.pHMax}")

                _uiState.value = _uiState.value.copy(
                    thresholds = thresholds,
                    isLoading = false,
                    errorMessage = null
                )
                populateFormFields(thresholds)
            } else {
                // ✅ EDGE CASE: No thresholds found in Firebase - use defaults
                Log.w(TAG, "No thresholds found for user - using defaults")
                val defaultThresholds = Thresholds(
                    pHMin = Defaults.PH_MIN,
                    pHMax = Defaults.PH_MAX,
                    doMin = Defaults.DISSOLVED_OXYGEN_MIN,
                    salinityMin = Defaults.SALINITY_MIN,
                    salinityMax = Defaults.SALINITY_MAX,
                    ammoniaMax = Defaults.AMMONIA_MAX,
                    tempMin = Defaults.TEMPERATURE_MIN,
                    tempMax = Defaults.TEMPERATURE_MAX,
                    levelMin = Defaults.WATER_LEVEL_MIN,
                    levelMax = Defaults.WATER_LEVEL_MAX,
                    tdsMin = Defaults.TDS_MIN,
                    tdsMax = Defaults.TDS_MAX,
                    turbidityMax = Defaults.TURBIDITY_MAX
                )
                _uiState.value = _uiState.value.copy(
                    thresholds = defaultThresholds,
                    isLoading = false,
                    errorMessage = null
                )
                populateFormFields(defaultThresholds)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Firebase fetch FAILED", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to load thresholds from server: ${e.message}"
            )
        }
    }

    fun refreshThresholdsFromFirebase() {
        Log.d(TAG, "refreshThresholdsFromFirebase() called - SWIPE REFRESH")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                loadThresholdsFromFirebase()
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    // ========== Feeding Reminders Management ==========

    fun loadFeedingReminders() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
            .child("feeding_reminders")

        // Changed from addValueEventListener (continuous) to addListenerForSingleValueEvent (query-on-demand)
        // This saves data by not maintaining a persistent listener connection
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reminders = mutableListOf<FeedingReminder>()
                for (child in snapshot.children) {

                    // 🔹 Read raw actionType from Firebase, default to "FEED" if missing
                    // 🔹 Read raw actionType from Firebase, default to "FEED" if missing
                    val actionTypeStr = child.child("actionType").value?.toString() ?: "FEED"

                    val actionTypeEnum = try {
                        com.crabtrack.app.data.model.ActionType.valueOf(actionTypeStr)
                    } catch (_: Exception) {
                        com.crabtrack.app.data.model.ActionType.FEED
                    }

// 🔹 Read the rest of the reminder normally
                    val reminder = child.getValue(com.crabtrack.app.data.model.FeedingReminder::class.java)
                    if (reminder != null) {
                        reminders.add(
                            reminder.copy(
                                id = child.key ?: "",
                                actionType = actionTypeEnum.name   // ✅ now a String
                            )
                        )
                    }

                }

                // Sort by timestamp (earliest first)
                _feedingReminders.value = reminders.sortedBy { it.timestamp }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("SettingsViewModel", "Failed to load reminders: ${error.message}")
            }
        })
    }

    fun deleteReminder(reminder: FeedingReminder) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        viewModelScope.launch {
            try {
                FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.uid)
                    .child("feeding_reminders")
                    .child(reminder.id)
                    .removeValue()
                    .await()

                _reminderMessage.value = "Reminder deleted successfully"

                // Reload reminders after deletion since we're using query-on-demand now
                loadFeedingReminders()
            } catch (e: Exception) {
                _reminderMessage.value = "Failed to delete reminder: ${e.message}"
            }
        }
    }

    fun clearReminderMessage() {
        _reminderMessage.value = null
    }

}