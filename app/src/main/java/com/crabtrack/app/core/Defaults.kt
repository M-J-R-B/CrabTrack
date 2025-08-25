package com.crabtrack.app.core

import com.crabtrack.app.data.model.Thresholds

/**
 * Default values and constants for mud crab water quality parameters.
 * These values are based on optimal conditions for mud crab aquaculture.
 */
object Defaults {
    
    /**
     * Default pH range for mud crabs.
     * Optimal range: 7.5 - 8.5
     */
    const val PH_MIN = 7.5
    const val PH_MAX = 8.5
    
    /**
     * Default dissolved oxygen minimum for mud crabs.
     * Minimum acceptable: 5.0 mg/L
     */
    const val DISSOLVED_OXYGEN_MIN = 5.0
    
    /**
     * Default salinity range for mud crabs.
     * Optimal range: 15 - 25 ppt
     */
    const val SALINITY_MIN = 15.0
    const val SALINITY_MAX = 25.0
    
    /**
     * Default maximum ammonia level for mud crabs.
     * Maximum acceptable: 0.1 mg/L
     */
    const val AMMONIA_MAX = 0.1
    
    /**
     * Default temperature range for mud crabs.
     * Optimal range: 26 - 30 °C
     */
    const val TEMPERATURE_MIN = 26.0
    const val TEMPERATURE_MAX = 30.0
    
    /**
     * Default water level range for mud crabs.
     * Optimal range: 25 - 35 cm
     */
    const val WATER_LEVEL_MIN = 25.0
    const val WATER_LEVEL_MAX = 35.0
    
    /**
     * Creates a Thresholds object with sensible defaults for mud crab aquaculture.
     *
     * @return Thresholds object configured with optimal mud crab parameters
     */
    fun createDefaultThresholds(): Thresholds {
        return Thresholds(
            pHMin = PH_MIN,
            pHMax = PH_MAX,
            doMin = DISSOLVED_OXYGEN_MIN,
            salinityMin = SALINITY_MIN,
            salinityMax = SALINITY_MAX,
            ammoniaMax = AMMONIA_MAX,
            tempMin = TEMPERATURE_MIN,
            tempMax = TEMPERATURE_MAX,
            levelMin = WATER_LEVEL_MIN,
            levelMax = WATER_LEVEL_MAX
        )
    }
}