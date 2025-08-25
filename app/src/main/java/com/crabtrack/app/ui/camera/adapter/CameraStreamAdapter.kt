package com.crabtrack.app.ui.camera.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.ConnectionStatus
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.databinding.ItemCameraStreamBinding

/**
 * Adapter for displaying camera streams in a RecyclerView
 */
class CameraStreamAdapter(
    private val onPlayPause: (CameraStream) -> Unit = {},
    private val onQualityChange: (CameraStream, VideoQuality) -> Unit = { _, _ -> },
    private val onSnapshot: (CameraStream) -> Unit = {}
) : ListAdapter<CameraStream, CameraStreamAdapter.StreamViewHolder>(StreamDiffCallback()) {

    private val streamMetrics = mutableMapOf<String, StreamMetrics>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val binding = ItemCameraStreamBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StreamViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    fun updateStreamMetrics(streamId: String, metrics: StreamMetrics) {
        streamMetrics[streamId] = metrics
        // Find and update the specific item
        currentList.indexOfFirst { it.id == streamId }.let { index ->
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }
    }
    
    inner class StreamViewHolder(
        private val binding: ItemCameraStreamBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(stream: CameraStream) {
            binding.apply {
                // Stream name
                streamNameText.text = stream.name
                
                // Connection status
                updateStreamStatus(stream)
                
                // Video setup
                setupVideoView(stream)
                
                // Metrics
                val metrics = streamMetrics[stream.id]
                updateStreamMetrics(stream, metrics)
                
                // Click handlers
                playPauseButton.setOnClickListener { 
                    onPlayPause(stream) 
                }
                
                qualityButton.setOnClickListener { 
                    showQualityDialog(stream)
                }
                
                snapshotButton.setOnClickListener { 
                    onSnapshot(stream) 
                }
            }
        }
        
        private fun updateStreamStatus(stream: CameraStream) {
            binding.apply {
                when (stream.connectionStatus) {
                    ConnectionStatus.DISCONNECTED -> {
                        statusChip.text = root.context.getString(R.string.stream_offline)
                        statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
                        playPauseButton.text = root.context.getString(R.string.stream_play)
                        
                        // Hide overlays
                        statusOverlay.isVisible = false
                        statusText.isVisible = false
                        liveIndicator.isVisible = false
                        controlsOverlay.isVisible = false
                    }
                    
                    ConnectionStatus.CONNECTING -> {
                        statusChip.text = root.context.getString(R.string.stream_connecting)
                        statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        
                        statusOverlay.isVisible = true
                        statusText.isVisible = true
                        statusText.text = root.context.getString(R.string.stream_connecting)
                        liveIndicator.isVisible = false
                        controlsOverlay.isVisible = false
                    }
                    
                    ConnectionStatus.BUFFERING -> {
                        statusChip.text = root.context.getString(R.string.stream_buffering)
                        statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        
                        statusOverlay.isVisible = true
                        statusText.isVisible = true
                        statusText.text = root.context.getString(R.string.stream_buffering)
                        liveIndicator.isVisible = false
                        controlsOverlay.isVisible = false
                    }
                    
                    ConnectionStatus.CONNECTED -> {
                        statusChip.text = root.context.getString(R.string.stream_online)
                        statusChip.setChipBackgroundColorResource(android.R.color.holo_green_light)
                        playPauseButton.text = root.context.getString(R.string.stream_stop)
                        
                        statusOverlay.isVisible = false
                        statusText.isVisible = false
                        liveIndicator.isVisible = true
                        controlsOverlay.isVisible = true
                    }
                    
                    ConnectionStatus.ERROR -> {
                        statusChip.text = root.context.getString(R.string.stream_error)
                        statusChip.setChipBackgroundColorResource(android.R.color.holo_red_light)
                        
                        statusOverlay.isVisible = true
                        statusText.isVisible = true
                        statusText.text = root.context.getString(R.string.stream_error)
                        liveIndicator.isVisible = false
                        controlsOverlay.isVisible = false
                    }
                }
            }
        }
        
        private fun setupVideoView(stream: CameraStream) {
            binding.apply {
                if (stream.isActive && stream.connectionStatus == ConnectionStatus.CONNECTED) {
                    // Setup video playback
                    videoView.isVisible = true
                    placeholderImage.isVisible = false
                    
                    // Set video URI (for real implementation, this would be an RTSP/HTTP stream)
                    videoView.setVideoURI(Uri.parse(stream.streamUrl))
                    videoView.setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = true
                        videoView.start()
                    }
                } else {
                    videoView.isVisible = false
                    placeholderImage.isVisible = true
                }
            }
        }
        
        private fun updateStreamMetrics(stream: CameraStream, metrics: StreamMetrics?) {
            binding.apply {
                if (stream.isActive && metrics != null) {
                    metricsLayout.isVisible = true
                    
                    val qualityText = "${metrics.resolution} • ${metrics.fps}fps"
                    this.qualityText.text = qualityText
                    
                    latencyText.text = "${metrics.latencyMs}ms"
                } else {
                    metricsLayout.isVisible = false
                }
            }
        }
        
        private fun showQualityDialog(stream: CameraStream) {
            // For now, cycle through qualities
            val currentQuality = stream.quality
            val nextQuality = when (currentQuality) {
                VideoQuality.LOW -> VideoQuality.MEDIUM
                VideoQuality.MEDIUM -> VideoQuality.HIGH
                VideoQuality.HIGH -> VideoQuality.ULTRA
                VideoQuality.ULTRA -> VideoQuality.LOW
            }
            onQualityChange(stream, nextQuality)
        }
    }
    
    private class StreamDiffCallback : DiffUtil.ItemCallback<CameraStream>() {
        override fun areItemsTheSame(oldItem: CameraStream, newItem: CameraStream): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: CameraStream, newItem: CameraStream): Boolean {
            return oldItem == newItem
        }
    }
}