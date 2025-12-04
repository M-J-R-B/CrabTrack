package com.crabtrack.app.data.migration

import android.content.Context
import android.util.Log
import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.local.datastore.ThresholdPreferences
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.repository.FirebaseThresholdRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThresholdMigrationManager @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val firebaseThresholdRepository: FirebaseThresholdRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ThresholdMigration"
        private const val PREFS_NAME = "threshold_migration"
        private const val KEY_MIGRATION_COMPLETE = "migration_v1_complete"
    }

    private val migrationPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Performs one-time migration of thresholds to Firebase
     *
     * Migration strategy:
     * 1. If Firebase has data → cloud wins (overwrite local cache)
     * 2. If Firebase empty but local has data → upload local to Firebase
     * 3. If both empty → upload defaults to Firebase
     */
    suspend fun migrateIfNeeded() {
        if (isMigrationComplete()) {
            Log.i(TAG, "Migration already completed - skipping")
            return
        }

        Log.i(TAG, "Starting threshold migration to Firebase...")

        try {
            // Step 1: Fetch from Firebase to see if it has data
            val firebaseThresholds = firebaseThresholdRepository.fetchThresholdsOnce()

            if (firebaseThresholds != null && firebaseThresholds.isNotEmpty()) {
                // Firebase has data - cloud is source of truth
                Log.i(TAG, "Firebase data found (${firebaseThresholds.size} sensors) - using cloud as source of truth")

                // Update local cache with Firebase data
                firebaseThresholds.forEach { (_, threshold) ->
                    preferencesDataStore.updateThreshold(threshold)
                }

                Log.i(TAG, "Local cache updated with Firebase data")
            } else {
                // Firebase has no data - check local
                Log.i(TAG, "No Firebase data found - checking local DataStore...")

                val localThresholds = preferencesDataStore.getAllThresholds().first()

                if (localThresholds.isNotEmpty()) {
                    // Upload local to Firebase
                    Log.i(TAG, "Local data found (${localThresholds.size} sensors) - migrating to Firebase")

                    val result = firebaseThresholdRepository.saveAllThresholds(
                        localThresholds.values.toList()
                    )

                    if (result.isSuccess) {
                        Log.i(TAG, "Local thresholds successfully migrated to Firebase")
                    } else {
                        Log.e(TAG, "Failed to migrate local thresholds: ${result.exceptionOrNull()?.message}")
                        // Don't mark as complete if migration failed
                        return
                    }
                } else {
                    // Neither local nor Firebase has data - use defaults
                    Log.i(TAG, "No local or Firebase data - uploading defaults")

                    val defaultThresholds = SensorType.values().map { sensorType ->
                        ThresholdPreferences.getDefaultThreshold(sensorType)
                    }

                    val result = firebaseThresholdRepository.saveAllThresholds(defaultThresholds)

                    if (result.isSuccess) {
                        Log.i(TAG, "Default thresholds uploaded to Firebase")
                    } else {
                        Log.e(TAG, "Failed to upload defaults: ${result.exceptionOrNull()?.message}")
                        return
                    }
                }
            }

            // Mark migration as complete
            markMigrationComplete()
            Log.i(TAG, "Migration completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed with exception - will retry next launch", e)
            // Don't mark as complete so it will retry
        }
    }

    /**
     * Checks if migration has already been completed
     */
    private fun isMigrationComplete(): Boolean {
        return migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
    }

    /**
     * Marks migration as complete to prevent re-runs
     */
    private fun markMigrationComplete() {
        migrationPrefs.edit().putBoolean(KEY_MIGRATION_COMPLETE, true).apply()
        Log.i(TAG, "Migration marked as complete")
    }

    /**
     * Resets migration flag (for testing purposes only)
     */
    fun resetMigrationFlag() {
        migrationPrefs.edit().putBoolean(KEY_MIGRATION_COMPLETE, false).apply()
        Log.w(TAG, "Migration flag reset - migration will run again")
    }
}
