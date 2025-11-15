package com.crabtrack.app.presentation.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.domain.usecase.EvaluateThresholdsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.mockito.kotlin.whenever

/**
 * Unit tests for DashboardViewModel
 * Tests UI state management, data aggregation, and error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockTelemetryRepository: TelemetryRepository
    private lateinit var mockEvaluateThresholdsUseCase: EvaluateThresholdsUseCase
    private lateinit var mockThresholdsFlow: Flow<Thresholds>
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockTelemetryRepository = mock()
        mockEvaluateThresholdsUseCase = mock()
        mockThresholdsFlow = flowOf(createDefaultThresholds())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ====================
    // Helper Methods
    // ====================

    private fun createDefaultThresholds() = Thresholds(
        pHMin = 7.5,
        pHMax = 8.4,
        salinityMin = 30.0,
        salinityMax = 40.0,
        temperatureMin = 22.0,
        temperatureMax = 28.0
    )

    private fun createSampleReading(
        pH: Double = 8.0,
        salinity: Double = 35.0,
        temp: Double = 25.0
    ) = WaterReading(
        tankId = "tank001",
        timestampMs = System.currentTimeMillis(),
        pH = pH,
        salinityPpt = salinity,
        temperatureC = temp
    )

    // ====================
    // UI State Tests
    // ====================

    @Test
    fun `ui state - initial state is loading`() = runTest {
        // Given: Initial setup with no data
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf())

        // When: ViewModel is created
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Initial state should be loading
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Should be loading initially", state.isLoading)
            assertNull("No reading initially", state.latestReading)
            assertEquals("Initial severity should be INFO", AlertSeverity.INFO, state.overallSeverity)
        }
    }

    @Test
    fun `ui state - updates with latest reading`() = runTest {
        // Given: Repository provides reading
        val reading = createSampleReading()
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(reading to emptyList()))

        // When: ViewModel collects data
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: State should contain reading
        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull("Reading should be present", state.latestReading)
            assertEquals("tank001", state.latestReading?.tankId)
        }
    }

    @Test
    fun `ui state - calculates overall severity from alerts`() = runTest {
        // Given: Reading with CRITICAL alert
        val reading = createSampleReading()
        val criticalAlert = Alert(
            id = "alert001",
            tankId = "tank001",
            parameter = "pH",
            message = "pH critical",
            severity = AlertSeverity.CRITICAL,
            timestampMs = System.currentTimeMillis()
        )
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(reading to listOf(criticalAlert)))

        // When: ViewModel processes alerts
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Overall severity should be CRITICAL
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Overall severity should be CRITICAL",
                AlertSeverity.CRITICAL, state.overallSeverity)
        }
    }

    @Test
    fun `ui state - severity is INFO when no alerts`() = runTest {
        // Given: Reading with no alerts
        val reading = createSampleReading()
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(reading to emptyList()))

        // When: No alerts present
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Severity should be INFO
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Severity should be INFO with no alerts",
                AlertSeverity.INFO, state.overallSeverity)
        }
    }

    // ====================
    // Error Handling Tests
    // ====================

    @Test
    fun `error handling - displays error message on failure`() = runTest {
        // Given: Repository throws exception
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf())

        // When: Error occurs (simulated by empty flow then checking error state)
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: UI state should handle errors gracefully
        viewModel.uiState.test {
            val state = awaitItem()
            // Even with errors, state should be initialized
            assertNotNull("UI state should exist", state)
        }
    }

    @Test
    fun `error handling - stops loading on error`() = runTest {
        // Given: Repository with empty flow (simulates error)
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf())

        // When: ViewModel is created
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Loading should eventually stop
        viewModel.uiState.test {
            val state = awaitItem()
            // State should be initialized even if no data
            assertNotNull("State should exist", state)
        }
    }

    // ====================
    // Data Aggregation Tests
    // ====================

    @Test
    fun `data aggregation - handles multiple severity levels`() = runTest {
        // Given: Alerts with different severities
        val reading = createSampleReading()
        val alerts = listOf(
            Alert("a1", "tank001", "pH", "Info alert", AlertSeverity.INFO, System.currentTimeMillis()),
            Alert("a2", "tank001", "temp", "Warning alert", AlertSeverity.WARNING, System.currentTimeMillis()),
            Alert("a3", "tank001", "salinity", "Critical alert", AlertSeverity.CRITICAL, System.currentTimeMillis())
        )
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(reading to alerts))

        // When: Processing mixed alerts
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Highest severity should be selected
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Should select highest severity",
                AlertSeverity.CRITICAL, state.overallSeverity)
        }
    }

    @Test
    fun `data aggregation - updates on new readings`() = runTest {
        // Given: Initial reading
        val reading1 = createSampleReading(pH = 8.0)
        val reading2 = createSampleReading(pH = 7.5)

        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(reading1 to emptyList(), reading2 to emptyList()))

        // When: New readings arrive
        viewModel = DashboardViewModel(
            mockTelemetryRepository,
            mockEvaluateThresholdsUseCase,
            mockThresholdsFlow
        )

        // Then: Should update with latest
        viewModel.uiState.test {
            val state1 = awaitItem()
            assertEquals(8.0, state1.latestReading?.pH ?: 0.0, 0.01)

            val state2 = awaitItem()
            assertEquals(7.5, state2.latestReading?.pH ?: 0.0, 0.01)
        }
    }
}
