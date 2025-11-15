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
 * @property keepAliveInterval Keep-alive interval in seconds (increased to 300 for data savings)
 * @property autoReconnect Whether to automatically reconnect on connection loss
 * @property cleanSession Whether to start with a clean session (true saves data)
 * @property maxReconnectDelay Maximum delay between reconnection attempts in seconds
 */
data class MqttConfig(
    val brokerUrl: String = "tcp://mqtt.imbento.online:1883",
    val username: String = "roche",
    val password: String = "roche@54321",
    val clientId: String = "crabtrack_android_${System.currentTimeMillis()}",
    val topic: String = "crabtrack/device/data",
    val qos: Int = 1,
    val connectionTimeout: Int = 30,
    val keepAliveInterval: Int = 300, // Increased from 60 to 300 (5 min) for 80% reduction in keep-alive overhead
    val autoReconnect: Boolean = true,
    val cleanSession: Boolean = true,
    val maxReconnectDelay: Int = 60
) {
    companion object {
        /**
         * Optimized configuration for WiFi connections
         * Lower latency, more frequent keep-alives acceptable
         */
        fun forWiFi(): MqttConfig = MqttConfig(
            keepAliveInterval = 120, // 2 minutes
            qos = 1, // At-least-once delivery
            maxReconnectDelay = 30
        )

        /**
         * Optimized configuration for mobile data
         * Maximum data savings with longer keep-alive intervals
         */
        fun forMobileData(): MqttConfig = MqttConfig(
            keepAliveInterval = 300, // 5 minutes - 80% reduction in keep-alive overhead
            qos = 0, // At-most-once delivery to save data (no acks)
            maxReconnectDelay = 60,
            cleanSession = true
        )

        /**
         * Data saver mode configuration
         * Aggressive data saving with minimal overhead
         */
        fun forDataSaver(): MqttConfig = MqttConfig(
            keepAliveInterval = 600, // 10 minutes - maximum data savings
            qos = 0, // No acknowledgments
            maxReconnectDelay = 120,
            cleanSession = true
        )

        /**
         * Get configuration based on network type
         */
        fun forNetworkType(isWiFi: Boolean, dataSaverEnabled: Boolean): MqttConfig {
            return when {
                dataSaverEnabled -> forDataSaver()
                isWiFi -> forWiFi()
                else -> forMobileData()
            }
        }
    }
}
