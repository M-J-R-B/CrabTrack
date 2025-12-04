package com.crabtrack.app.data.repository

import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThresholdRepository @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val firebaseThresholdRepository: FirebaseThresholdRepository
) {

    /**
     * Get threshold for a specific sensor
     * Returns local cache value - updated automatically by Firebase listener
     */
    fun getThreshold(sensorType: SensorType): Flow<Threshold> {
        return preferencesDataStore.getThreshold(sensorType)
    }

    /**
     * Get all thresholds with real-time Firebase sync
     * Firebase listener automatically updates local cache
     */
    fun getAllThresholds(): Flow<Map<SensorType, Threshold>> {
        return firebaseThresholdRepository.observeThresholds()
    }

    /**
     * Update threshold - saves to Firebase (cloud as source of truth)
     * Local cache updated automatically via Firebase listener
     */
    suspend fun updateThreshold(threshold: Threshold) {
        firebaseThresholdRepository.saveThreshold(threshold)
    }

    /**
     * Batch update thresholds - saves to Firebase
     */
    suspend fun updateThresholds(thresholds: List<Threshold>) {
        firebaseThresholdRepository.saveAllThresholds(thresholds)
    }
}