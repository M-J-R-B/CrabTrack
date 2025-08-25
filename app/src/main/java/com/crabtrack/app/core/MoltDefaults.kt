package com.crabtrack.app.core

/**
 * Default constants and care windows for hermit crab molting management.
 * 
 * These values are based on established hermit crab care guidelines and
 * provide reasonable defaults for molt monitoring and care protocols.
 */
object MoltDefaults {
    
    /**
     * Duration of the high-risk post-molt window in milliseconds.
     * During this period (0-6 hours), crabs are extremely vulnerable.
     */
    const val POSTMOLT_RISK_WINDOW_MS = 6L * 60L * 60L * 1000L // 6 hours
    
    /**
     * Duration of the extended monitoring post-molt window in milliseconds.
     * Safe period but still requires monitoring (6-72 hours post-ecdysis).
     */
    const val POSTMOLT_SAFE_WINDOW_MS = 66L * 60L * 60L * 1000L // 66 hours (3 days - 6 hours)
    
    /**
     * Total post-molt monitoring period in milliseconds.
     * Complete recovery period requiring some level of monitoring (72 hours).
     */
    const val TOTAL_POSTMOLT_WINDOW_MS = 72L * 60L * 60L * 1000L // 72 hours
    
    /**
     * Maximum expected duration for the ecdysis stage in milliseconds.
     * Most crabs complete active molting within 8 hours.
     */
    const val MAX_ECDYSIS_DURATION_MS = 8L * 60L * 60L * 1000L // 8 hours
    
    /**
     * Minimum confidence threshold for automatic molt state detection.
     * Events below this threshold should be reviewed manually.
     */
    const val MIN_DETECTION_CONFIDENCE = 0.7
    
    /**
     * High confidence threshold for molt state detection.
     * Events above this threshold are considered highly reliable.
     */
    const val HIGH_CONFIDENCE_THRESHOLD = 0.9
    
    /**
     * Default check interval for molt monitoring in milliseconds.
     * How frequently to check for molt state changes during active periods.
     */
    const val MOLT_CHECK_INTERVAL_MS = 30L * 60L * 1000L // 30 minutes
    
    /**
     * Critical check interval for high-risk periods in milliseconds.
     * More frequent monitoring during ecdysis and immediate post-molt.
     */
    const val CRITICAL_CHECK_INTERVAL_MS = 15L * 60L * 1000L // 15 minutes
}