package com.crabtrack.app.data.source.molt

import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing molting state and event data.
 * 
 * This abstraction allows for different implementations such as mock data generators,
 * sensor-based detection systems, or manual input sources.
 */
interface MoltSource {
    
    /**
     * Provides a continuous stream of molt states for a specific tank.
     * 
     * @param tankId The ID of the tank to monitor
     * @return Flow of molt states representing the current molting status
     */
    fun streamStates(tankId: String): Flow<MoltState>
    
    /**
     * Provides a continuous stream of molt events for a specific tank.
     * 
     * Events are emitted when molt state transitions occur, providing
     * detailed information about the molting process including timestamps,
     * confidence levels, and associated evidence.
     * 
     * @param tankId The ID of the tank to monitor
     * @return Flow of molt events representing state transitions and observations
     */
    fun streamEvents(tankId: String): Flow<MoltEvent>
}