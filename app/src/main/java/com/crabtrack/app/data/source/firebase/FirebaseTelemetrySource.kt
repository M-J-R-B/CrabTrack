package com.crabtrack.app.data.source.firebase

import android.util.Log
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Firebase Realtime Database implementation of TelemetrySource.
 * Listens to /crabtrack/realtime path for real-time water quality sensor data.
 *
 * Expected Firebase data structure:
 * {
 *   "ph": 4.34186,
 *   "salinity": 0.68968,
 *   "tds": 690.42169,
 *   "temperature": 29.625,
 *   "timestamp": 143452916,
 *   "turbidity": 0
 * }
 */
class FirebaseTelemetrySource @Inject constructor() : TelemetrySource {

    companion object {
        private const val TAG = "FirebaseTelemetrySource"
        private const val REALTIME_PATH = "crabtrack/realtime"
    }

    override fun stream(config: TelemetrySourceConfig): Flow<WaterReading> = callbackFlow {
        Log.i(TAG, "=== FIREBASE STREAM STARTING ===")
        Log.i(TAG, "Initializing Firebase listener for tank: ${config.tankId}")

        val database = FirebaseDatabase.getInstance()
        val realtimeRef = database.getReference(REALTIME_PATH)

        Log.i(TAG, "Firebase Database instance: ${database}")
        Log.i(TAG, "Firebase Database URL: ${database.reference.toString()}")
        Log.i(TAG, "Listening to path: $REALTIME_PATH")
        Log.i(TAG, "Full reference path: ${realtimeRef.toString()}")

        // Create value event listener for real-time updates
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    Log.i(TAG, "=== FIREBASE ON DATA CHANGE ===")
                    Log.i(TAG, "Firebase onDataChange triggered")
                    Log.i(TAG, "Snapshot exists: ${snapshot.exists()}")
                    Log.i(TAG, "Snapshot children count: ${snapshot.childrenCount}")
                    Log.i(TAG, "Snapshot key: ${snapshot.key}")
                    Log.i(TAG, "Snapshot value: ${snapshot.value}")

                    if (!snapshot.exists()) {
                        Log.w(TAG, "⚠️ No data available at $REALTIME_PATH - Firebase path may be wrong or empty")
                        Log.w(TAG, "Check Firebase Console to verify data exists at: $REALTIME_PATH")
                        // Emit default reading to prevent UI from blocking
                        trySend(createDefaultReading(config.tankId))
                        return
                    }

                    // Log all available fields for debugging
                    snapshot.children.forEach { child ->
                        Log.d(TAG, "Field: ${child.key} = ${child.value}")
                    }

                    // Parse Firebase data
                    val ph = snapshot.child("ph").getValue(Double::class.java) ?: 0.0
                    val salinity = snapshot.child("salinity").getValue(Double::class.java) ?: 0.0
                    val tds = snapshot.child("tds").getValue(Double::class.java) ?: 0.0
                    val temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                    val turbidity = snapshot.child("turbidity").getValue(Double::class.java) ?: 0.0

                    // Handle timestamp - could be in seconds or milliseconds
                    val rawTimestamp = snapshot.child("timestamp").getValue(Long::class.java)
                    val timestampMs = if (rawTimestamp != null) {
                        // If timestamp is less than 10 digits, it's likely in seconds
                        if (rawTimestamp < 10000000000L) {
                            rawTimestamp * 1000 // Convert seconds to milliseconds
                        } else {
                            rawTimestamp // Already in milliseconds
                        }
                    } else {
                        System.currentTimeMillis()
                    }

                    val reading = WaterReading(
                        tankId = config.tankId,
                        timestampMs = timestampMs,
                        pH = ph,
                        dissolvedOxygenMgL = null, // Not provided by ESP32
                        salinityPpt = salinity,
                        ammoniaMgL = null, // Not provided by ESP32
                        temperatureC = temperature,
                        waterLevelCm = null, // Not provided by ESP32
                        tdsPpm = tds,
                        turbidityNTU = turbidity
                    )

                    Log.i(TAG, "Firebase data parsed successfully")
                    Log.d(TAG, "Reading: pH=$ph, temp=$temperature°C, salinity=$salinity ppt, tds=$tds ppm, turbidity=$turbidity NTU")
                    Log.d(TAG, "Timestamp: $timestampMs (raw: $rawTimestamp)")

                    val sendResult = trySend(reading)
                    if (sendResult.isSuccess) {
                        Log.i(TAG, "✓ Reading emitted successfully to Flow")
                    } else {
                        Log.e(TAG, "✗ Failed to emit reading - channel may be closed or full")
                        Log.e(TAG, "Send result: $sendResult")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Firebase data: ${e.message}", e)
                    e.printStackTrace()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "=== FIREBASE LISTENER CANCELLED ===")
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                Log.e(TAG, "Error code: ${error.code}, Details: ${error.details}")
                Log.e(TAG, "This usually indicates permission denied or network issues")
                // Emit default reading to prevent UI from blocking
                trySend(createDefaultReading(config.tankId))
            }
        }

        // Attach listener
        try {
            Log.i(TAG, "Attempting to attach Firebase listener...")
            realtimeRef.addValueEventListener(listener)
            Log.i(TAG, "✓ Firebase listener attached successfully to $REALTIME_PATH")
            Log.i(TAG, "Waiting for Firebase data changes...")
            // Emit initial default reading to unblock UI (like MQTT does)
            trySend(createDefaultReading(config.tankId))
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to attach Firebase listener: ${e.message}", e)
            e.printStackTrace()
            // Emit default reading on exception
            trySend(createDefaultReading(config.tankId))
        }

        // Clean up on flow cancellation
        awaitClose {
            Log.i(TAG, "Removing Firebase listener from $REALTIME_PATH")
            realtimeRef.removeEventListener(listener)
        }
    }

    /**
     * Create a default reading when Firebase is unavailable or has errors
     */
    private fun createDefaultReading(tankId: String): WaterReading {
        return WaterReading(
            tankId = tankId,
            timestampMs = System.currentTimeMillis(),
            pH = 0.0,
            dissolvedOxygenMgL = null,
            salinityPpt = 0.0,
            ammoniaMgL = null,
            temperatureC = 0.0,
            waterLevelCm = null,
            tdsPpm = 0.0,
            turbidityNTU = 0.0
        )
    }
}