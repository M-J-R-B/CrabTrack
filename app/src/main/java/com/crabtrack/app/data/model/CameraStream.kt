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
    val quality: VideoQuality = VideoQuality.ULTRA_LOW, // Default to lowest for data savings
    val lastFrameTimestamp: Long = 0L,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

enum class VideoQuality(
    val displayName: String,
    val resolution: String,
    val estimatedBitrateKbps: Int,
    val estimatedMBPerMinute: Double
) {
    ULTRA_LOW("Ultra Low (Mobile)", "320x240", 250, 1.9),      // ~30 KB/sec, ~1.9 MB/min
    LOW("Low", "640x480", 750, 5.6),                            // ~75 KB/sec, ~5.6 MB/min
    MEDIUM("Medium", "1280x720", 2000, 15.0),                   // ~250 KB/sec, ~15 MB/min
    HIGH("High", "1920x1080", 8000, 60.0),                      // ~1 MB/sec, ~60 MB/min
    ULTRA("Ultra (4K)", "3840x2160", 24000, 180.0);             // ~3 MB/sec, ~180 MB/min

    fun getEstimatedUsageForDuration(minutes: Int): Long {
        return (estimatedMBPerMinute * minutes * 1024 * 1024).toLong()
    }
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