package com.crabtrack.app.data.repository

import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThresholdRepository @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) {
    
    fun getThreshold(sensorType: SensorType): Flow<Threshold> {
        return preferencesDataStore.getThreshold(sensorType)
    }
    
    fun getAllThresholds(): Flow<Map<SensorType, Threshold>> {
        return preferencesDataStore.getAllThresholds()
    }
    
    suspend fun updateThreshold(threshold: Threshold) {
        preferencesDataStore.updateThreshold(threshold)
    }
    
    suspend fun updateThresholds(thresholds: List<Threshold>) {
        thresholds.forEach { threshold ->
            preferencesDataStore.updateThreshold(threshold)
        }
    }
}