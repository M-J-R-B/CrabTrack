package com.crabtrack.app.data.source.mqtt

import android.content.Context
import android.util.Log
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import javax.inject.Inject

/**
 * MQTT-based telemetry source that subscribes to an MQTT broker
 * and emits WaterReading objects from received messages.
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
    private val mqttConfig: MqttConfig = MqttConfig()
) : TelemetrySource {

    companion object {
        private const val TAG = "MqttTelemetrySource"
    }

    override fun stream(config: TelemetrySourceConfig): Flow<WaterReading> = callbackFlow {
        val client = MqttAndroidClient(context, mqttConfig.brokerUrl, mqttConfig.clientId)

        // Configure connection options
        val connectOptions = MqttConnectOptions().apply {
            userName = mqttConfig.username
            password = mqttConfig.password.toCharArray()
            isCleanSession = true
            connectionTimeout = mqttConfig.connectionTimeout
            keepAliveInterval = mqttConfig.keepAliveInterval
            isAutomaticReconnect = mqttConfig.autoReconnect
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
            Log.i(TAG, "Connecting to MQTT broker: ${mqttConfig.brokerUrl}")
            Log.i(TAG, "Using mobile data - ensure broker is accessible from internet")

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
     * Parse the compact JSON payload from ESP32 into a WaterReading object
     *
     * Expected format: {"t":24.5,"d":350,"s":34.0,"u":12.5,"p":7.8}
     */
    private fun parseMessage(payload: String, tankId: String): WaterReading {
        val json = JSONObject(payload)

        return WaterReading(
            tankId = tankId,
            timestampMs = System.currentTimeMillis(),
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
