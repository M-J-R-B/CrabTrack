package com.crabtrack.app.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.StreamMetrics
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.data.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val errorMessage: String? = null
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
    private val cameraRepository: CameraRepository
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
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            stopAllStreams()
        }
    }
}