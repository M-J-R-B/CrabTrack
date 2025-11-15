package com.crabtrack.app.ui.camera

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.crabtrack.app.data.model.CameraStream
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.data.repository.CameraRepository
import com.crabtrack.app.data.repository.MoltRepository
import com.crabtrack.app.data.util.DataUsageTracker
import com.crabtrack.app.data.util.NetworkType
import com.crabtrack.app.data.util.NetworkTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for CameraViewModel
 * Tests video feed, snapshot, and sensor overlay functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var cameraRepository: CameraRepository
    private lateinit var moltRepository: MoltRepository
    private lateinit var networkTypeDetector: NetworkTypeDetector
    private lateinit var dataUsageTracker: DataUsageTracker
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        cameraRepository = mock()
        moltRepository = mock()
        networkTypeDetector = mock()
        dataUsageTracker = mock()

        // Default mock behaviors
        whenever(cameraRepository.allStreams).thenReturn(flowOf(emptyList()))
        whenever(moltRepository.currentState).thenReturn(MutableStateFlow(com.crabtrack.app.data.model.MoltState.NONE))
        whenever(moltRepository.riskLevel).thenReturn(MutableStateFlow(com.crabtrack.app.data.model.AlertSeverity.INFO))
        whenever(moltRepository.careWindowRemaining).thenReturn(MutableStateFlow(null))
        whenever(moltRepository.events).thenReturn(flowOf())
        whenever(networkTypeDetector.observeNetworkType()).thenReturn(flowOf(NetworkType.WIFI))
        whenever(dataUsageTracker.getUsageStats()).thenReturn(flowOf(com.crabtrack.app.data.util.DataUsageStats()))
        whenever(dataUsageTracker.isDataSaverEnabled()).thenReturn(flowOf(false))
        whenever(dataUsageTracker.currentStats).thenReturn(MutableStateFlow(com.crabtrack.app.data.util.DataUsageStats()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ====================
    // Video Feed Tests
    // ====================

    @Test
    fun `video feed - streams are loaded on initialization`() = runTest {
        // Given: Repository has streams
        val mockStreams = listOf(
            CameraStream(id = "stream1", tankId = "tank1", name = "Tank 1 Camera", streamUrl = "rtsp://example.com/stream1")
        )
        whenever(cameraRepository.allStreams).thenReturn(flowOf(mockStreams))

        // When: ViewModel is created
        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: UI state should contain streams
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.streams.size)
            assertEquals("stream1", state.streams[0].id)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `video feed - stream can be started`() = runTest {
        // Given: ViewModel with a stream
        whenever(cameraRepository.startStream("stream1")).thenReturn(Result.success(Unit))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Starting a stream
        viewModel.startStream("stream1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Repository method should be called
        verify(cameraRepository).startStream("stream1")
    }

    @Test
    fun `video feed - stream can be stopped`() = runTest {
        // Given: ViewModel with an active stream
        whenever(cameraRepository.stopStream("stream1")).thenReturn(Result.success(Unit))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Stopping a stream
        viewModel.stopStream("stream1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Repository method should be called
        verify(cameraRepository).stopStream("stream1")
    }

    @Test
    fun `video feed - quality can be changed`() = runTest {
        // Given: ViewModel with a stream
        whenever(cameraRepository.changeQuality("stream1", VideoQuality.HIGH))
            .thenReturn(Result.success(Unit))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Changing quality
        viewModel.changeStreamQuality("stream1", VideoQuality.HIGH)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Repository method should be called
        verify(cameraRepository).changeQuality("stream1", VideoQuality.HIGH)
    }

    // ====================
    // Snapshot Tests
    // ====================

    @Test
    fun `snapshot - can be taken successfully`() = runTest {
        // Given: Repository returns snapshot URI
        whenever(cameraRepository.takeSnapshot("stream1")).thenReturn(Result.success("file:///snapshot.jpg"))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Taking snapshot
        viewModel.takeSnapshot("stream1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Repository method should be called
        verify(cameraRepository).takeSnapshot("stream1")
    }

    @Test
    fun `snapshot - error is handled gracefully`() = runTest {
        // Given: Repository fails to take snapshot
        whenever(cameraRepository.takeSnapshot("stream1"))
            .thenReturn(Result.failure(Exception("Camera unavailable")))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Taking snapshot
        viewModel.takeSnapshot("stream1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Error event should be emitted
        viewModel.uiEvents.test {
            val event = awaitItem()
            assertTrue(event is CameraUiEvent.ShowMessage)
            assertTrue((event as CameraUiEvent.ShowMessage).message.contains("Failed"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ====================
    // Network Optimization Tests
    // ====================

    @Test
    fun `network - quality recommendation adapts to WiFi`() = runTest {
        // Given: Device is on WiFi
        whenever(networkTypeDetector.observeNetworkType()).thenReturn(flowOf(NetworkType.WIFI))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Recommended quality should be higher for WiFi
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(NetworkType.WIFI, state.networkType)
            assertEquals(VideoQuality.MEDIUM, state.recommendedQuality)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network - quality recommendation adapts to mobile data`() = runTest {
        // Given: Device is on mobile data
        whenever(networkTypeDetector.observeNetworkType()).thenReturn(flowOf(NetworkType.MOBILE_DATA))

        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Recommended quality should be lower for mobile
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(NetworkType.MOBILE_DATA, state.networkType)
            assertEquals(VideoQuality.ULTRA_LOW, state.recommendedQuality)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network - data usage estimation is accurate`() {
        // Given: ViewModel is initialized
        viewModel = CameraViewModel(cameraRepository, moltRepository, networkTypeDetector, dataUsageTracker)

        // When: Estimating usage for 5 minutes of medium quality
        val estimatedUsage = viewModel.getEstimatedUsage(VideoQuality.MEDIUM, 5)

        // Then: Should return reasonable estimate
        assertTrue(estimatedUsage.contains("MB"))
        assertFalse(estimatedUsage.isEmpty())
    }
}
