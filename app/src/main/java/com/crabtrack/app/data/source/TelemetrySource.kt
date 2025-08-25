package com.crabtrack.app.data.source

import com.crabtrack.app.data.model.WaterReading
import kotlinx.coroutines.flow.Flow

interface TelemetrySource {
    fun stream(config: TelemetrySourceConfig = TelemetrySourceConfig()): Flow<WaterReading>
}

data class TelemetrySourceConfig(
    val baseIntervalMs: Long = 1500,
    val jitterMs: Long = 300,
    val spikeFrequency: Double = 0.3,  // Increased from 0.05 to 0.3 (30%) to make alerts more visible
    val tankId: String = "tank_001"
)