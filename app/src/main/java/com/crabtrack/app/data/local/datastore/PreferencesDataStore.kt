package com.crabtrack.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "thresholds")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun getThreshold(sensorType: SensorType): Flow<Threshold> {
        return context.dataStore.data.map { preferences ->
            val defaultThreshold = ThresholdPreferences.getDefaultThreshold(sensorType)
            
            Threshold(
                sensorType = sensorType,
                warningMin = preferences[ThresholdPreferences.getWarningMinKey(sensorType)]
                    ?: defaultThreshold.warningMin,
                warningMax = preferences[ThresholdPreferences.getWarningMaxKey(sensorType)]
                    ?: defaultThreshold.warningMax,
                criticalMin = preferences[ThresholdPreferences.getCriticalMinKey(sensorType)]
                    ?: defaultThreshold.criticalMin,
                criticalMax = preferences[ThresholdPreferences.getCriticalMaxKey(sensorType)]
                    ?: defaultThreshold.criticalMax
            )
        }
    }
    
    fun getAllThresholds(): Flow<Map<SensorType, Threshold>> {
        return context.dataStore.data.map { preferences ->
            SensorType.values().associateWith { sensorType ->
                val defaultThreshold = ThresholdPreferences.getDefaultThreshold(sensorType)
                
                Threshold(
                    sensorType = sensorType,
                    warningMin = preferences[ThresholdPreferences.getWarningMinKey(sensorType)]
                        ?: defaultThreshold.warningMin,
                    warningMax = preferences[ThresholdPreferences.getWarningMaxKey(sensorType)]
                        ?: defaultThreshold.warningMax,
                    criticalMin = preferences[ThresholdPreferences.getCriticalMinKey(sensorType)]
                        ?: defaultThreshold.criticalMin,
                    criticalMax = preferences[ThresholdPreferences.getCriticalMaxKey(sensorType)]
                        ?: defaultThreshold.criticalMax
                )
            }
        }
    }
    
    suspend fun updateThreshold(threshold: Threshold) {
        context.dataStore.edit { preferences ->
            threshold.warningMin?.let {
                preferences[ThresholdPreferences.getWarningMinKey(threshold.sensorType)] = it
            }
            threshold.warningMax?.let {
                preferences[ThresholdPreferences.getWarningMaxKey(threshold.sensorType)] = it
            }
            threshold.criticalMin?.let {
                preferences[ThresholdPreferences.getCriticalMinKey(threshold.sensorType)] = it
            }
            threshold.criticalMax?.let {
                preferences[ThresholdPreferences.getCriticalMaxKey(threshold.sensorType)] = it
            }
        }
    }
}