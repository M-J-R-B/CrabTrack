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
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.data.model.Tank
import com.crabtrack.app.data.repository.CrabDetailsRepository
import com.crabtrack.app.data.repository.MoltingAlertRepository
import com.crabtrack.app.data.repository.TankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing RTSP stream playback, tank management, and crab details
 */
@UnstableApi
@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val crabDetailsRepository: CrabDetailsRepository,
    private val moltingAlertRepository: MoltingAlertRepository,
    private val tankRepository: TankRepository
) : AndroidViewModel(application) {

    companion object {
        private const val RTSP_CREDENTIALS = "crabtrack:capstone2"
        private const val RTSP_STREAM_PATH = "/stream2"
    }

    // ExoPlayer instance
    private var _player: ExoPlayer? = null
    val player: ExoPlayer?
        get() = _player

    // Current camera IP being streamed
    private var currentStreamingIp: String? = null

    // UI State
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // Crab details state
    private val _crabDetailsMap = MutableStateFlow<Map<String, CrabDetails>>(emptyMap())
    val crabDetailsMap: StateFlow<Map<String, CrabDetails>> = _crabDetailsMap.asStateFlow()

    // Tank list state
    private val _tanks = MutableStateFlow<List<Tank>>(emptyList())
    val tanks: StateFlow<List<Tank>> = _tanks.asStateFlow()

    // Selected tank ID
    private val _selectedTankId = MutableStateFlow("tank1")
    val selectedTankId: StateFlow<String> = _selectedTankId.asStateFlow()

    // Current molting alert for the selected tank
    private val _currentMoltingAlert = MutableStateFlow<MoltingAlert?>(null)
    val currentMoltingAlert: StateFlow<MoltingAlert?> = _currentMoltingAlert.asStateFlow()

    // Events for UI (toast messages, navigation, etc.)
    private val _events = MutableSharedFlow<CameraEvent>()
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    init {
        observeCrabDetails()
        observeTanks()
        ensureDefaultTank()
    }

    private fun observeCrabDetails() {
        viewModelScope.launch {
            crabDetailsRepository.observeAllCrabDetails()
                .collect { detailsMap ->
                    _crabDetailsMap.value = detailsMap
                }
        }
    }

    private fun observeTanks() {
        viewModelScope.launch {
            tankRepository.observeAllTanks()
                .collect { tankList ->
                    _tanks.value = tankList
                    // If current selection is invalid, select first tank
                    if (tankList.isNotEmpty() && tankList.none { it.id == _selectedTankId.value }) {
                        _selectedTankId.value = tankList.first().id
                    }
                }
        }
    }

    private fun ensureDefaultTank() {
        viewModelScope.launch {
            tankRepository.ensureDefaultTankExists()
        }
    }

    /**
     * Build RTSP URL for a given camera IP
     */
    private fun buildRtspUrl(cameraIp: String): String {
        return "rtsp://$RTSP_CREDENTIALS@$cameraIp$RTSP_STREAM_PATH"
    }

    /**
     * Get the RTSP URL for the currently selected tank
     */
    fun getCurrentRtspUrl(): String {
        val tank = getSelectedTank()
        return buildRtspUrl(tank?.cameraIp ?: Tank.DEFAULT_CAMERA_IP)
    }

    /**
     * Get the currently selected tank
     */
    fun getSelectedTank(): Tank? {
        return _tanks.value.find { it.id == _selectedTankId.value }
    }

    /**
     * Select a tank and restart stream if IP changed
     */
    fun selectTank(tankId: String) {
        val previousTank = getSelectedTank()
        _selectedTankId.value = tankId
        val newTank = _tanks.value.find { it.id == tankId }

        // Restart stream if IP is different and stream is active
        if (newTank != null && previousTank != null &&
            newTank.cameraIp != previousTank.cameraIp && _player != null) {
            retry()
        }
    }

    /**
     * Add a new tank
     */
    fun addTank() {
        viewModelScope.launch {
            val result = tankRepository.addTank()
            result.onSuccess { newTank ->
                // Select the newly added tank
                _selectedTankId.value = newTank.id
            }
        }
    }

    /**
     * Delete a tank (cannot delete tank1)
     */
    fun deleteTank(tankId: String) {
        viewModelScope.launch {
            val result = tankRepository.deleteTank(tankId)
            result.onSuccess {
                // If we deleted the selected tank, select tank1
                if (_selectedTankId.value == tankId) {
                    _selectedTankId.value = "tank1"
                }
            }
        }
    }

    /**
     * Update camera IP for a tank
     */
    fun updateTankIp(tankId: String, newIp: String) {
        viewModelScope.launch {
            tankRepository.updateTankIp(tankId, newIp)
            // Restart stream if this is the selected tank and stream is active
            if (tankId == _selectedTankId.value && _player != null) {
                retry()
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

    /**
     * Clears molting status for a tank.
     * Called when molting is complete to allow new alerts.
     */
    fun clearMoltingStatus(tankId: String) {
        viewModelScope.launch {
            moltingAlertRepository.clearMoltingStatus(tankId)
        }
    }

    /**
     * Checks if a tank has active molting status.
     */
    fun hasMoltingStatus(tankId: String): Boolean {
        return moltingAlertRepository.hasMoltingStatus(tankId)
    }

    /**
     * Dismisses a tank's molting status as false alarm.
     */
    fun dismissAsFalseAlarm(tankId: String) {
        viewModelScope.launch {
            moltingAlertRepository.dismissTankAsFalseAlarm(tankId)
        }
    }

    /**
     * Loads the current molting alert for the selected tank.
     * Call this when selecting a tank to have the alert data available for actions.
     */
    fun loadCurrentMoltingAlert(tankId: String) {
        viewModelScope.launch {
            val alert = moltingAlertRepository.getAlertForTank(tankId)
            _currentMoltingAlert.value = alert
        }
    }

    /**
     * Sets the current molting alert directly (e.g., when passed from MoltingFragment).
     */
    fun setCurrentMoltingAlert(alert: MoltingAlert?) {
        _currentMoltingAlert.value = alert
    }

    /**
     * Action: Still Molting
     * - Marks alert as read
     * - Creates feedback with "confirmed_still_molting"
     * - Keeps alert visible
     * - Does NOT reset tankHighestConfidence
     */
    fun onStillMoltingAction() {
        val alert = _currentMoltingAlert.value
        if (alert == null) {
            viewModelScope.launch {
                _events.emit(CameraEvent.ShowError("No alert found"))
            }
            return
        }

        viewModelScope.launch {
            val result = moltingAlertRepository.acknowledgeStillMolting(alert)
            result.onSuccess {
                _events.emit(CameraEvent.ShowToast(CameraEvent.ToastType.STILL_MOLTING))
            }.onFailure { error ->
                _events.emit(CameraEvent.ShowError(error.message ?: "Failed to acknowledge alert"))
            }
        }
    }

    /**
     * Action: Molting Complete
     * - Creates feedback with "confirmed_molting"
     * - Deletes all alerts for tank
     * - Resets tankHighestConfidence
     */
    fun onMoltingCompleteAction() {
        val tankId = _selectedTankId.value
        val alert = _currentMoltingAlert.value

        viewModelScope.launch {
            val result = moltingAlertRepository.clearMoltingStatusWithAlert(tankId, alert)
            result.onSuccess {
                _currentMoltingAlert.value = null
                _events.emit(CameraEvent.ShowToast(CameraEvent.ToastType.MOLTING_COMPLETE))
                _events.emit(CameraEvent.HideMoltingButtons)
            }.onFailure { error ->
                _events.emit(CameraEvent.ShowError(error.message ?: "Failed to complete molting"))
            }
        }
    }

    /**
     * Action: False Alarm
     * - Creates feedback with "false_alarm"
     * - Deletes all alerts for tank
     * - Resets tankHighestConfidence
     */
    fun onFalseAlarmAction() {
        val tankId = _selectedTankId.value
        val alert = _currentMoltingAlert.value

        viewModelScope.launch {
            val result = moltingAlertRepository.dismissTankAsFalseAlarmWithAlert(tankId, alert)
            result.onSuccess {
                _currentMoltingAlert.value = null
                _events.emit(CameraEvent.ShowToast(CameraEvent.ToastType.FALSE_ALARM))
                _events.emit(CameraEvent.HideMoltingButtons)
            }.onFailure { error ->
                _events.emit(CameraEvent.ShowError(error.message ?: "Failed to dismiss as false alarm"))
            }
        }
    }

    /**
     * Action: Dismiss (no feedback)
     * - Does NOT create feedback
     * - Deletes all alerts for tank
     * - Resets tankHighestConfidence
     */
    fun onDismissAction() {
        val tankId = _selectedTankId.value

        viewModelScope.launch {
            val result = moltingAlertRepository.dismissWithoutFeedback(tankId)
            result.onSuccess {
                _currentMoltingAlert.value = null
                _events.emit(CameraEvent.ShowToast(CameraEvent.ToastType.DISMISSED))
                _events.emit(CameraEvent.HideMoltingButtons)
            }.onFailure { error ->
                _events.emit(CameraEvent.ShowError(error.message ?: "Failed to dismiss alert"))
            }
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
            val rtspUrl = getCurrentRtspUrl()
            currentStreamingIp = getSelectedTank()?.cameraIp

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
        currentStreamingIp = null

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

/**
 * One-time events from CameraViewModel to CameraFragment
 */
sealed class CameraEvent {
    data class ShowToast(val type: ToastType) : CameraEvent()
    data class ShowError(val message: String) : CameraEvent()
    data object HideMoltingButtons : CameraEvent()

    enum class ToastType {
        STILL_MOLTING,
        MOLTING_COMPLETE,
        FALSE_ALARM,
        DISMISSED
    }
}
