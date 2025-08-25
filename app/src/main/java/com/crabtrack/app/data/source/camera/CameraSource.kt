package com.crabtrack.app.data.source.camera

import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing camera stream data and management
 */
interface CameraSource {
    
    /**
     * Gets all available camera streams
     */
    fun getStreams(): Flow<List<CameraStream>>
    
    /**
     * Gets a specific camera stream by ID
     */
    fun getStream(streamId: String): Flow<CameraStream?>
    
    /**
     * Gets streams for a specific tank
     */
    fun getStreamsForTank(tankId: String): Flow<List<CameraStream>>
    
    /**
     * Starts streaming for a specific camera
     */
    suspend fun startStream(streamId: String): Result<Unit>
    
    /**
     * Stops streaming for a specific camera
     */
    suspend fun stopStream(streamId: String): Result<Unit>
    
    /**
     * Changes the video quality for a stream
     */
    suspend fun changeQuality(streamId: String, quality: VideoQuality): Result<Unit>
    
    /**
     * Gets real-time metrics for a stream
     */
    fun getStreamMetrics(streamId: String): Flow<StreamMetrics>
    
    /**
     * Takes a snapshot from the live stream
     */
    suspend fun takeSnapshot(streamId: String): Result<String> // Returns image URI
    
    /**
     * Checks if a stream URL is reachable
     */
    suspend fun testConnection(streamUrl: String): Result<Boolean>
}