package com.crabtrack.app.data.source.camera

import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.ConnectionStatus
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock implementation of CameraSource for development and testing
 * Uses demo video URLs and simulated streaming behavior
 */
@Singleton
class MockCameraSource @Inject constructor() : CameraSource {

    private val _mockStreams = MutableStateFlow(
        listOf(
            CameraStream(
                id = "cam_001",
                tankId = "tank_001",
                name = "Tank 001 - Top View",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                isActive = false,
                quality = VideoQuality.ULTRA_LOW,
                connectionStatus = ConnectionStatus.DISCONNECTED
            ),
            CameraStream(
                id = "cam_002",
                tankId = "tank_002",
                name = "Tank 002 - Top View",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                isActive = false,
                quality = VideoQuality.ULTRA_LOW,
                connectionStatus = ConnectionStatus.DISCONNECTED
            ),
            CameraStream(
                id = "cam_003",
                tankId = "tank_003",
                name = "Tank 003 - Top View",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                isActive = false,
                quality = VideoQuality.ULTRA_LOW,
                connectionStatus = ConnectionStatus.DISCONNECTED
            ),
            CameraStream(
                id = "cam_004",
                tankId = "tank_004",
                name = "Tank 004 - Top View",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                isActive = false,
                quality = VideoQuality.ULTRA_LOW,
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        )
    )

    override fun getStreams(): Flow<List<CameraStream>> = _mockStreams

    override fun getStream(streamId: String): Flow<CameraStream?> {
        return _mockStreams.map { streams -> streams.find { it.id == streamId } }
    }

    override fun getStreamsForTank(tankId: String): Flow<List<CameraStream>> {
        return _mockStreams.map { streams -> streams.filter { it.tankId == tankId } }
    }
    
    override suspend fun startStream(streamId: String): Result<Unit> {
        return try {
            val currentStreams = _mockStreams.value
            val streamIndex = currentStreams.indexOfFirst { it.id == streamId }
            if (streamIndex != -1) {
                // Simulate connection process
                val updatedStreams = currentStreams.toMutableList()
                updatedStreams[streamIndex] = updatedStreams[streamIndex].copy(
                    connectionStatus = ConnectionStatus.CONNECTING
                )
                _mockStreams.value = updatedStreams

                delay(1000) // Simulate connection time

                val finalStreams = _mockStreams.value.toMutableList()
                val finalIndex = finalStreams.indexOfFirst { it.id == streamId }
                finalStreams[finalIndex] = finalStreams[finalIndex].copy(
                    isActive = true,
                    connectionStatus = ConnectionStatus.CONNECTED,
                    lastFrameTimestamp = System.currentTimeMillis()
                )
                _mockStreams.value = finalStreams

                Result.success(Unit)
            } else {
                Result.failure(Exception("Stream not found: $streamId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun stopStream(streamId: String): Result<Unit> {
        return try {
            val currentStreams = _mockStreams.value
            val streamIndex = currentStreams.indexOfFirst { it.id == streamId }
            if (streamIndex != -1) {
                val updatedStreams = currentStreams.toMutableList()
                updatedStreams[streamIndex] = updatedStreams[streamIndex].copy(
                    isActive = false,
                    connectionStatus = ConnectionStatus.DISCONNECTED
                )
                _mockStreams.value = updatedStreams
                Result.success(Unit)
            } else {
                Result.failure(Exception("Stream not found: $streamId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun changeQuality(streamId: String, quality: VideoQuality): Result<Unit> {
        return try {
            val currentStreams = _mockStreams.value
            val streamIndex = currentStreams.indexOfFirst { it.id == streamId }
            if (streamIndex != -1) {
                val updatedStreams = currentStreams.toMutableList()
                updatedStreams[streamIndex] = updatedStreams[streamIndex].copy(quality = quality)
                _mockStreams.value = updatedStreams
                Result.success(Unit)
            } else {
                Result.failure(Exception("Stream not found: $streamId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getStreamMetrics(streamId: String): Flow<StreamMetrics> = flow {
        while (true) {
            val stream = _mockStreams.value.find { it.id == streamId }
            if (stream?.isActive == true) {
                val metrics = StreamMetrics(
                    streamId = streamId,
                    fps = when (stream.quality) {
                        VideoQuality.ULTRA_LOW -> Random.nextInt(10, 16) // 10-15 fps for data savings
                        else -> Random.nextInt(25, 31) // 25-30 fps
                    },
                    bitrate = when (stream.quality) {
                        VideoQuality.ULTRA_LOW -> Random.nextInt(200, 300)    // 200-300 kbps
                        VideoQuality.LOW -> Random.nextInt(500, 1000)         // 500-1000 kbps
                        VideoQuality.MEDIUM -> Random.nextInt(1500, 3000)     // 1.5-3 Mbps
                        VideoQuality.HIGH -> Random.nextInt(4000, 8000)       // 4-8 Mbps
                        VideoQuality.ULTRA -> Random.nextInt(15000, 25000)    // 15-25 Mbps
                    },
                    resolution = when (stream.quality) {
                        VideoQuality.ULTRA_LOW -> "320x240"
                        VideoQuality.LOW -> "640x480"
                        VideoQuality.MEDIUM -> "1280x720"
                        VideoQuality.HIGH -> "1920x1080"
                        VideoQuality.ULTRA -> "3840x2160"
                    },
                    latencyMs = Random.nextLong(50, 200), // 50-200ms latency
                    droppedFrames = Random.nextInt(0, 5), // 0-5 dropped frames
                    timestamp = System.currentTimeMillis()
                )
                emit(metrics)
            }
            delay(1000) // Update every second
        }
    }
    
    override suspend fun takeSnapshot(streamId: String): Result<String> {
        return try {
            val stream = _mockStreams.value.find { it.id == streamId }
            if (stream?.isActive == true) {
                delay(500) // Simulate capture time
                val snapshotUri = "mock://snapshot/${streamId}_${System.currentTimeMillis()}.jpg"
                Result.success(snapshotUri)
            } else {
                Result.failure(Exception("Stream not active: $streamId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun testConnection(streamUrl: String): Result<Boolean> {
        return try {
            delay(Random.nextLong(500, 2000)) // Simulate network test
            // Simulate 90% success rate
            val isReachable = Random.nextFloat() < 0.9f
            Result.success(isReachable)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}