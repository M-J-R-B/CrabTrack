package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.FirebaseAlert
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for listening to alerts stored in Firebase Realtime Database.
 * Path: users/{uid}/alerts/{alertId}
 */
@Singleton
class AlertRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AlertRepository"
        private const val ALERTS_PATH = "alerts"
        private const val DUPLICATE_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes
        private const val CLEANUP_INTERVAL_MS = 60 * 1000L     // Cleanup every 1 minute
    }

    /**
     * Deduplication key: parameter + threshold_type combination.
     * Alerts with the same key within the time window are considered duplicates.
     */
    private data class AlertDeduplicationKey(
        val parameter: String,
        val thresholdType: String
    )

    /**
     * Tracks when an alert was last shown for a specific deduplication key.
     */
    private data class TrackedAlertEntry(
        val timestamp: Long,
        val alertId: String
    )

    // In-memory tracking for duplicate prevention
    private val recentlyShownAlerts = ConcurrentHashMap<AlertDeduplicationKey, TrackedAlertEntry>()
    private var lastCleanupTime = System.currentTimeMillis()

    private var listener: ValueEventListener? = null
    private var currentListenerRef: com.google.firebase.database.DatabaseReference? = null

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Observes alerts from Firebase in real-time as a Flow.
     * Emits list of alerts sorted by timestamp (newest first).
     */
    fun observeAlerts(): Flow<List<Alert>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in, cannot observe alerts")
            trySend(emptyList())
            _isConnected.value = false
            awaitClose { }
            return@callbackFlow
        }

        val alertsRef = firebaseDatabase.getReference("users/$uid/$ALERTS_PATH")

        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val alertsList = parseAlertsFromFirebase(snapshot)
                        _alerts.value = alertsList
                        _isConnected.value = true
                        trySend(alertsList)
                        Log.i(TAG, "Alerts received from Firebase: ${alertsList.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Firebase alerts", e)
                        trySend(emptyList())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase alerts listener cancelled: ${error.message}")
                _isConnected.value = false
                trySend(emptyList())
            }
        }

        alertsRef.addValueEventListener(valueListener)
        currentListenerRef = alertsRef
        listener = valueListener

        awaitClose {
            alertsRef.removeEventListener(valueListener)
            currentListenerRef = null
            listener = null
            Log.i(TAG, "Firebase alerts listener removed")
        }
    }

    /**
     * Starts listening to alerts. Call this when user logs in.
     */
    fun startListening() {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in, cannot start listening")
            return
        }

        // Remove existing listener if any
        stopListening()

        val alertsRef = firebaseDatabase.getReference("users/$uid/$ALERTS_PATH")

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val alertsList = parseAlertsFromFirebase(snapshot)
                        _alerts.value = alertsList
                        _isConnected.value = true
                        Log.i(TAG, "Alerts updated: ${alertsList.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing alerts", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listener cancelled: ${error.message}")
                _isConnected.value = false
            }
        }

        alertsRef.addValueEventListener(listener!!)
        currentListenerRef = alertsRef
        Log.i(TAG, "Started listening to alerts for user: $uid")
    }

    /**
     * Stops listening to alerts. Call this when user logs out.
     */
    fun stopListening() {
        listener?.let { l ->
            currentListenerRef?.removeEventListener(l)
        }
        listener = null
        currentListenerRef = null
        _alerts.value = emptyList()
        _isConnected.value = false
        recentlyShownAlerts.clear()  // Clear deduplication tracking on logout
        Log.i(TAG, "Stopped listening to alerts")
    }

    /**
     * Creates a deduplication key from a FirebaseAlert.
     * Uses parameter + threshold_type as the unique combination.
     */
    private fun createDeduplicationKey(firebaseAlert: FirebaseAlert): AlertDeduplicationKey {
        return AlertDeduplicationKey(
            parameter = firebaseAlert.parameter.lowercase().trim(),
            thresholdType = firebaseAlert.threshold_type.lowercase().trim()
        )
    }

    /**
     * Checks if an alert should be filtered out as a duplicate.
     * An alert is considered a duplicate if another alert with the same
     * parameter + threshold_type was shown within the last 5 minutes.
     */
    private fun isDuplicateAlert(firebaseAlert: FirebaseAlert): Boolean {
        val key = createDeduplicationKey(firebaseAlert)
        val existingEntry = recentlyShownAlerts[key] ?: return false
        val timeSinceLastShown = System.currentTimeMillis() - existingEntry.timestamp
        return timeSinceLastShown < DUPLICATE_WINDOW_MS
    }

    /**
     * Records that an alert has been shown.
     */
    private fun trackAlertShown(firebaseAlert: FirebaseAlert, alertId: String) {
        val key = createDeduplicationKey(firebaseAlert)
        val createdAtLong = FirebaseAlert.toLong(firebaseAlert.created_at)
        recentlyShownAlerts[key] = TrackedAlertEntry(
            timestamp = if (createdAtLong > 0) createdAtLong else System.currentTimeMillis(),
            alertId = alertId
        )
    }

    /**
     * Removes expired entries from the tracking map to prevent memory growth.
     */
    private fun cleanupExpiredEntries() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS) return

        lastCleanupTime = currentTime
        val expiredKeys = recentlyShownAlerts.entries
            .filter { currentTime - it.value.timestamp >= DUPLICATE_WINDOW_MS }
            .map { it.key }

        expiredKeys.forEach { recentlyShownAlerts.remove(it) }
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredKeys.size} expired alert tracking entries")
        }
    }

    /**
     * Parses Firebase snapshot to list of Alert objects.
     * Filters out duplicate alerts (same parameter + threshold_type within 5 minutes).
     * Sorted by timestamp descending (newest first).
     */
    private fun parseAlertsFromFirebase(snapshot: DataSnapshot): List<Alert> {
        // Cleanup expired tracking entries periodically
        cleanupExpiredEntries()

        val alerts = mutableListOf<Alert>()

        // Group alerts by dedup key, keep only most recent per key
        val alertsByKey = mutableMapOf<AlertDeduplicationKey, Pair<String, FirebaseAlert>>()

        snapshot.children.forEach { alertSnapshot ->
            val alertId = alertSnapshot.key ?: return@forEach

            try {
                val firebaseAlert = alertSnapshot.getValue(FirebaseAlert::class.java)
                if (firebaseAlert != null) {
                    val key = createDeduplicationKey(firebaseAlert)
                    val existing = alertsByKey[key]
                    // Keep only the most recent alert for each key
                    val newTimestamp = FirebaseAlert.toLong(firebaseAlert.created_at)
                    val existingTimestamp = existing?.let { FirebaseAlert.toLong(it.second.created_at) } ?: 0L
                    if (existing == null || newTimestamp > existingTimestamp) {
                        alertsByKey[key] = alertId to firebaseAlert
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing alert $alertId", e)
            }
        }

        // Filter duplicates and track shown alerts
        alertsByKey.forEach { (key, pair) ->
            val (alertId, firebaseAlert) = pair
            if (!isDuplicateAlert(firebaseAlert)) {
                alerts.add(firebaseAlert.toAlert(alertId))
                trackAlertShown(firebaseAlert, alertId)
            } else {
                Log.d(TAG, "Filtered duplicate alert: $alertId (key: $key)")
            }
        }

        return alerts.sortedByDescending { it.timestampMs }
    }

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
