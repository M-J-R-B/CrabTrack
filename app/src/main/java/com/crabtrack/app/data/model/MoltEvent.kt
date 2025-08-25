package com.crabtrack.app.data.model

/**
 * Represents a molting event for a hermit crab.
 * 
 * Tracks the complete lifecycle of a molt from start to finish, including
 * confidence levels, associated evidence, and detailed timing information.
 * 
 * @property id Unique identifier for this molt event
 * @property tankId ID of the tank where the molting occurred
 * @property crabId Optional ID of the specific crab molting (null if unknown/multiple crabs)
 * @property state Current stage of the molting process
 * @property confidence Confidence level (0.0-1.0) in the molt state detection
 * @property startedAtMs Timestamp (milliseconds since epoch) when molting began
 * @property endedAtMs Optional timestamp when molting completed (null if ongoing)
 * @property evidenceUris List of URIs to photos, videos, or other evidence of the molt
 * @property notes Optional notes about the molting event or observations
 */
data class MoltEvent(
    val id: String,
    val tankId: String,
    val crabId: String? = null,
    val state: MoltState,
    val confidence: Double,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val evidenceUris: List<String> = emptyList(),
    val notes: String? = null
)