package com.crabtrack.app.data.repository

import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.data.source.camera.CameraSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing camera streams and related operations
 */
@Singleton
class CameraRepository @Inject constructor(
    private val cameraSource: CameraSource,
    private val applicationScope: CoroutineScope
) {
    
    // Shared flow of all available streams
    val allStreams: Flow<List<CameraStream>> = cameraSource.getStreams()
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.Lazily,
            replay = 1
        )
    
    /**
     * Gets a specific camera stream by ID
     */
    fun getStream(streamId: String): Flow<CameraStream?> {
        return cameraSource.getStream(streamId)
    }
    
    /**
     * Gets all streams for a specific tank
     */
    fun getStreamsForTank(tankId: String): Flow<List<CameraStream>> {
        return cameraSource.getStreamsForTank(tankId)
    }
    
    /**
     * Starts a camera stream
     */
    suspend fun startStream(streamId: String): Result<Unit> {
        return cameraSource.startStream(streamId)
    }
    
    /**
     * Stops a camera stream
     */
    suspend fun stopStream(streamId: String): Result<Unit> {
        return cameraSource.stopStream(streamId)
    }
    
    /**
     * Changes video quality for a stream
     */
    suspend fun changeQuality(streamId: String, quality: VideoQuality): Result<Unit> {
        return cameraSource.changeQuality(streamId, quality)
    }
    
    /**
     * Gets real-time metrics for a stream
     */
    fun getStreamMetrics(streamId: String): Flow<StreamMetrics> {
        return cameraSource.getStreamMetrics(streamId)
    }
    
    /**
     * Takes a snapshot from the live stream
     */
    suspend fun takeSnapshot(streamId: String): Result<String> {
        return cameraSource.takeSnapshot(streamId)
    }
    
    /**
     * Tests connection to a stream URL
     */
    suspend fun testConnection(streamUrl: String): Result<Boolean> {
        return cameraSource.testConnection(streamUrl)
    }
    
    /**
     * Stops all active streams
     */
    suspend fun stopAllStreams(): Result<Unit> {
        return try {
            allStreams.shareIn(applicationScope, SharingStarted.Eagerly, 1)
                .collect { streams ->
                    streams.filter { it.isActive }.forEach { stream ->
                        stopStream(stream.id)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}