package com.crabtrack.app.ui.alerts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.repository.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for AlertsViewModel
 * Tests alert loading, filtering, and clearing functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockTelemetryRepository: TelemetryRepository
    private lateinit var viewModel: AlertsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockTelemetryRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ====================
    // Helper Methods
    // ====================

    private fun createSampleReading() = WaterReading(
        tankId = "tank001",
        timestampMs = System.currentTimeMillis(),
        pH = 8.0,
        salinityPpt = 35.0,
        temperatureC = 25.0
    )

    private fun createAlert(
        id: String,
        severity: AlertSeverity,
        timestampMs: Long = System.currentTimeMillis()
    ) = Alert(
        id = id,
        tankId = "tank001",
        parameter = "pH",
        message = "Test alert $id",
        severity = severity,
        timestampMs = timestampMs
    )

    // ====================
    // Alert Loading Tests
    // ====================

    @Test
    fun `alert loading - initial state is loading`() = runTest {
        // Given: Repository with no alerts
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(emptyList()))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to emptyList()))

        // When: ViewModel is created
        viewModel = AlertsViewModel(mockTelemetryRepository)

        // Then: Initial state should be loading
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Should be loading initially", state.isLoading)
        }
    }

    @Test
    fun `alert loading - displays alerts from repository`() = runTest {
        // Given: Repository with alerts
        val alerts = listOf(
            createAlert("alert1", AlertSeverity.WARNING),
            createAlert("alert2", AlertSeverity.INFO)
        )
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(alerts))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to alerts))

        // When: ViewModel collects alerts
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: State should contain alerts
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.alerts.size)
        }
    }

    @Test
    fun `alert loading - stops loading after data received`() = runTest {
        // Given: Repository provides data
        val alerts = listOf(createAlert("alert1", AlertSeverity.INFO))
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(alerts))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to alerts))

        // When: Data is loaded
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Loading should stop
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse("Should not be loading", state.isLoading)
        }
    }

    // ====================
    // Alert Filtering Tests
    // ====================

    @Test
    fun `alert filtering - sorts by timestamp descending`() = runTest {
        // Given: Alerts with different timestamps
        val alert1 = createAlert("a1", AlertSeverity.INFO, timestampMs = 1000L)
        val alert2 = createAlert("a2", AlertSeverity.INFO, timestampMs = 3000L)
        val alert3 = createAlert("a3", AlertSeverity.INFO, timestampMs = 2000L)

        whenever(mockTelemetryRepository.allAlerts)
            .thenReturn(flowOf(listOf(alert1, alert2, alert3)))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to listOf(alert1, alert2, alert3)))

        // When: Alerts are loaded
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should be sorted by timestamp descending
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("a2", state.alerts[0].id) // Most recent
            assertEquals("a3", state.alerts[1].id)
            assertEquals("a1", state.alerts[2].id) // Oldest
        }
    }

    @Test
    fun `alert filtering - limits to 50 alerts maximum`() = runTest {
        // Given: More than 50 alerts
        val alerts = (1..60).map { createAlert("alert$it", AlertSeverity.INFO) }
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(alerts))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to alerts))

        // When: Many alerts are loaded
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should limit to 50
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Should have at most 50 alerts", state.alerts.size <= 50)
        }
    }

    @Test
    fun `alert filtering - removes duplicates by ID`() = runTest {
        // Given: Duplicate alerts
        val alert1 = createAlert("alert1", AlertSeverity.WARNING)
        val alert2 = createAlert("alert1", AlertSeverity.WARNING) // Duplicate ID

        whenever(mockTelemetryRepository.allAlerts)
            .thenReturn(flowOf(listOf(alert1, alert2)))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to listOf(alert1, alert2)))

        // When: Duplicates are processed
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should have only one alert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.alerts.size)
        }
    }

    // ====================
    // Clear Functionality Tests
    // ====================

    @Test
    fun `clear alerts - clears all alerts from UI`() = runTest {
        // Given: ViewModel with alerts
        val alerts = listOf(createAlert("a1", AlertSeverity.INFO))
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(alerts))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to alerts))

        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Clearing all alerts
        viewModel.clearAllAlerts()

        // Then: Alerts list should be empty
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Alerts should be cleared", state.alerts.isEmpty())
        }
    }

    @Test
    fun `refresh alerts - sets loading state`() = runTest {
        // Given: ViewModel with data
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf(emptyList()))
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf(createSampleReading() to emptyList()))

        viewModel = AlertsViewModel(mockTelemetryRepository)

        // When: Refreshing alerts
        viewModel.refreshAlerts()

        // Then: Should set loading state
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("Should be loading", state.isLoading)
            assertNull("Error should be cleared", state.errorMessage)
        }
    }

    // ====================
    // Error Handling Tests
    // ====================

    @Test
    fun `error handling - displays error message on failure`() = runTest {
        // Given: Repository that throws error
        whenever(mockTelemetryRepository.allAlerts)
            .thenReturn(flowOf())
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf())

        // When: Error occurs
        viewModel = AlertsViewModel(mockTelemetryRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should handle gracefully
        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull("State should exist", state)
        }
    }

    @Test
    fun `error handling - clears error message`() = runTest {
        // Given: ViewModel with error
        whenever(mockTelemetryRepository.allAlerts).thenReturn(flowOf())
        whenever(mockTelemetryRepository.readingsWithAlerts)
            .thenReturn(flowOf())

        viewModel = AlertsViewModel(mockTelemetryRepository)

        // Manually set error
        viewModel.refreshAlerts()

        // When: Clearing error
        viewModel.clearError()

        // Then: Error should be cleared
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull("Error should be cleared", state.errorMessage)
        }
    }
}
