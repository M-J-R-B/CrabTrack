package com.crabtrack.app.data.source.mqtt

import android.content.Context
import android.util.Log
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import com.crabtrack.app.data.util.NetworkTypeDetector
import com.crabtrack.app.data.util.DataUsageTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import javax.inject.Inject

/**
 * MQTT-based telemetry source that subscribes to an MQTT broker
 * and emits WaterReading objects from received messages.
 *
 * This source automatically optimizes MQTT connection parameters based on
 * the current network type (WiFi vs Mobile Data) and data saver mode status.
 *
 * Expected JSON payload format from ESP32:
 * {
 *   "t": 24.5,  // temperature (°C)
 *   "d": 350,   // TDS (ppm)
 *   "s": 34.0,  // salinity (ppt)
 *   "u": 12.5,  // turbidity (NTU)
 *   "p": 7.8    // pH
 * }
 */
class MqttTelemetrySource @Inject constructor(
    private val context: Context,
    private val networkTypeDetector: NetworkTypeDetector,
    private val dataUsageTracker: DataUsageTracker
) : TelemetrySource {

    companion object {
        private const val TAG = "MqttTelemetrySource"
    }

    override fun stream(config: TelemetrySourceConfig): Flow<WaterReading> = callbackFlow {
        // Get network-optimized MQTT configuration
        val isWiFi = networkTypeDetector.isOnWiFi()
        val dataSaverEnabled = dataUsageTracker.isDataSaverEnabled().first()
        val mqttConfig = MqttConfig.forNetworkType(isWiFi, dataSaverEnabled)

        Log.i(TAG, "Initializing MQTT with config: WiFi=$isWiFi, DataSaver=$dataSaverEnabled")
        Log.i(TAG, "MQTT settings: keepAlive=${mqttConfig.keepAliveInterval}s, QoS=${mqttConfig.qos}")

        val client = MqttAndroidClient(context, mqttConfig.brokerUrl, mqttConfig.clientId)

        // Configure connection options with network-optimized parameters
        val connectOptions = MqttConnectOptions().apply {
            userName = mqttConfig.username
            password = mqttConfig.password.toCharArray()
            isCleanSession = mqttConfig.cleanSession
            connectionTimeout = mqttConfig.connectionTimeout
            keepAliveInterval = mqttConfig.keepAliveInterval
            isAutomaticReconnect = mqttConfig.autoReconnect
            maxReconnectDelay = mqttConfig.maxReconnectDelay
        }

        // Set up callbacks
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "Connection lost: ${cause?.message}")
                // Emit a default reading to prevent UI from hanging
                trySend(createDefaultReading(config.tankId))
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.let {
                    try {
                        val payload = String(it.payload)
                        Log.d(TAG, "Message arrived on topic $topic: $payload")

                        val reading = parseMessage(payload, config.tankId)
                        trySend(reading)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message", e)
                        // Emit default reading on parse error
                        trySend(createDefaultReading(config.tankId))
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not used for subscriber
            }
        })

        // Connect to broker
        try {
            val networkType = if (isWiFi) "WiFi" else "Mobile Data"
            Log.i(TAG, "Connecting to MQTT broker: ${mqttConfig.brokerUrl} via $networkType")

            client.connect(connectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Connected to MQTT broker successfully")

                    // Subscribe to topic
                    try {
                        client.subscribe(mqttConfig.topic, mqttConfig.qos, null, object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                Log.i(TAG, "Subscribed to topic: ${mqttConfig.topic}")
                                // Emit initial default reading to unblock UI
                                trySend(createDefaultReading(config.tankId))
                            }

                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.e(TAG, "Failed to subscribe to topic", exception)
                                // Emit default reading on subscription failure
                                trySend(createDefaultReading(config.tankId))
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "Error subscribing to topic", e)
                        trySend(createDefaultReading(config.tankId))
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to connect to MQTT broker", exception)
                    Log.e(TAG, "Connection failure reason: ${exception?.message}")
                    // Emit default reading on connection failure
                    trySend(createDefaultReading(config.tankId))
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to MQTT broker", e)
            // Emit default reading on exception
            trySend(createDefaultReading(config.tankId))
        }

        // Clean up on flow cancellation
        awaitClose {
            try {
                if (client.isConnected) {
                    client.unsubscribe(mqttConfig.topic)
                    client.disconnect()
                    Log.i(TAG, "Disconnected from MQTT broker")
                }
                client.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from MQTT broker", e)
            }
        }
    }

    /**
     * Create a default reading to prevent UI from blocking when MQTT is unavailable
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

    /**
     * Parse the JSON payload from ESP32 into a WaterReading object.
     *
     * Expected format: {"t":24.5,"d":350,"s":34.0,"u":12.5,"p":7.8,"ts":1638360000}
     *
     * If "ts" (timestamp) field is present:
     * - Values < 10000000000 are treated as Unix seconds (multiply by 1000)
     * - Values >= 10000000000 are treated as Unix milliseconds
     * - Invalid timestamps (uptime counters, future dates) fall back to device time
     *
     * If "ts" is missing, falls back to System.currentTimeMillis() (device time).
     */
    private fun parseMessage(payload: String, tankId: String): WaterReading {
        val json = JSONObject(payload)
        val now = System.currentTimeMillis()

        Log.i(TAG, "=== MQTT TIMESTAMP DIAGNOSTIC ===")
        Log.i(TAG, "MQTT Payload: $payload")
        Log.i(TAG, "Current device time (ms): $now")

        // Try to extract timestamp from payload
        val timestampMs = if (json.has("ts")) {
            val rawTimestamp = json.getLong("ts")
            Log.i(TAG, "Raw timestamp from MQTT 'ts' field: $rawTimestamp")

            // Detect timestamp format
            val isSeconds = rawTimestamp < 10000000000L
            val convertedTimestamp = if (isSeconds) {
                Log.d(TAG, "Raw < 10B, treating as seconds: $rawTimestamp")
                rawTimestamp * 1000 // Convert seconds to milliseconds
            } else {
                Log.d(TAG, "Raw >= 10B, treating as milliseconds: $rawTimestamp")
                rawTimestamp
            }

            Log.i(TAG, "Converted timestamp: $convertedTimestamp")

            // Enhanced sanity check with automatic fallback
            val hourInMs = 3600000L
            val dayInMs = 24 * hourInMs
            val sevenDaysInMs = 7 * dayInMs
            val timeDifference = convertedTimestamp - now
            val timeDifferenceHours = timeDifference / hourInMs

            Log.i(TAG, "Time difference: $timeDifference ms ($timeDifferenceHours hours)")

            // Check if timestamp is reasonable (within ±7 days of device time)
            // Current time should be > 1.7 trillion ms (after Nov 2023)
            val isReasonable = Math.abs(timeDifference) <= sevenDaysInMs &&
                             convertedTimestamp > 1700000000000L  // After Nov 2023

            if (!isReasonable) {
                Log.e(TAG, "❌ INVALID MQTT TIMESTAMP DETECTED!")
                Log.e(TAG, "Converted timestamp: $convertedTimestamp")
                Log.e(TAG, "Device time: $now")
                Log.e(TAG, "Difference: $timeDifferenceHours hours")
                Log.e(TAG, "This timestamp appears to be:")

                if (convertedTimestamp < 1000000000000L) {
                    Log.e(TAG, "  → Way too old (before year 2001)")
                    Log.e(TAG, "  → Likely an ESP32 uptime counter, not Unix epoch")
                } else {
                    Log.e(TAG, "  → Way too far in the future")
                }

                Log.w(TAG, "⚠️ FALLBACK: Using device time instead")
                now
            } else {
                Log.i(TAG, "✓ Timestamp is reasonable, using converted value")
                convertedTimestamp
            }
        } else {
            // No timestamp in payload - use device time
            Log.w(TAG, "No 'ts' field in MQTT payload, using device time: $now")
            now
        }

        Log.i(TAG, "Final timestamp: $timestampMs")
        Log.i(TAG, "Formatted: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestampMs))}")
        Log.i(TAG, "=================================")

        return WaterReading(
            tankId = tankId,
            timestampMs = timestampMs,
            pH = json.optDouble("p", 7.0),
            dissolvedOxygenMgL = null, // Not provided by ESP32
            salinityPpt = json.optDouble("s", 0.0),
            ammoniaMgL = null, // Not provided by ESP32
            temperatureC = json.optDouble("t", 0.0),
            waterLevelCm = null, // Not provided by ESP32
            tdsPpm = json.optDouble("d", 0.0),
            turbidityNTU = json.optDouble("u", 0.0)
        )
    }
}
