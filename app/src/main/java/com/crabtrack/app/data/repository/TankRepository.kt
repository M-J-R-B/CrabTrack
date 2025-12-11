package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.Tank
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TankRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TankRepository"
        private const val TANKS_PATH = "tanks"
        private const val TANK_INFO_PATH = "details"  // Changed from "tank_info" to match web app
        private const val CRAB_DETAILS_PATH = "crab_details"
    }

    private var tanksListener: ValueEventListener? = null

    // Cached tanks for quick access
    private val _cachedTanks = MutableStateFlow<List<Tank>>(emptyList())
    val cachedTanks: StateFlow<List<Tank>> = _cachedTanks.asStateFlow()

    /**
     * Observes all tanks from Firebase in real-time
     * @return Flow emitting list of tanks sorted by number
     */
    fun observeAllTanks(): Flow<List<Tank>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in")
            trySend(emptyList())
            return@callbackFlow
        }

        val tanksRef = firebaseDatabase.getReference("users/$uid/$TANKS_PATH")

        tanksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val tanksList = mutableListOf<Tank>()

                        snapshot.children.forEach { tankSnapshot ->
                            val tankId = tankSnapshot.key ?: return@forEach
                            // Parse tank data directly from tankSnapshot (no subnode)
                            // Web stores data at tanks/{tankId}/, not tanks/{tankId}/details
                            val tank = parseTank(tankSnapshot, tankId)
                            if (tank != null) {
                                tanksList.add(tank)
                            }
                        }

                        // Sort by tank number
                        val sortedTanks = tanksList.sortedBy { it.number }
                        _cachedTanks.value = sortedTanks
                        trySend(sortedTanks)
                        Log.i(TAG, "Tanks synced from Firebase: ${sortedTanks.size} tanks")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing tanks", e)
                        trySend(emptyList())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                scope.launch {
                    trySend(emptyList())
                }
            }
        }

        tanksRef.addValueEventListener(tanksListener!!)

        awaitClose {
            tanksListener?.let { tanksRef.removeEventListener(it) }
            Log.i(TAG, "Tanks Firebase listener removed")
        }
    }

    /**
     * Adds a new tank with auto-incremented number.
     * New tanks default to Tank 1's camera IP.
     * @return Result containing the created Tank or failure
     */
    suspend fun addTank(): Result<Tank> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            // Calculate next tank number
            val nextNumber = (_cachedTanks.value.maxOfOrNull { it.number } ?: 0) + 1

            // Get Tank 1's camera IP to use as default, or use default
            val defaultIp = _cachedTanks.value.find { it.number == 1 }?.cameraIp
                ?: Tank.DEFAULT_CAMERA_IP

            val newTank = Tank.create(nextNumber, defaultIp)

            // Write directly to tanks/{tankId}/ (no subnode) to match web app
            val ref = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/${newTank.id}")
            ref.updateChildren(newTank.toMap()).await()

            Log.i(TAG, "Tank added: ${newTank.displayName}")
            Result.success(newTank)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add tank", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a tank and its associated crab details.
     * Tank 1 cannot be deleted.
     * @param tankId The tank identifier to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteTank(tankId: String): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        // Prevent deleting tank1
        if (tankId == "tank1") {
            return Result.failure(Exception("Cannot delete Tank 1"))
        }

        return try {
            // Delete the entire tank node (includes tank_info and crab_details)
            val ref = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/$tankId")
            ref.removeValue().await()

            Log.i(TAG, "Tank deleted: $tankId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete tank $tankId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the camera IP for a tank.
     * @param tankId The tank identifier
     * @param newIp The new camera IP address
     * @return Result indicating success or failure
     */
    suspend fun updateTankIp(tankId: String, newIp: String): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val updates = mapOf(
                "cameraIp" to newIp,
                "updatedAt" to System.currentTimeMillis()
            )

            // Write directly to tanks/{tankId}/ (no subnode) to match web app
            val ref = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/$tankId")
            ref.updateChildren(updates).await()

            Log.i(TAG, "Tank $tankId IP updated to: $newIp")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update tank IP", e)
            Result.failure(e)
        }
    }

    /**
     * Ensures the default Tank 1 exists.
     * Called on app startup or after login.
     */
    suspend fun ensureDefaultTankExists() {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "Cannot ensure default tank: no user logged in")
            return
        }

        try {
            // Write directly to tanks/tank1/ (no subnode) to match web app
            val tankRef = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/tank1")
            val snapshot = tankRef.get().await()

            if (!snapshot.exists()) {
                Log.i(TAG, "Creating default Tank 1")
                val defaultTank = Tank.createDefault()
                tankRef.updateChildren(defaultTank.toMap()).await()
                Log.i(TAG, "Default Tank 1 created successfully")
            } else {
                Log.i(TAG, "Tank 1 already exists")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure default tank exists", e)
        }
    }

    /**
     * Gets a tank by ID from the cache.
     * @param tankId The tank identifier
     * @return Tank or null if not found
     */
    fun getTankById(tankId: String): Tank? {
        return _cachedTanks.value.find { it.id == tankId }
    }

    /**
     * Gets all tanks that use a specific camera IP.
     * @param cameraIp The camera IP address
     * @return List of tanks using that IP
     */
    fun getTanksByCameraIp(cameraIp: String): List<Tank> {
        return _cachedTanks.value.filter { it.cameraIp == cameraIp }
    }

    /**
     * Parses a Firebase snapshot into a Tank object.
     * Handles both web app format and Android format.
     * Web uses: name, createdAt directly under tanks/{tankId}/
     * Android uses: displayName, id, number, cameraIp, createdAt, updatedAt
     */
    private fun parseTank(snapshot: DataSnapshot, tankIdFromPath: String? = null): Tank? {
        return try {
            // Get tank ID - prefer from snapshot, fall back to path-based ID
            val id = snapshot.child("id").getValue(String::class.java)
                ?: tankIdFromPath
                ?: return null

            // Extract number from ID if not present in data
            val numberFromData = snapshot.child("number").getValue(Int::class.java)
            val number = numberFromData ?: Tank.extractNumber(id)

            // Handle both web format (name) and Android format (displayName)
            val displayName = snapshot.child("name").getValue(String::class.java)
                ?: snapshot.child("displayName").getValue(String::class.java)
                ?: "Tank $number"

            val cameraIp = snapshot.child("cameraIp").getValue(String::class.java)
                ?: Tank.DEFAULT_CAMERA_IP

            val createdAt = snapshot.child("createdAt").getValue(Long::class.java)
                ?: 0L

            val updatedAt = snapshot.child("updatedAt").getValue(Long::class.java)
                ?: 0L

            // Web app specific fields (optional)
            val crabInDate = snapshot.child("crab_in_date").getValue(String::class.java)
            val crabOutDate = snapshot.child("crab_out_date").getValue(String::class.java)
            val initialWeight = snapshot.child("initial_weight").getValue(Double::class.java)

            Tank(
                id = id,
                displayName = displayName,
                number = number,
                cameraIp = cameraIp,
                createdAt = createdAt,
                updatedAt = updatedAt,
                crabInDate = crabInDate,
                crabOutDate = crabOutDate,
                initialWeight = initialWeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tank snapshot", e)
            null
        }
    }

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
