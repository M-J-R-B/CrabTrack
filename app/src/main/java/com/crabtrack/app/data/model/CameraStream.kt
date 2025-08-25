package com.crabtrack.app.data.model

/**
 * Represents a live camera stream for a specific tank
 */
data class CameraStream(
    val id: String,
    val tankId: String,
    val name: String,
    val streamUrl: String,
    val isActive: Boolean = false,
    val quality: VideoQuality = VideoQuality.MEDIUM,
    val lastFrameTimestamp: Long = 0L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

enum class VideoQuality {
    LOW,      // 480p
    MEDIUM,   // 720p
    HIGH,     // 1080p
    ULTRA     // 4K
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BUFFERING,
    ERROR
}

/**
 * Represents camera stream metrics
 */
data class StreamMetrics(
    val streamId: String,
    val fps: Int = 0,
    val bitrate: Int = 0,
    val resolution: String = "",
    val latencyMs: Long = 0L,
    val droppedFrames: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)