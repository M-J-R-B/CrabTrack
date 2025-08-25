package com.crabtrack.app.data.model

/**
 * Represents the different stages of a hermit crab's molting cycle.
 * 
 * The molting process is critical for hermit crabs as they shed their exoskeleton
 * to grow. Each stage requires different care considerations and monitoring.
 */
enum class MoltState {
    /**
     * Normal state - crab is not currently molting and showing no signs of preparation.
     * Safe to handle and feed normally.
     */
    NONE,

    /**
     * Pre-molting stage - crab is preparing to molt.
     * Signs include: digging, decreased activity, cloudy eyes, gel limbs.
     * Should not be disturbed and requires increased humidity.
     */
    PREMOLT,

    /**
     * Active molting stage - crab is in the process of shedding its exoskeleton.
     * Crab is extremely vulnerable and should not be disturbed under any circumstances.
     * This is the most critical and dangerous phase.
     */
    ECDYSIS,

    /**
     * Post-molt safe stage - crab has successfully molted and exoskeleton is hardening.
     * Still vulnerable but past the most critical period. Requires continued monitoring
     * and should be offered calcium-rich foods.
     */
    POSTMOLT_SAFE,

    /**
     * Post-molt risk stage - newly molted crab in critical recovery period.
     * Exoskeleton is still soft and crab is extremely vulnerable to injury and stress.
     * Requires isolation from other crabs and minimal disturbance.
     */
    POSTMOLT_RISK
}