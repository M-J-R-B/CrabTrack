package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.CameraToTankMapper
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing camera-to-tank mappings from Firebase.
 *
 * Listens to the global /camera_mappings path and updates CameraToTankMapper
 * with dynamic mappings. This allows the app to map camera IPs to tank IDs
 * for molting alerts.
 *
 * Firebase structure:
 * /camera_mappings/{ip_with_underscores}/
 *   - tank_id: String (e.g., "tank2")
 *   - user_id: String
 *   - created_at: Long
 */
@Singleton
class CameraMappingRepository @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CameraMappingRepo"
        private const val CAMERA_MAPPINGS_PATH = "camera_mappings"
    }

    private var mappingsListener: ValueEventListener? = null

    /**
     * Starts listening to the /camera_mappings path in Firebase.
     * Updates CameraToTankMapper whenever mappings change.
     */
    fun startListening() {
        if (mappingsListener != null) {
            Log.d(TAG, "Already listening to camera mappings")
            return
        }

        val mappingsRef = firebaseDatabase.getReference(CAMERA_MAPPINGS_PATH)

        mappingsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val mappings = mutableMapOf<String, String>()

                        snapshot.children.forEach { ipSnapshot ->
                            val ipKey = ipSnapshot.key ?: return@forEach
                            val tankId = ipSnapshot.child("tank_id").getValue(String::class.java)

                            if (tankId != null) {
                                // Convert Firebase key back to IP format
                                // e.g., "192_168_8_116" -> "192.168.8.116"
                                val cameraIp = ipKeyToIp(ipKey)
                                mappings[cameraIp] = tankId
                            }
                        }

                        // Update the mapper with new mappings
                        CameraToTankMapper.updateFromFirebase(mappings)
                        Log.i(TAG, "Camera mappings updated: ${mappings.size} mappings")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing camera mappings", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Camera mappings listener cancelled: ${error.message}")
            }
        }

        mappingsRef.addValueEventListener(mappingsListener!!)
        Log.i(TAG, "Started listening to camera mappings")
    }

    /**
     * Stops listening to camera mappings.
     */
    fun stopListening() {
        mappingsListener?.let { listener ->
            firebaseDatabase.getReference(CAMERA_MAPPINGS_PATH).removeEventListener(listener)
            mappingsListener = null
            Log.i(TAG, "Stopped listening to camera mappings")
        }
    }

    /**
     * Saves a camera-to-tank mapping to Firebase.
     * Used when generating test alerts to ensure the camera IP is mapped.
     *
     * @param cameraIp The camera IP address (e.g., "192.168.8.116")
     * @param tankId The tank ID (e.g., "tank2")
     * @param userId The user ID who created the mapping
     */
    suspend fun saveMapping(cameraIp: String, tankId: String, userId: String): Result<Unit> {
        return try {
            val ipKey = ipToIpKey(cameraIp)
            val mappingData = mapOf(
                "tank_id" to tankId,
                "user_id" to userId,
                "created_at" to System.currentTimeMillis()
            )

            firebaseDatabase
                .getReference("$CAMERA_MAPPINGS_PATH/$ipKey")
                .setValue(mappingData)
                .await()

            Log.i(TAG, "Saved camera mapping: $cameraIp -> $tankId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save camera mapping", e)
            Result.failure(e)
        }
    }

    /**
     * Converts a camera IP to Firebase key format.
     * e.g., "192.168.8.116" -> "192_168_8_116"
     */
    private fun ipToIpKey(cameraIp: String): String {
        return cameraIp.replace(".", "_")
    }

    /**
     * Converts a Firebase key back to camera IP format.
     * e.g., "192_168_8_116" -> "192.168.8.116"
     */
    private fun ipKeyToIp(ipKey: String): String {
        return ipKey.replace("_", ".")
    }
}
