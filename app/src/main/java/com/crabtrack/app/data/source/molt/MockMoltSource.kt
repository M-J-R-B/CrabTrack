package com.crabtrack.app.data.source.molt

import com.crabtrack.app.core.MoltDefaults
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock implementation of MoltSource for testing and development.
 * 
 * Simulates realistic hermit crab molting behavior with configurable timing
 * and deterministic behavior for testing purposes.
 */
@Singleton
class MockMoltSource @Inject constructor() : MoltSource {
    
    private val seed: Long = System.currentTimeMillis()
    private val tickIntervalMs: Long = 15_000L
    
    private val random = Random(seed)
    
    /**
     * State transition probabilities and timing configuration
     */
    private companion object {
        // Probability of transitioning from NONE to PREMOLT per tick
        const val NONE_TO_PREMOLT_PROBABILITY = 0.001
        
        // Probability of transitioning from PREMOLT to ECDYSIS per tick
        const val PREMOLT_TO_ECDYSIS_PROBABILITY = 0.01
        
        // Duration ranges for each state (in ticks)
        const val ECDYSIS_MIN_TICKS = 2 // ~30 seconds minimum
        const val ECDYSIS_MAX_TICKS = 8 // ~2 minutes maximum
        
        const val POSTMOLT_RISK_TICKS = 24 // 6 minutes (6h scaled down)
        const val POSTMOLT_SAFE_TICKS = 120 // 30 minutes (66h scaled down)
        
        // Confidence ranges
        const val MIN_CONFIDENCE = 0.6
        const val MAX_CONFIDENCE = 0.95
    }
    
    override fun streamStates(tankId: String): Flow<MoltState> = flow {
        var currentState = MoltState.NONE
        var stateTicksRemaining = 0
        
        while (true) {
            emit(currentState)
            
            // Add random jitter to tick interval (10-20s range)
            val jitter = random.nextLong(-5000, 5000)
            delay(tickIntervalMs + jitter)
            
            // Handle state transitions
            when (currentState) {
                MoltState.NONE -> {
                    if (random.nextDouble() < NONE_TO_PREMOLT_PROBABILITY) {
                        currentState = MoltState.PREMOLT
                    }
                }
                
                MoltState.PREMOLT -> {
                    if (random.nextDouble() < PREMOLT_TO_ECDYSIS_PROBABILITY) {
                        currentState = MoltState.ECDYSIS
                        stateTicksRemaining = random.nextInt(
                            ECDYSIS_MIN_TICKS, 
                            ECDYSIS_MAX_TICKS + 1
                        )
                    }
                }
                
                MoltState.ECDYSIS -> {
                    stateTicksRemaining--
                    if (stateTicksRemaining <= 0) {
                        currentState = MoltState.POSTMOLT_RISK
                        stateTicksRemaining = POSTMOLT_RISK_TICKS
                    }
                }
                
                MoltState.POSTMOLT_RISK -> {
                    stateTicksRemaining--
                    if (stateTicksRemaining <= 0) {
                        currentState = MoltState.POSTMOLT_SAFE
                        stateTicksRemaining = POSTMOLT_SAFE_TICKS
                    }
                }
                
                MoltState.POSTMOLT_SAFE -> {
                    stateTicksRemaining--
                    if (stateTicksRemaining <= 0) {
                        currentState = MoltState.NONE
                        stateTicksRemaining = 0
                    }
                }
            }
        }
    }
    
    override fun streamEvents(tankId: String): Flow<MoltEvent> = flow {
        var previousState = MoltState.NONE
        
        // First emit some mock events for testing
        val currentTime = System.currentTimeMillis()
        val testEvents = listOf(
            createMoltEvent(
                tankId = tankId,
                state = MoltState.PREMOLT,
                startTime = currentTime - 3600000, // 1 hour ago
                endTime = currentTime - 1800000, // 30 minutes ago
                isTransition = false
            ),
            createMoltEvent(
                tankId = tankId,
                state = MoltState.ECDYSIS,
                startTime = currentTime - 1800000, // 30 minutes ago
                endTime = null,
                isTransition = true
            )
        )
        
        // Emit test events first
        testEvents.forEach { emit(it) }
        
        // Then start monitoring for real state changes
        streamStates(tankId).collect { currentState ->
            if (currentState != previousState && currentState != MoltState.NONE) {
                val eventTime = System.currentTimeMillis()
                emit(createMoltEvent(
                    tankId = tankId,
                    state = currentState,
                    startTime = eventTime,
                    endTime = null,
                    isTransition = true
                ))
            }
            previousState = currentState
        }
    }
    
    private fun createMoltEvent(
        tankId: String,
        state: MoltState,
        startTime: Long,
        endTime: Long?,
        isTransition: Boolean
    ): MoltEvent {
        val confidence = MIN_CONFIDENCE + (MAX_CONFIDENCE - MIN_CONFIDENCE) * random.nextDouble()
        
        // Higher confidence for critical states
        val adjustedConfidence = when (state) {
            MoltState.ECDYSIS, MoltState.POSTMOLT_RISK -> 
                (confidence + 0.1).coerceAtMost(MAX_CONFIDENCE)
            else -> confidence
        }
        
        val notes = when (state) {
            MoltState.PREMOLT -> "Observed digging behavior and decreased activity"
            MoltState.ECDYSIS -> "Active molting detected - crab vulnerable"
            MoltState.POSTMOLT_RISK -> "Critical post-molt period - high vulnerability"
            MoltState.POSTMOLT_SAFE -> "Post-molt monitoring - recovery phase"
            MoltState.NONE -> "Normal activity resumed"
        }
        
        return MoltEvent(
            id = UUID.randomUUID().toString(),
            tankId = tankId,
            crabId = "mock_crab_${tankId}",
            state = state,
            confidence = adjustedConfidence,
            startedAtMs = startTime,
            endedAtMs = endTime,
            evidenceUris = generateMockEvidenceUris(state),
            notes = if (isTransition) "State transition: $notes" else "State completed: $notes"
        )
    }
    
    private fun generateMockEvidenceUris(state: MoltState): List<String> {
        val baseUris = mutableListOf<String>()
        
        when (state) {
            MoltState.PREMOLT -> {
                baseUris.add("mock://evidence/premolt_digging_${random.nextInt(1000)}.jpg")
                if (random.nextBoolean()) {
                    baseUris.add("mock://evidence/cloudy_eyes_${random.nextInt(1000)}.jpg")
                }
            }
            MoltState.ECDYSIS -> {
                baseUris.add("mock://evidence/shed_exoskeleton_${random.nextInt(1000)}.jpg")
                baseUris.add("mock://evidence/molting_process_${random.nextInt(1000)}.mp4")
            }
            MoltState.POSTMOLT_RISK, MoltState.POSTMOLT_SAFE -> {
                baseUris.add("mock://evidence/soft_shell_${random.nextInt(1000)}.jpg")
                if (random.nextBoolean()) {
                    baseUris.add("mock://evidence/recovery_${random.nextInt(1000)}.mp4")
                }
            }
            MoltState.NONE -> {
                if (random.nextDouble() < 0.3) { // 30% chance of evidence for normal state
                    baseUris.add("mock://evidence/normal_activity_${random.nextInt(1000)}.jpg")
                }
            }
        }
        
        return baseUris
    }
}