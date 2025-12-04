package com.crabtrack.app.ui.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.crabtrack.app.data.model.CrabDetails
import com.crabtrack.app.data.repository.CrabDetailsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing RTSP stream playback and crab details
 */
@UnstableApi
@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val crabDetailsRepository: CrabDetailsRepository
) : AndroidViewModel(application) {

    // RTSP stream URL
    private val rtspUrl = "rtsp://crabtrack:capstone2@192.168.8.115/stream2"

    // ExoPlayer instance
    private var _player: ExoPlayer? = null
    val player: ExoPlayer?
        get() = _player

    // UI State
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // Crab details state
    private val _crabDetailsMap = MutableStateFlow<Map<String, CrabDetails>>(emptyMap())
    val crabDetailsMap: StateFlow<Map<String, CrabDetails>> = _crabDetailsMap.asStateFlow()

    init {
        observeCrabDetails()
    }

    private fun observeCrabDetails() {
        viewModelScope.launch {
            crabDetailsRepository.observeAllCrabDetails()
                .collect { detailsMap ->
                    _crabDetailsMap.value = detailsMap
                }
        }
    }

    /**
     * Get crab details for a specific tank
     */
    fun getCrabDetails(tankId: String): CrabDetails? {
        return _crabDetailsMap.value[tankId]
    }

    /**
     * Save crab details for a tank
     */
    fun saveCrabDetails(details: CrabDetails) {
        viewModelScope.launch {
            crabDetailsRepository.saveCrabDetails(details)
        }
    }

    /**
     * Reset/clear crab details for a tank
     */
    fun resetCrabDetails(tankId: String) {
        viewModelScope.launch {
            crabDetailsRepository.resetCrabDetails(tankId)
        }
    }

    // Player listener
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            viewModelScope.launch {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPlaying = false,
                            connectionStatus = "Idle"
                        )
                    }
                    Player.STATE_BUFFERING -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            isPlaying = false,
                            connectionStatus = "Buffering..."
                        )
                    }
                    Player.STATE_READY -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPlaying = true,
                            connectionStatus = "Connected",
                            errorMessage = null
                        )
                    }
                    Player.STATE_ENDED -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPlaying = false,
                            connectionStatus = "Stream ended"
                        )
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    connectionStatus = "Error",
                    errorMessage = error.message ?: "Failed to connect to stream"
                )
            }
        }
    }

    /**
     * Initialize ExoPlayer and start streaming
     */
    fun initializePlayer() {
        if (_player == null) {
            _player = ExoPlayer.Builder(getApplication())
                .build()
                .also { exoPlayer ->
                    exoPlayer.addListener(playerListener)

                    // Create RTSP media source
                    val mediaItem = MediaItem.fromUri(rtspUrl)
                    val mediaSource = RtspMediaSource.Factory()
                        .createMediaSource(mediaItem)

                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                connectionStatus = "Connecting..."
            )
        }
    }

    /**
     * Release ExoPlayer resources
     */
    fun releasePlayer() {
        _player?.apply {
            removeListener(playerListener)
            release()
        }
        _player = null

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPlaying = false,
            connectionStatus = "Disconnected"
        )
    }

    /**
     * Retry connection
     */
    fun retry() {
        releasePlayer()
        initializePlayer()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

/**
 * UI State for Camera screen
 */
data class CameraUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val connectionStatus: String = "Disconnected",
    val errorMessage: String? = null
)
