package com.crabtrack.app.data.source.mock

import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.math.*

class MockTelemetrySource : TelemetrySource {
    
    private fun Random.nextGaussian(): Double {
        var u1: Double
        var u2: Double
        do {
            u1 = nextDouble()
            u2 = nextDouble()
        } while (u1 <= 0.0)
        
        val z0 = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return z0
    }
    
    override fun stream(config: TelemetrySourceConfig): Flow<WaterReading> = flow {
        while (true) {
            val reading = generateReading(config)
            emit(reading)
            
            val delayMs = config.baseIntervalMs + Random.nextLong(
                -config.jitterMs,
                config.jitterMs + 1
            )
            delay(delayMs.coerceAtLeast(100))
        }
    }
    
    private fun generateReading(config: TelemetrySourceConfig): WaterReading {
        val shouldSpike = Random.nextDouble() < config.spikeFrequency

        return WaterReading(
            tankId = config.tankId,
            timestampMs = System.currentTimeMillis(),
            pH = generatepH(shouldSpike),
            dissolvedOxygenMgL = generateDissolvedOxygen(shouldSpike),
            salinityPpt = generateSalinity(shouldSpike),
            ammoniaMgL = generateAmmonia(shouldSpike),
            temperatureC = generateTemperature(shouldSpike),
            waterLevelCm = generateWaterLevel(shouldSpike),
            tdsPpm = null,  // Mock source doesn't generate TDS
            turbidityNTU = null  // Mock source doesn't generate turbidity
        )
    }
    
    private fun generatepH(shouldSpike: Boolean): Double {
        return if (shouldSpike && Random.nextBoolean()) {
            if (Random.nextBoolean()) 5.0 + Random.nextDouble() * 1.5 else 9.5 + Random.nextDouble() * 2.0
        } else {
            7.8 + Random.nextGaussian() * 0.3
        }.coerceIn(0.0, 14.0)
    }
    
    private fun generateDissolvedOxygen(shouldSpike: Boolean): Double {
        return if (shouldSpike) {
            // 60% chance of low DO (critical), 40% chance of high DO 
            if (Random.nextDouble() < 0.6) {
                // Critical low DO: 1.0-4.5 mg/L (below 5.0 threshold)
                1.0 + Random.nextDouble() * 3.5
            } else {
                // High DO: 12.0-15.0 mg/L 
                12.0 + Random.nextDouble() * 3.0
            }
        } else {
            // Normal values: 6.5 ± 1.5 mg/L (mostly above threshold, some borderline)
            6.5 + Random.nextGaussian() * 1.5
        }.coerceAtLeast(0.0)
    }
    
    private fun generateSalinity(shouldSpike: Boolean): Double {
        return if (shouldSpike && Random.nextBoolean()) {
            if (Random.nextBoolean()) Random.nextDouble() * 25.0 else 40.0 + Random.nextDouble() * 10.0
        } else {
            34.0 + Random.nextGaussian() * 2.0
        }.coerceAtLeast(0.0)
    }
    
    private fun generateAmmonia(shouldSpike: Boolean): Double {
        return if (shouldSpike && Random.nextBoolean()) {
            1.5 + Random.nextDouble() * 3.0
        } else {
            0.1 + Random.nextGaussian() * 0.2
        }.coerceAtLeast(0.0)
    }
    
    private fun generateTemperature(shouldSpike: Boolean): Double {
        return if (shouldSpike && Random.nextBoolean()) {
            if (Random.nextBoolean()) Random.nextDouble() * 10.0 else 30.0 + Random.nextDouble() * 15.0
        } else {
            24.0 + Random.nextGaussian() * 2.0
        }
    }
    
    private fun generateWaterLevel(shouldSpike: Boolean): Double {
        return if (shouldSpike && Random.nextBoolean()) {
            if (Random.nextBoolean()) Random.nextDouble() * 20.0 else 90.0 + Random.nextDouble() * 30.0
        } else {
            65.0 + Random.nextGaussian() * 5.0
        }.coerceAtLeast(0.0)
    }
}