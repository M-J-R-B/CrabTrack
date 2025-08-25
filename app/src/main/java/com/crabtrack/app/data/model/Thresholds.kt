package com.crabtrack.app.data.model

/**
 * Represents the acceptable thresholds for water quality parameters in a crab tank.
 * Each parameter has minimum and/or maximum acceptable values.
 *
 * @property pHMin Minimum acceptable pH level
 * @property pHMax Maximum acceptable pH level
 * @property doMin Minimum acceptable dissolved oxygen in mg/L
 * @property salinityMin Minimum acceptable salinity in ppt
 * @property salinityMax Maximum acceptable salinity in ppt
 * @property ammoniaMax Maximum acceptable ammonia level in mg/L
 * @property tempMin Minimum acceptable temperature in degrees Celsius
 * @property tempMax Maximum acceptable temperature in degrees Celsius
 * @property levelMin Minimum acceptable water level in cm
 * @property levelMax Maximum acceptable water level in cm
 */
data class Thresholds(
    val pHMin: Double,
    val pHMax: Double,
    val doMin: Double,
    val salinityMin: Double,
    val salinityMax: Double,
    val ammoniaMax: Double,
    val tempMin: Double,
    val tempMax: Double,
    val levelMin: Double,
    val levelMax: Double
)