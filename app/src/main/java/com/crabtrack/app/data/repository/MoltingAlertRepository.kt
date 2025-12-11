package com.crabtrack.app.data.repository

import android.util.Log
import com.crabtrack.app.data.model.CameraToTankMapper
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.data.model.MoltingFeedback
import com.crabtrack.app.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing molting alerts.
 *
 * Listens to global /molting_alerts path from ML detection system,
 * and copies high-confidence alerts to user's personal path.
 *
 * Supports multi-tank alerts: when an alert comes in for a camera IP,
 * it's associated with ALL tanks that use that camera IP.
 */
@Singleton
class MoltingAlertRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    private val tankRepository: TankRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MoltingAlertRepo"
        private const val GLOBAL_ALERTS_PATH = "molting_alerts"
        private const val USER_ALERTS_PATH = "molting_alerts"
    }

    private var globalListener: ChildEventListener? = null
    private val processedAlertIds = mutableSetOf<String>()

    // Track highest confidence per tank - only write alert if confidence is higher
    private val tankHighestConfidence = mutableMapOf<String, Double>()

    /**
     * Starts monitoring the global /molting_alerts path.
     * When a high-confidence alert is detected, it's copied to the user's path.
     */
    fun startMonitoring() {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "Cannot start monitoring: No user logged in")
            return
        }

        if (globalListener != null) {
            Log.d(TAG, "Already monitoring global alerts")
            return
        }

        // Initialize tankHighestConfidence from existing Firebase data
        scope.launch {
            initializeHighestConfidenceFromFirebase(uid)
        }

        val globalRef = firebaseDatabase.getReference(GLOBAL_ALERTS_PATH)

        globalListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                processAlert(snapshot, uid)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                processAlert(snapshot, uid)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Optional: handle removal if needed
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not typically used
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Global alerts listener cancelled: ${error.message}")
            }
        }

        globalRef.addChildEventListener(globalListener!!)
        Log.i(TAG, "Started monitoring global molting alerts")
    }

    /**
     * Stops monitoring the global alerts path.
     */
    fun stopMonitoring() {
        globalListener?.let { listener ->
            firebaseDatabase.getReference(GLOBAL_ALERTS_PATH).removeEventListener(listener)
            globalListener = null
            Log.i(TAG, "Stopped monitoring global molting alerts")
        }
    }

    /**
     * Initializes tankHighestConfidence from existing alerts in Firebase.
     * This ensures we don't write duplicate alerts for tanks that already have alerts.
     */
    private suspend fun initializeHighestConfidenceFromFirebase(uid: String) {
        try {
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertId = alertSnapshot.key ?: return@forEach
                val alert = parseAlert(alertId, alertSnapshot)
                if (alert != null) {
                    val currentHighest = tankHighestConfidence[alert.tankId] ?: 0.0
                    if (alert.confidence > currentHighest) {
                        tankHighestConfidence[alert.tankId] = alert.confidence
                    }
                }
            }
            Log.i(TAG, "Initialized tankHighestConfidence: $tankHighestConfidence")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tankHighestConfidence", e)
        }
    }

    /**
     * Processes an alert from the global path.
     * Only writes alert if:
     * 1. Confidence >= 80%
     * 2. Confidence is HIGHER than the previous alert for each tank
     *
     * Multi-tank support: Creates alerts for ALL tanks that use the same camera IP.
     */
    private fun processAlert(snapshot: DataSnapshot, uid: String) {
        val alertId = snapshot.key ?: return

        // Skip if already processed
        if (alertId in processedAlertIds) {
            return
        }

        scope.launch {
            try {
                val alert = parseAlert(alertId, snapshot)
                if (alert == null) {
                    Log.w(TAG, "Failed to parse alert: $alertId")
                    return@launch
                }

                // Only process high-confidence alerts (>= 80%)
                if (!alert.isHighConfidence()) {
                    Log.d(TAG, "Alert $alertId below confidence threshold: ${alert.confidence}")
                    processedAlertIds.add(alertId)
                    return@launch
                }

                // Get all tanks that use this camera IP
                val tanksWithThisCamera = tankRepository.getTanksByCameraIp(alert.cameraIp)

                if (tanksWithThisCamera.isEmpty()) {
                    // Fallback to legacy CameraToTankMapper for backward compatibility
                    val tankId = CameraToTankMapper.getTankId(alert.cameraIp)
                    if (tankId == null) {
                        Log.w(TAG, "Unknown camera IP and no tanks configured: ${alert.cameraIp}")
                        processedAlertIds.add(alertId)
                        return@launch
                    }
                    // Process for single tank (legacy behavior)
                    processAlertForTank(uid, alert, tankId)
                } else {
                    // Process for ALL tanks with this camera IP
                    Log.i(TAG, "Processing alert for ${tanksWithThisCamera.size} tanks with camera IP: ${alert.cameraIp}")
                    tanksWithThisCamera.forEach { tank ->
                        processAlertForTank(uid, alert, tank.id)
                    }
                }

                processedAlertIds.add(alertId)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alert $alertId", e)
            }
        }
    }

    /**
     * Processes an alert for a specific tank.
     * Only writes if confidence is higher than existing.
     */
    private suspend fun processAlertForTank(uid: String, alert: MoltingAlert, tankId: String) {
        // Check if this alert has HIGHER confidence than existing one for this tank
        val currentHighest = tankHighestConfidence[tankId] ?: 0.0
        if (alert.confidence <= currentHighest) {
            Log.d(TAG, "Alert ${alert.id} confidence ${alert.confidence} not higher than existing $currentHighest for $tankId")
            return
        }

        // Delete existing alerts for this tank before writing new one
        deleteExistingAlertsForTank(uid, tankId)

        // Write new alert to user's path with unique ID per tank
        val alertWithTank = alert.copy(
            id = "${alert.id}_$tankId",  // Unique ID per tank
            tankId = tankId
        )
        writeAlertToUser(uid, alertWithTank)

        // Update tracking
        tankHighestConfidence[tankId] = alert.confidence

        Log.i(TAG, "Wrote alert to user path: ${alertWithTank.id} (tank: $tankId, confidence: ${alert.confidence})")
    }

    /**
     * Deletes all existing alerts for a specific tank from user's path.
     * Called before writing a new higher-confidence alert.
     */
    private suspend fun deleteExistingAlertsForTank(uid: String, tankId: String) {
        try {
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertCameraIp = alertSnapshot.child("camera_ip").getValue(String::class.java)
                val alertTankId = alertCameraIp?.let { CameraToTankMapper.getTankId(it) }

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted old alert for tank $tankId: ${alertSnapshot.key}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting existing alerts for tank $tankId", e)
        }
    }

    /**
     * Writes an alert to the user's personal alerts path.
     */
    private suspend fun writeAlertToUser(uid: String, alert: MoltingAlert) {
        val userAlertRef = firebaseDatabase
            .getReference("users/$uid/$USER_ALERTS_PATH/${alert.id}")

        userAlertRef.setValue(alert.toFirebaseMap()).await()
    }

    /**
     * Observes alerts from the user's personal path.
     * Returns only unread alerts, sorted by timestamp (newest first).
     */
    val userAlerts: Flow<List<MoltingAlert>> = callbackFlow {
        val uid = getCurrentUserId()
        if (uid == null) {
            Log.w(TAG, "No user logged in, emitting empty list")
            trySend(emptyList())
            return@callbackFlow
        }

        val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    try {
                        val alerts = mutableListOf<MoltingAlert>()

                        snapshot.children.forEach { alertSnapshot ->
                            val alertId = alertSnapshot.key ?: return@forEach
                            val alert = parseAlert(alertId, alertSnapshot)
                            if (alert != null && alert.status == MoltingAlert.STATUS_UNREAD) {
                                alerts.add(alert)
                            }
                        }

                        // Filter to one alert per tank - keep only highest confidence
                        val highestPerTank = alerts
                            .groupBy { it.tankId }
                            .mapValues { (_, tankAlerts) -> tankAlerts.maxByOrNull { it.confidence } }
                            .values
                            .filterNotNull()

                        // Sort by timestamp (newest first)
                        val sortedAlerts = highestPerTank.sortedByDescending { it.timestamp }

                        trySend(sortedAlerts)
                        Log.i(TAG, "User alerts updated: ${sortedAlerts.size} alerts (filtered to one per tank)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user alerts", e)
                        trySend(emptyList())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User alerts listener cancelled: ${error.message}")
                scope.launch {
                    trySend(emptyList())
                }
            }
        }

        userAlertsRef.addValueEventListener(listener)

        awaitClose {
            userAlertsRef.removeEventListener(listener)
            Log.i(TAG, "User alerts listener removed")
        }
    }

    /**
     * Returns alerts for a specific tank.
     */
    fun alertsForTank(tankId: String): Flow<List<MoltingAlert>> {
        return userAlerts.map { alerts ->
            alerts.filter { it.tankId == tankId }
        }
    }

    /**
     * Marks an alert as read.
     */
    suspend fun markAsRead(alertId: String): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid == null) {
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            val alertRef = firebaseDatabase
                .getReference("users/$uid/$USER_ALERTS_PATH/$alertId/status")

            alertRef.setValue(MoltingAlert.STATUS_READ).await()

            Log.i(TAG, "Alert marked as read: $alertId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark alert as read: $alertId", e)
            Result.failure(e)
        }
    }

    /**
     * Dismisses an alert as false alarm.
     * - Deletes alert from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Logs feedback to /molting_feedback for ML improvement
     */
    suspend fun dismissAsFalseAlarm(alert: MoltingAlert, isFalseAlarm: Boolean): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete alert from user's path
            firebaseDatabase
                .getReference("users/$uid/$USER_ALERTS_PATH/${alert.id}")
                .removeValue()
                .await()

            // 2. Reset confidence threshold for this tank
            tankHighestConfidence.remove(alert.tankId)
            processedAlertIds.remove(alert.id)

            // 3. Log feedback if marked as false alarm
            if (isFalseAlarm) {
                val feedbackRef = firebaseDatabase.getReference("molting_feedback").push()
                feedbackRef.setValue(mapOf(
                    "alert_id" to alert.id,
                    "camera_ip" to alert.cameraIp,
                    "confidence" to alert.confidence,
                    "detection_class" to alert.detectionClass,
                    "feedback" to "false_alarm",
                    "timestamp" to ServerValue.TIMESTAMP,
                    "user_id" to uid
                )).await()
            }

            Log.i(TAG, "Dismissed alert ${alert.id}, false_alarm=$isFalseAlarm")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss alert", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a Firebase snapshot into a MoltingAlert.
     */
    private fun parseAlert(alertId: String, snapshot: DataSnapshot): MoltingAlert? {
        return try {
            val cameraIp = snapshot.child("camera_ip").getValue(String::class.java) ?: return null
            val confidence = snapshot.child("confidence").getValue(Double::class.java) ?: return null
            val detectionClass = snapshot.child("detection_class").getValue(String::class.java) ?: "Unknown"
            val status = snapshot.child("status").getValue(String::class.java) ?: MoltingAlert.STATUS_UNREAD
            val timestamp = snapshot.child("timestamp").getValue(String::class.java) ?: ""
            val imagePath = snapshot.child("image_path").getValue(String::class.java)

            val tankId = snapshot.child("tank_id").getValue(String::class.java)
                ?: CameraToTankMapper.getTankId(cameraIp)
                ?: CameraToTankMapper.getTankIdOrDefault(cameraIp)

            MoltingAlert(
                id = alertId,
                cameraIp = cameraIp,
                confidence = confidence,
                detectionClass = detectionClass,
                status = status,
                timestamp = timestamp,
                tankId = tankId,
                imagePath = imagePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing alert snapshot: $alertId", e)
            null
        }
    }

    /**
     * Clears molting status for a tank.
     * - Deletes all alerts for this tank from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Logs positive feedback (confirmed molting) for ML improvement
     */
    suspend fun clearMoltingStatus(tankId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete all alerts for this tank from user's path
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertTankId = alertSnapshot.child("tank_id").getValue(String::class.java)
                    ?: CameraToTankMapper.getTankId(
                        alertSnapshot.child("camera_ip").getValue(String::class.java) ?: ""
                    )

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted alert for tank $tankId: ${alertSnapshot.key}")
                }
            }

            // 2. Reset confidence threshold
            val previousConfidence = tankHighestConfidence.remove(tankId)

            // 3. Log positive feedback for ML improvement
            if (previousConfidence != null) {
                val feedbackRef = firebaseDatabase.getReference("molting_feedback").push()
                feedbackRef.setValue(mapOf(
                    "tank_id" to tankId,
                    "confidence" to previousConfidence,
                    "feedback" to "confirmed_molting",
                    "timestamp" to ServerValue.TIMESTAMP,
                    "user_id" to uid
                )).await()
            }

            Log.i(TAG, "Cleared molting status for $tankId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear molting status", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a tank has active molting status.
     */
    fun hasMoltingStatus(tankId: String): Boolean {
        return tankHighestConfidence.containsKey(tankId)
    }

    /**
     * Dismisses a tank's molting status as false alarm.
     * - Deletes all alerts for this tank from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Logs false alarm feedback to /molting_feedback for ML improvement
     */
    suspend fun dismissTankAsFalseAlarm(tankId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete all alerts for this tank from user's path
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertTankId = alertSnapshot.child("tank_id").getValue(String::class.java)
                    ?: CameraToTankMapper.getTankId(
                        alertSnapshot.child("camera_ip").getValue(String::class.java) ?: ""
                    )

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted alert for tank $tankId: ${alertSnapshot.key}")
                }
            }

            // 2. Get the confidence before removing from tracking
            val previousConfidence = tankHighestConfidence.remove(tankId)

            // 3. Log false alarm feedback for ML improvement
            if (previousConfidence != null) {
                val feedbackRef = firebaseDatabase.getReference("molting_feedback").push()
                feedbackRef.setValue(mapOf(
                    "tank_id" to tankId,
                    "confidence" to previousConfidence,
                    "feedback" to "false_alarm",
                    "timestamp" to ServerValue.TIMESTAMP,
                    "user_id" to uid
                )).await()
            }

            Log.i(TAG, "Dismissed tank $tankId as false alarm")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss tank as false alarm", e)
            Result.failure(e)
        }
    }

    /**
     * Submits feedback for a molting alert with full alert details.
     * Used for ML model improvement.
     *
     * @param alert The alert to submit feedback for
     * @param feedbackType One of: "confirmed_molting", "confirmed_still_molting", "false_alarm"
     */
    suspend fun submitFeedback(alert: MoltingAlert, feedbackType: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val feedback = MoltingFeedback.fromAlert(alert, uid, feedbackType)
            val feedbackRef = firebaseDatabase.getReference("molting_feedback").push()
            feedbackRef.setValue(feedback.toFirebaseMap()).await()

            Log.i(TAG, "Submitted feedback for alert ${alert.id}: $feedbackType")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit feedback for alert ${alert.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Clears molting status for a tank with full alert details for feedback.
     * - Deletes all alerts for this tank from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Logs positive feedback with full alert details for ML improvement
     *
     * @param tankId The tank ID to clear status for
     * @param alert Optional alert to include full details in feedback
     */
    suspend fun clearMoltingStatusWithAlert(tankId: String, alert: MoltingAlert?): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete all alerts for this tank from user's path
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertTankId = alertSnapshot.child("tank_id").getValue(String::class.java)
                    ?: CameraToTankMapper.getTankId(
                        alertSnapshot.child("camera_ip").getValue(String::class.java) ?: ""
                    )

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted alert for tank $tankId: ${alertSnapshot.key}")
                }
            }

            // 2. Reset confidence threshold
            tankHighestConfidence.remove(tankId)

            // 3. Log positive feedback for ML improvement with full details
            if (alert != null) {
                submitFeedback(alert, MoltingFeedback.FEEDBACK_CONFIRMED_MOLTING)
            }

            Log.i(TAG, "Cleared molting status for $tankId with full feedback")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear molting status", e)
            Result.failure(e)
        }
    }

    /**
     * Dismisses a tank's molting status as false alarm with full alert details.
     * - Deletes all alerts for this tank from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Logs false alarm feedback with full alert details for ML improvement
     *
     * @param tankId The tank ID to dismiss
     * @param alert Optional alert to include full details in feedback
     */
    suspend fun dismissTankAsFalseAlarmWithAlert(tankId: String, alert: MoltingAlert?): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete all alerts for this tank from user's path
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertTankId = alertSnapshot.child("tank_id").getValue(String::class.java)
                    ?: CameraToTankMapper.getTankId(
                        alertSnapshot.child("camera_ip").getValue(String::class.java) ?: ""
                    )

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted alert for tank $tankId: ${alertSnapshot.key}")
                }
            }

            // 2. Reset confidence threshold
            tankHighestConfidence.remove(tankId)

            // 3. Log false alarm feedback with full details for ML improvement
            if (alert != null) {
                submitFeedback(alert, MoltingFeedback.FEEDBACK_FALSE_ALARM)
            }

            Log.i(TAG, "Dismissed tank $tankId as false alarm with full feedback")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss tank as false alarm", e)
            Result.failure(e)
        }
    }

    /**
     * Dismisses a tank's molting status without creating feedback.
     * - Deletes all alerts for this tank from user's path
     * - Resets tankHighestConfidence so new alerts can trigger
     * - Does NOT log any feedback
     *
     * @param tankId The tank ID to dismiss
     */
    suspend fun dismissWithoutFeedback(tankId: String): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Delete all alerts for this tank from user's path
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertTankId = alertSnapshot.child("tank_id").getValue(String::class.java)
                    ?: CameraToTankMapper.getTankId(
                        alertSnapshot.child("camera_ip").getValue(String::class.java) ?: ""
                    )

                if (alertTankId == tankId) {
                    alertSnapshot.ref.removeValue().await()
                    Log.d(TAG, "Deleted alert for tank $tankId: ${alertSnapshot.key}")
                }
            }

            // 2. Reset confidence threshold
            tankHighestConfidence.remove(tankId)

            // 3. NO feedback logged

            Log.i(TAG, "Dismissed tank $tankId without feedback")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss tank without feedback", e)
            Result.failure(e)
        }
    }

    /**
     * Acknowledges an alert as "still molting" - marks as read and creates feedback.
     * - Marks alert status as "read"
     * - Creates feedback with "confirmed_still_molting"
     * - Does NOT delete alert or reset confidence tracker
     *
     * @param alert The alert to acknowledge
     */
    suspend fun acknowledgeStillMolting(alert: MoltingAlert): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {
            // 1. Mark alert as read
            markAsRead(alert.id)

            // 2. Submit feedback
            submitFeedback(alert, MoltingFeedback.FEEDBACK_CONFIRMED_STILL_MOLTING)

            // 3. Do NOT reset tracker or delete alert

            Log.i(TAG, "Acknowledged alert ${alert.id} as still molting")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acknowledge alert as still molting", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the current alert for a specific tank from the cached alerts.
     * Returns null if no alert exists for this tank.
     */
    suspend fun getAlertForTank(tankId: String): MoltingAlert? {
        val uid = getCurrentUserId() ?: return null

        return try {
            val userAlertsRef = firebaseDatabase.getReference("users/$uid/$USER_ALERTS_PATH")
            val snapshot = userAlertsRef.get().await()

            snapshot.children.forEach { alertSnapshot ->
                val alertId = alertSnapshot.key ?: return@forEach
                val alert = parseAlert(alertId, alertSnapshot)

                if (alert != null) {
                    val alertTankId = alert.tankId
                    if (alertTankId == tankId) {
                        return alert
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get alert for tank $tankId", e)
            null
        }
    }

    private fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}
