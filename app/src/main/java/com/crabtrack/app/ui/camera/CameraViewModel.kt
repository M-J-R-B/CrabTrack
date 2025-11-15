package com.crabtrack.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.data.repository.CameraRepository
import com.crabtrack.app.data.repository.MoltRepository
import com.crabtrack.app.data.util.DataUsageTracker
import com.crabtrack.app.data.util.DataComponent
import com.crabtrack.app.data.util.DataUsageStats
import com.crabtrack.app.data.util.NetworkTypeDetector
import com.crabtrack.app.data.util.NetworkType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val streams: List<CameraStream> = emptyList(),
    val selectedTankId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val networkType: NetworkType = NetworkType.NONE,
    val dataUsageStats: DataUsageStats = DataUsageStats(),
    val recommendedQuality: VideoQuality = VideoQuality.ULTRA_LOW,
    val dataSaverEnabled: Boolean = false,
    val showDataWarning: Boolean = false,
    // Molt monitoring state
    val moltState: MoltState = MoltState.NONE,
    val moltRiskLevel: AlertSeverity = AlertSeverity.INFO,
    val moltCareWindowRemaining: Long? = null,
    val moltEvents: List<MoltEvent> = emptyList(),
    val moltCardExpanded: Boolean = false
)

sealed class CameraUiEvent {
    data class ShowMessage(val message: String) : CameraUiEvent()
    data class StreamStarted(val streamId: String) : CameraUiEvent()
    data class StreamStopped(val streamId: String) : CameraUiEvent()
    data class SnapshotTaken(val streamId: String, val uri: String) : CameraUiEvent()
}

/**
 * ViewModel for the Camera screen
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val moltRepository: MoltRepository,
    private val networkTypeDetector: NetworkTypeDetector,
    private val dataUsageTracker: DataUsageTracker
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    private val _uiEvents = MutableStateFlow<CameraUiEvent?>(null)
    val uiEvents: StateFlow<CameraUiEvent?> = _uiEvents.asStateFlow()
    
    private val _streamMetrics = MutableStateFlow<Map<String, StreamMetrics>>(emptyMap())
    val streamMetrics: StateFlow<Map<String, StreamMetrics>> = _streamMetrics.asStateFlow()
    
    // Available tank IDs
    val availableTanks: StateFlow<List<String>> = MutableStateFlow(emptyList())
    
    init {
        startMonitoring()
        monitorNetworkChanges()
        monitorDataUsage()
        trackStreamDataUsage()
        startMoltMonitoring()
    }
    
    private fun startMonitoring() {
        // Monitor all streams
        cameraRepository.allStreams
            .catch { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load streams: ${exception.message}"
                )
            }
            .onEach { streams ->
                val tankIds = streams.map { it.tankId }.distinct()
                val selectedTank = _uiState.value.selectedTankId ?: tankIds.firstOrNull()
                
                val filteredStreams = if (selectedTank != null) {
                    streams.filter { it.tankId == selectedTank }
                } else {
                    streams
                }
                
                _uiState.value = CameraUiState(
                    streams = filteredStreams,
                    selectedTankId = selectedTank,
                    isLoading = false,
                    errorMessage = null
                )
                
                // Update available tanks
                (availableTanks as MutableStateFlow).value = tankIds
                
                // Start monitoring metrics for active streams
                streams.filter { it.isActive }.forEach { stream ->
                    monitorStreamMetrics(stream.id)
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun monitorStreamMetrics(streamId: String) {
        cameraRepository.getStreamMetrics(streamId)
            .onEach { metrics ->
                val currentMetrics = _streamMetrics.value.toMutableMap()
                currentMetrics[streamId] = metrics
                _streamMetrics.value = currentMetrics
            }
            .launchIn(viewModelScope)
    }
    
    fun selectTank(tankId: String) {
        viewModelScope.launch {
            cameraRepository.allStreams.collect { allStreams ->
                // Since each tank has only one camera, find the single stream for this tank
                val tankStream = allStreams.find { it.tankId == tankId }
                val filteredStreams = if (tankStream != null) listOf(tankStream) else emptyList()

                _uiState.value = _uiState.value.copy(
                    streams = filteredStreams,
                    selectedTankId = tankId
                )

                // Update molt monitoring for the selected tank
                updateMoltMonitoringForTank(tankId)
            }
        }
    }
    
    fun toggleStream(stream: CameraStream) {
        viewModelScope.launch {
            if (stream.isActive) {
                stopStream(stream.id)
            } else {
                startStream(stream.id)
            }
        }
    }
    
    fun startStream(streamId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            cameraRepository.startStream(streamId)
                .onSuccess {
                    _uiEvents.value = CameraUiEvent.StreamStarted(streamId)
                    monitorStreamMetrics(streamId)
                }
                .onFailure { exception ->
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Failed to start stream: ${exception.message}"
                    )
                }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun stopStream(streamId: String) {
        viewModelScope.launch {
            cameraRepository.stopStream(streamId)
                .onSuccess {
                    _uiEvents.value = CameraUiEvent.StreamStopped(streamId)
                    // Remove metrics for stopped stream
                    val currentMetrics = _streamMetrics.value.toMutableMap()
                    currentMetrics.remove(streamId)
                    _streamMetrics.value = currentMetrics
                }
                .onFailure { exception ->
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Failed to stop stream: ${exception.message}"
                    )
                }
        }
    }
    
    fun changeStreamQuality(streamId: String, quality: VideoQuality) {
        viewModelScope.launch {
            cameraRepository.changeQuality(streamId, quality)
                .onSuccess {
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Quality changed to ${quality.name.lowercase()}"
                    )
                }
                .onFailure { exception ->
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Failed to change quality: ${exception.message}"
                    )
                }
        }
    }
    
    fun takeSnapshot(streamId: String) {
        viewModelScope.launch {
            cameraRepository.takeSnapshot(streamId)
                .onSuccess { uri ->
                    _uiEvents.value = CameraUiEvent.SnapshotTaken(streamId, uri)
                }
                .onFailure { exception ->
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Failed to take snapshot: ${exception.message}"
                    )
                }
        }
    }
    
    fun stopAllStreams() {
        viewModelScope.launch {
            cameraRepository.stopAllStreams()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun clearUiEvent() {
        _uiEvents.value = null
    }
    
    fun refreshStreams() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        startMonitoring()
    }

    /**
     * Monitor network type changes and adjust quality recommendations
     */
    private fun monitorNetworkChanges() {
        networkTypeDetector.observeNetworkType()
            .onEach { networkType ->
                val recommendedQuality = getRecommendedQualityForNetwork(networkType)

                _uiState.value = _uiState.value.copy(
                    networkType = networkType,
                    recommendedQuality = recommendedQuality
                )

                // Auto-adjust quality for active streams if on mobile data
                if (networkType == NetworkType.MOBILE_DATA || networkType == NetworkType.ROAMING) {
                    autoAdjustQualityForMobileData(recommendedQuality)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Monitor data usage statistics
     */
    private fun monitorDataUsage() {
        dataUsageTracker.getUsageStats()
            .onEach { stats ->
                _uiState.value = _uiState.value.copy(
                    dataUsageStats = stats,
                    dataSaverEnabled = stats.dataSaverEnabled,
                    showDataWarning = stats.isNearDailyLimit() || stats.isOverDailyLimit()
                )

                // Show warning if approaching or over limit
                if (stats.isOverDailyLimit()) {
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Daily data limit exceeded! Consider using WiFi."
                    )
                } else if (stats.isNearDailyLimit()) {
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Warning: ${stats.percentageOfDailyLimit()}% of daily data limit used"
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Track data usage for active streams
     */
    private fun trackStreamDataUsage() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Check every second

                _streamMetrics.value.forEach { (streamId, metrics) ->
                    // Calculate bytes used in last second based on bitrate
                    val bytesPerSecond = (metrics.bitrate * 1000L) / 8 // Convert kbps to bytes/sec

                    // Record usage
                    dataUsageTracker.recordUsage(bytesPerSecond, DataComponent.CAMERA_STREAM)
                }
            }
        }
    }

    /**
     * Get recommended quality based on network type
     */
    private fun getRecommendedQualityForNetwork(networkType: NetworkType): VideoQuality {
        return when (networkType) {
            NetworkType.WIFI, NetworkType.ETHERNET -> VideoQuality.MEDIUM
            NetworkType.MOBILE_DATA -> VideoQuality.ULTRA_LOW
            NetworkType.ROAMING -> VideoQuality.ULTRA_LOW
            NetworkType.NONE -> VideoQuality.ULTRA_LOW
        }
    }

    /**
     * Auto-adjust quality for active streams when on mobile data
     */
    private fun autoAdjustQualityForMobileData(recommendedQuality: VideoQuality) {
        viewModelScope.launch {
            val activeStreams = _uiState.value.streams.filter { it.isActive }
            activeStreams.forEach { stream ->
                // Only adjust if current quality is higher than recommended
                if (stream.quality.ordinal > recommendedQuality.ordinal) {
                    changeStreamQuality(stream.id, recommendedQuality)
                    _uiEvents.value = CameraUiEvent.ShowMessage(
                        "Quality adjusted to ${recommendedQuality.displayName} for mobile data"
                    )
                }
            }
        }
    }

    /**
     * Get estimated data usage for streaming at given quality
     */
    fun getEstimatedUsage(quality: VideoQuality, durationMinutes: Int): String {
        val bytes = quality.getEstimatedUsageForDuration(durationMinutes)
        val mb = bytes / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }

    /**
     * Check if should warn about starting stream on mobile data
     */
    suspend fun shouldWarnBeforeStreaming(): Boolean {
        val isMobileData = networkTypeDetector.isMeteredConnection()
        val stats = dataUsageTracker.currentStats.value
        return isMobileData && (stats.isNearDailyLimit() || stats.isOverDailyLimit())
    }

    // ===== Molt Monitoring =====

    /**
     * Start monitoring molt data for the selected tank
     */
    private fun startMoltMonitoring() {
        // Monitor molt state for selected tank
        combine(
            moltRepository.currentState,
            moltRepository.riskLevel,
            moltRepository.careWindowRemaining
        ) { state, risk, careWindow ->
            Triple(state, risk, careWindow)
        }
        .onEach { (state, risk, careWindow) ->
            _uiState.value = _uiState.value.copy(
                moltState = state,
                moltRiskLevel = risk,
                moltCareWindowRemaining = careWindow
            )
        }
        .launchIn(viewModelScope)

        // Monitor molt events (collect last 10 events for display)
        val moltEventsList = mutableListOf<MoltEvent>()
        moltRepository.events
            .onEach { event ->
                // Add new events at the beginning
                moltEventsList.add(0, event)
                // Keep only last 10 events
                if (moltEventsList.size > 10) {
                    moltEventsList.removeAt(moltEventsList.size - 1)
                }
                _uiState.value = _uiState.value.copy(
                    moltEvents = moltEventsList.toList()
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Start molt monitoring for a specific tank
     * Called when tank selection changes
     */
    private fun updateMoltMonitoringForTank(tankId: String) {
        moltRepository.startMonitoring(tankId)
    }

    /**
     * Toggle molt monitoring card expansion
     */
    fun toggleMoltCardExpansion() {
        _uiState.value = _uiState.value.copy(
            moltCardExpanded = !_uiState.value.moltCardExpanded
        )
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            stopAllStreams()
            moltRepository.stopMonitoring()
        }
    }
}