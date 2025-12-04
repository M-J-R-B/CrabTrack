package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.local.datastore.ThresholdPreferences
import com.crabtrack.app.data.model.FirebaseThresholdData
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import com.crabtrack.app.data.model.toFirebaseData
import com.crabtrack.app.data.model.toFirebaseKey
import com.crabtrack.app.data.model.toSensorType
import com.crabtrack.app.data.model.toThreshold
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseThresholdRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    private val preferencesDataStore: PreferencesDataStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FirebaseThresholdRepo"
        private const val THRESHOLDS_PATH = "thresholds"
    }

    private var listener: ValueEventListener? = null

    /**
     * Observes thresholds from Firebase in real-time
     * - When online: streams updates from Firebase and updates local cache
     * - When offline: returns cached values from DataStore
     */
    fun observeThresholds(): Flow<Map<SensorType, Threshold>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            // Not logged in - emit cached local data
            Log.w(TAG, "No user logged in, using local cache only")
            preferencesDataStore.getAllThresholds().collect { thresholds ->
                trySend(thresholds)
            }
            return@callbackFlow
        }

        val thresholdsRef = firebaseDatabase.getReference("users/$uid/$THRESHOLDS_PATH")

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val thresholds = parseThresholdsFromFirebase(snapshot)

                        // Update local cache (cloud as source of truth)
                        thresholds.forEach { (_, threshold) ->
                            preferencesDataStore.updateThreshold(threshold)
                        }

                        trySend(thresholds)
                        Log.i(TAG, "Thresholds synced from Firebase: ${thresholds.size} sensors")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Firebase thresholds", e)
                        // Fallback to cached data on error
                        preferencesDataStore.getAllThresholds().collect { cached ->
                            trySend(cached)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                scope.launch {
                    // Fallback to cached data
                    preferencesDataStore.getAllThresholds().collect { cached ->
                        trySend(cached)
                    }
                }
            }
        }

        thresholdsRef.addValueEventListener(listener!!)

        awaitClose {
            listener?.let { thresholdsRef.removeEventListener(it) }
            Log.i(TAG, "Firebase listener removed")
        }
    }

    /**
     * Saves a single threshold to Firebase (cloud as source of truth)
     * Converts Android Threshold model to web's Firebase format
     */
    suspend fun saveThreshold(threshold: Threshold): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val firebaseKey = threshold.sensorType.toFirebaseKey()
            val ref = firebaseDatabase.getReference("users/$uid/$THRESHOLDS_PATH/$firebaseKey")

            // Convert to web's format and save
            val firebaseData = threshold.toFirebaseData()
            ref.setValue(firebaseData).await()

            Log.i(TAG, "Threshold saved to Firebase: ${threshold.sensorType} -> $firebaseKey")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save threshold to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Batch save all thresholds to Firebase
     * Used for migration and reset to defaults
     */
    suspend fun saveAllThresholds(thresholds: List<Threshold>): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val updates = mutableMapOf<String, Any>()

            thresholds.forEach { threshold ->
                val firebaseKey = threshold.sensorType.toFirebaseKey()
                val firebaseData = threshold.toFirebaseData()

                // Build update map
                updates["$firebaseKey/enabled"] = firebaseData.enabled
                firebaseData.min?.let { updates["$firebaseKey/min"] = it }
                firebaseData.max?.let { updates["$firebaseKey/max"] = it }
            }

            // Perform atomic batch update
            firebaseDatabase.getReference("users/$uid/$THRESHOLDS_PATH")
                .updateChildren(updates)
                .await()

            Log.i(TAG, "All thresholds saved to Firebase: ${thresholds.size} sensors")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save all thresholds", e)
            Result.failure(e)
        }
    }

    /**
     * One-time fetch of thresholds from Firebase
     * Used for migration to check if Firebase has existing data
     */
    suspend fun fetchThresholdsOnce(): Map<SensorType, Threshold>? {
        val uid = getCurrentUserId()
        if (uid == null) {
            return null
        }

        return try {
            val snapshot = firebaseDatabase
                .getReference("users/$uid/$THRESHOLDS_PATH")
                .get()
                .await()

            if (snapshot.exists()) {
                parseThresholdsFromFirebase(snapshot)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch thresholds", e)
            null
        }
    }

    /**
     * Parses Firebase snapshot to threshold map
     * Handles web's lowercase sensor names and converts to Android's SensorType enum
     */
    private fun parseThresholdsFromFirebase(snapshot: DataSnapshot): Map<SensorType, Threshold> {
        val thresholds = mutableMapOf<SensorType, Threshold>()

        snapshot.children.forEach { sensorSnapshot ->
            val firebaseKey = sensorSnapshot.key ?: return@forEach
            val sensorType = firebaseKey.toSensorType() ?: run {
                Log.w(TAG, "Unknown sensor type in Firebase: $firebaseKey")
                return@forEach
            }

            try {
                val data = sensorSnapshot.getValue(FirebaseThresholdData::class.java)
                if (data != null && data.enabled) {
                    thresholds[sensorType] = data.toThreshold(sensorType)
                } else {
                    // If disabled or null, use default
                    thresholds[sensorType] = ThresholdPreferences.getDefaultThreshold(sensorType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing sensor $firebaseKey", e)
                // Use default on error
                thresholds[sensorType] = ThresholdPreferences.getDefaultThreshold(sensorType)
            }
        }

        // Fill in missing sensors with defaults (web only has 5 sensors, Android has 8)
        SensorType.values().forEach { sensorType ->
            if (!thresholds.containsKey(sensorType)) {
                thresholds[sensorType] = ThresholdPreferences.getDefaultThreshold(sensorType)
                Log.d(TAG, "Using default for missing sensor: $sensorType")
            }
        }

        return thresholds
    }

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
