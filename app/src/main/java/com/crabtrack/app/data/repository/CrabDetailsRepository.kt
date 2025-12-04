package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.CrabDetails
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
class CrabDetailsRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CrabDetailsRepo"
        private const val TANKS_PATH = "tanks"
        private const val CRAB_DETAILS_PATH = "crab_details"
    }

    private var allTanksListener: ValueEventListener? = null

    /**
     * Observes crab details for all tanks from Firebase in real-time
     * @return Flow emitting a map of tankId to CrabDetails
     */
    fun observeAllCrabDetails(): Flow<Map<String, CrabDetails>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in")
            trySend(emptyMap())
            return@callbackFlow
        }

        val tanksRef = firebaseDatabase.getReference("users/$uid/$TANKS_PATH")

        allTanksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val detailsMap = mutableMapOf<String, CrabDetails>()

                        snapshot.children.forEach { tankSnapshot ->
                            val tankId = tankSnapshot.key ?: return@forEach
                            val crabDetailsSnapshot = tankSnapshot.child(CRAB_DETAILS_PATH)

                            if (crabDetailsSnapshot.exists()) {
                                val details = parseCrabDetails(tankId, crabDetailsSnapshot)
                                if (details != null) {
                                    detailsMap[tankId] = details
                                }
                            }
                        }

                        trySend(detailsMap)
                        Log.i(TAG, "Crab details synced from Firebase: ${detailsMap.size} tanks")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing crab details", e)
                        trySend(emptyMap())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                scope.launch {
                    trySend(emptyMap())
                }
            }
        }

        tanksRef.addValueEventListener(allTanksListener!!)

        awaitClose {
            allTanksListener?.let { tanksRef.removeEventListener(it) }
            Log.i(TAG, "Firebase listener removed")
        }
    }

    /**
     * Observes crab details for a specific tank
     * @param tankId The tank identifier
     * @return Flow emitting CrabDetails or null if not found
     */
    fun observeCrabDetails(tankId: String): Flow<CrabDetails?> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in")
            trySend(null)
            return@callbackFlow
        }

        val detailsRef = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/$tankId/$CRAB_DETAILS_PATH")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        if (snapshot.exists()) {
                            val details = parseCrabDetails(tankId, snapshot)
                            trySend(details)
                        } else {
                            trySend(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing crab details for tank $tankId", e)
                        trySend(null)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                scope.launch {
                    trySend(null)
                }
            }
        }

        detailsRef.addValueEventListener(listener)

        awaitClose {
            detailsRef.removeEventListener(listener)
        }
    }

    /**
     * Saves crab details for a tank to Firebase
     * @param details The CrabDetails to save
     * @return Result indicating success or failure
     */
    suspend fun saveCrabDetails(details: CrabDetails): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val ref = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/${details.tankId}/$CRAB_DETAILS_PATH")

            val dataMap = mutableMapOf<String, Any?>()
            dataMap["tankId"] = details.tankId
            details.crabName?.let { dataMap["crabName"] = it }
            details.placedDate?.let { dataMap["placedDate"] = it }
            details.placedDateMs?.let { dataMap["placedDateMs"] = it }
            details.initialWeightGrams?.let { dataMap["initialWeightGrams"] = it }
            details.removalDate?.let { dataMap["removalDate"] = it }
            details.removalDateMs?.let { dataMap["removalDateMs"] = it }
            details.removalWeightGrams?.let { dataMap["removalWeightGrams"] = it }
            dataMap["updatedAt"] = details.updatedAt
            dataMap["createdAt"] = details.createdAt

            ref.setValue(dataMap).await()

            Log.i(TAG, "Crab details saved for tank: ${details.tankId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crab details", e)
            Result.failure(e)
        }
    }

    /**
     * Resets/clears crab details for a tank (for when a new crab is added)
     * @param tankId The tank identifier
     * @return Result indicating success or failure
     */
    suspend fun resetCrabDetails(tankId: String): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val ref = firebaseDatabase.getReference("users/$uid/$TANKS_PATH/$tankId/$CRAB_DETAILS_PATH")
            ref.removeValue().await()

            Log.i(TAG, "Crab details reset for tank: $tankId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset crab details", e)
            Result.failure(e)
        }
    }

    /**
     * One-time fetch of crab details for a specific tank
     * @param tankId The tank identifier
     * @return CrabDetails or null if not found
     */
    suspend fun fetchCrabDetailsOnce(tankId: String): CrabDetails? {
        val uid = getCurrentUserId()
        if (uid == null) {
            return null
        }

        return try {
            val snapshot = firebaseDatabase
                .getReference("users/$uid/$TANKS_PATH/$tankId/$CRAB_DETAILS_PATH")
                .get()
                .await()

            if (snapshot.exists()) {
                parseCrabDetails(tankId, snapshot)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch crab details for tank $tankId", e)
            null
        }
    }

    /**
     * Parses a Firebase snapshot into CrabDetails
     */
    private fun parseCrabDetails(tankId: String, snapshot: DataSnapshot): CrabDetails? {
        return try {
            CrabDetails(
                tankId = tankId,
                crabName = snapshot.child("crabName").getValue(String::class.java),
                placedDate = snapshot.child("placedDate").getValue(String::class.java),
                placedDateMs = snapshot.child("placedDateMs").getValue(Long::class.java),
                initialWeightGrams = snapshot.child("initialWeightGrams").getValue(Double::class.java),
                removalDate = snapshot.child("removalDate").getValue(String::class.java),
                removalDateMs = snapshot.child("removalDateMs").getValue(Long::class.java),
                removalWeightGrams = snapshot.child("removalWeightGrams").getValue(Double::class.java),
                updatedAt = snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L,
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing crab details snapshot", e)
            null
        }
    }

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
