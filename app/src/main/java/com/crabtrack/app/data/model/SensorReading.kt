package com.crabtrack.app.data.model

import java.time.LocalDateTime

data class SensorReading(
    val sensorType: SensorType,
    val value: Double,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val alertLevel: AlertLevel = AlertLevel.NORMAL
)