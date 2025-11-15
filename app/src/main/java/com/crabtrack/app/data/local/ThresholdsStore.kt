package com.crabtrack.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.crabtrack.app.core.Defaults
import com.crabtrack.app.data.model.Thresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.thresholdsDataStore: DataStore<Preferences> by preferencesDataStore(name = "water_thresholds")

@Singleton
class ThresholdsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private val PH_MIN_KEY = doublePreferencesKey("ph_min")
        private val PH_MAX_KEY = doublePreferencesKey("ph_max")
        private val DO_MIN_KEY = doublePreferencesKey("do_min")
        private val SALINITY_MIN_KEY = doublePreferencesKey("salinity_min")
        private val SALINITY_MAX_KEY = doublePreferencesKey("salinity_max")
        private val AMMONIA_MAX_KEY = doublePreferencesKey("ammonia_max")
        private val TEMP_MIN_KEY = doublePreferencesKey("temp_min")
        private val TEMP_MAX_KEY = doublePreferencesKey("temp_max")
        private val LEVEL_MIN_KEY = doublePreferencesKey("level_min")
        private val LEVEL_MAX_KEY = doublePreferencesKey("level_max")
        private val TDS_MIN_KEY = doublePreferencesKey("tds_min")
        private val TDS_MAX_KEY = doublePreferencesKey("tds_max")
        private val TURBIDITY_MAX_KEY = doublePreferencesKey("turbidity_max")
    }
    
    private val _thresholds = MutableStateFlow(Defaults.createDefaultThresholds())
    val thresholds: StateFlow<Thresholds> = _thresholds.asStateFlow()

    init {
        // Initialize StateFlow with persisted values from DataStore
        scope.launch {
            getThresholds().collect { thresholds ->
                _thresholds.value = thresholds
            }
        }
    }
    
    /**
     * Gets the current thresholds from DataStore, falling back to defaults on first run
     */
    fun getThresholds(): Flow<Thresholds> {
        return context.thresholdsDataStore.data.map { preferences ->
            val defaults = Defaults.createDefaultThresholds()

            Thresholds(
                pHMin = preferences[PH_MIN_KEY] ?: defaults.pHMin,
                pHMax = preferences[PH_MAX_KEY] ?: defaults.pHMax,
                doMin = preferences[DO_MIN_KEY] ?: defaults.doMin,
                salinityMin = preferences[SALINITY_MIN_KEY] ?: defaults.salinityMin,
                salinityMax = preferences[SALINITY_MAX_KEY] ?: defaults.salinityMax,
                ammoniaMax = preferences[AMMONIA_MAX_KEY] ?: defaults.ammoniaMax,
                tempMin = preferences[TEMP_MIN_KEY] ?: defaults.tempMin,
                tempMax = preferences[TEMP_MAX_KEY] ?: defaults.tempMax,
                levelMin = preferences[LEVEL_MIN_KEY] ?: defaults.levelMin,
                levelMax = preferences[LEVEL_MAX_KEY] ?: defaults.levelMax,
                tdsMin = preferences[TDS_MIN_KEY] ?: defaults.tdsMin,
                tdsMax = preferences[TDS_MAX_KEY] ?: defaults.tdsMax,
                turbidityMax = preferences[TURBIDITY_MAX_KEY] ?: defaults.turbidityMax
            )
        }
    }
    
    /**
     * Saves the provided thresholds to DataStore and updates StateFlow
     */
    suspend fun saveThresholds(thresholds: Thresholds) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[PH_MIN_KEY] = thresholds.pHMin
            preferences[PH_MAX_KEY] = thresholds.pHMax
            preferences[DO_MIN_KEY] = thresholds.doMin
            preferences[SALINITY_MIN_KEY] = thresholds.salinityMin
            preferences[SALINITY_MAX_KEY] = thresholds.salinityMax
            preferences[AMMONIA_MAX_KEY] = thresholds.ammoniaMax
            preferences[TEMP_MIN_KEY] = thresholds.tempMin
            preferences[TEMP_MAX_KEY] = thresholds.tempMax
            preferences[LEVEL_MIN_KEY] = thresholds.levelMin
            preferences[LEVEL_MAX_KEY] = thresholds.levelMax
            preferences[TDS_MIN_KEY] = thresholds.tdsMin
            preferences[TDS_MAX_KEY] = thresholds.tdsMax
            preferences[TURBIDITY_MAX_KEY] = thresholds.turbidityMax
        }
        // Update StateFlow immediately
        _thresholds.value = thresholds
    }
    
    /**
     * Updates thresholds directly in StateFlow (for immediate UI updates)
     */
    fun updateThresholds(newThresholds: Thresholds) {
        _thresholds.value = newThresholds
    }
    
    /**
     * Resets all thresholds to default values
     */
    suspend fun resetToDefaults() {
        val defaults = Defaults.createDefaultThresholds()
        saveThresholds(defaults)
    }
    
    /**
     * Updates individual threshold values and StateFlow
     */
    suspend fun updatePhRange(min: Double, max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[PH_MIN_KEY] = min
            preferences[PH_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(pHMin = min, pHMax = max)
    }
    
    suspend fun updateDoMin(min: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[DO_MIN_KEY] = min
        }
        _thresholds.value = _thresholds.value.copy(doMin = min)
    }
    
    suspend fun updateSalinityRange(min: Double, max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[SALINITY_MIN_KEY] = min
            preferences[SALINITY_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(salinityMin = min, salinityMax = max)
    }
    
    suspend fun updateAmmoniaMax(max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[AMMONIA_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(ammoniaMax = max)
    }
    
    suspend fun updateTemperatureRange(min: Double, max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[TEMP_MIN_KEY] = min
            preferences[TEMP_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(tempMin = min, tempMax = max)
    }
    
    suspend fun updateWaterLevelRange(min: Double, max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[LEVEL_MIN_KEY] = min
            preferences[LEVEL_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(levelMin = min, levelMax = max)
    }

    suspend fun updateTdsRange(min: Double, max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[TDS_MIN_KEY] = min
            preferences[TDS_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(tdsMin = min, tdsMax = max)
    }

    suspend fun updateTurbidityMax(max: Double) {
        context.thresholdsDataStore.edit { preferences ->
            preferences[TURBIDITY_MAX_KEY] = max
        }
        _thresholds.value = _thresholds.value.copy(turbidityMax = max)
    }
}