package com.crabtrack.app.data.source.mqtt

/**
 * Configuration for MQTT broker connection
 *
 * @property brokerUrl MQTT broker URL (tcp://host:port)
 * @property username MQTT username for authentication
 * @property password MQTT password for authentication
 * @property clientId Unique client identifier for this connection
 * @property topic MQTT topic to subscribe to for telemetry data
 * @property qos Quality of Service level (0, 1, or 2)
 * @property connectionTimeout Connection timeout in seconds
 * @property keepAliveInterval Keep-alive interval in seconds
 * @property autoReconnect Whether to automatically reconnect on connection loss
 */
data class MqttConfig(
    val brokerUrl: String = "tcp://mqtt.imbento.online:1883",
    val username: String = "roche",
    val password: String = "roche@54321",
    val clientId: String = "crabtrack_android_${System.currentTimeMillis()}",
    val topic: String = "crabtrack/device/data",
    val qos: Int = 1,
    val connectionTimeout: Int = 30,
    val keepAliveInterval: Int = 60,
    val autoReconnect: Boolean = true
)
