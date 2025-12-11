package com.crabtrack.app.data.util

import android.util.Log
import com.crabtrack.app.data.model.Tank
import com.crabtrack.app.data.repository.CameraMappingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for generating test molting alerts.
 *
 * Creates fake alerts in the global /molting_alerts path to test
 * the molting detection and display functionality.
 */
@Singleton
class TestAlertGenerator @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseDatabase: FirebaseDatabase,
    private val cameraMappingRepository: CameraMappingRepository
) {
    companion object {
        private const val TAG = "TestAlertGenerator"
        private const val MOLTING_ALERTS_PATH = "molting_alerts"

        // Detection classes from the ML model
        private val DETECTION_CLASSES = listOf("molting-crab", "molted-crab")

        // Confidence range for test alerts (80-99%)
        private const val MIN_CONFIDENCE = 80
        private const val MAX_CONFIDENCE = 99

        // Camera IP base for generating fake IPs
        private const val CAMERA_IP_BASE = "192.168.8."
        private const val CAMERA_IP_OFFSET = 114
    }

    /**
     * Generates a test molting alert for the specified tank.
     *
     * @param tank The tank to generate the alert for
     * @return Result containing the alert ID on success, or error on failure
     */
    suspend fun generateTestAlert(tank: Tank): Result<String> {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "Cannot generate test alert: No user logged in")
            return Result.failure(Exception("Not authenticated"))
        }

        return try {
            // Generate camera IP based on tank number: 192.168.8.(114 + tankNumber)
            val tankNumber = tank.id.replace("tank", "").toIntOrNull() ?: 1
            val cameraIp = "$CAMERA_IP_BASE${CAMERA_IP_OFFSET + tankNumber}"

            // Create unique alert ID with test prefix
            val alertId = "test_${UUID.randomUUID()}"

            // Random confidence between 80-99%
            val confidence = (MIN_CONFIDENCE..MAX_CONFIDENCE).random() / 100.0

            // Random detection class
            val detectionClass = DETECTION_CLASSES.random()

            // ISO 8601 timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())

            // Alert data matching the structure expected by MoltingAlertRepository
            val alertData = mapOf(
                "camera_ip" to cameraIp,
                "confidence" to confidence,
                "detection_class" to detectionClass,
                "timestamp" to timestamp,
                "is_test" to true
            )

            // Write alert to global /molting_alerts path
            firebaseDatabase
                .getReference("$MOLTING_ALERTS_PATH/$alertId")
                .setValue(alertData)
                .await()

            Log.i(TAG, "Test alert created: $alertId for ${tank.displayName}")
            Log.d(TAG, "Alert details: cameraIp=$cameraIp, confidence=$confidence, class=$detectionClass")

            // Also save camera mapping to ensure the alert routes to this tank
            cameraMappingRepository.saveMapping(cameraIp, tank.id, uid)

            Result.success(alertId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate test alert for ${tank.displayName}", e)
            Result.failure(e)
        }
    }

    /**
     * Generates the camera IP for a given tank.
     * Formula: 192.168.8.(114 + tankNumber)
     *
     * @param tank The tank to get the camera IP for
     * @return The generated camera IP address
     */
    fun getCameraIpForTank(tank: Tank): String {
        val tankNumber = tank.id.replace("tank", "").toIntOrNull() ?: 1
        return "$CAMERA_IP_BASE${CAMERA_IP_OFFSET + tankNumber}"
    }
}
