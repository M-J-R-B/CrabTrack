package com.crabtrack.app.data.model

/**
 * Represents the acceptable thresholds for water quality parameters in a crab tank.
 * Each parameter has minimum and/or maximum acceptable values.
 */
data class Thresholds(
    val pHMin: Double = 0.0,
    val pHMax: Double = 0.0,
    val doMin: Double = 0.0,
    val salinityMin: Double = 0.0,
    val salinityMax: Double = 0.0,
    val ammoniaMax: Double = 0.0,
    val tempMin: Double = 0.0,
    val tempMax: Double = 0.0,
    val levelMin: Double = 0.0,
    val levelMax: Double = 0.0,
    val tdsMin: Double = 0.0,
    val tdsMax: Double = 0.0,
    val turbidityMax: Double = 0.0
)
