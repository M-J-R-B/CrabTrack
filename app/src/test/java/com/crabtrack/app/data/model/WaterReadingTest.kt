package com.crabtrack.app.data.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for WaterReading data model
 * Tests data validation, timestamp accuracy, and parameter ranges
 */
class WaterReadingTest {

    // ====================
    // Construction Tests
    // ====================

    @Test
    fun `water reading - creates with all required fields`() {
        // Given: Required parameters
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 7.5,
            salinityPpt = 35.0,
            temperatureC = 24.5
        )

        // Then: All fields should be set
        assertEquals("tank001", reading.tankId)
        assertEquals(7.5, reading.pH, 0.01)
        assertEquals(35.0, reading.salinityPpt, 0.01)
        assertEquals(24.5, reading.temperatureC, 0.01)
    }

    @Test
    fun `water reading - creates with all optional fields`() {
        // Given: All parameters including optional
        val reading = WaterReading(
            tankId = "tank002",
            timestampMs = 1672531200000L,
            pH = 8.0,
            dissolvedOxygenMgL = 7.2,
            salinityPpt = 34.5,
            ammoniaMgL = 0.25,
            temperatureC = 25.0,
            waterLevelCm = 50.0,
            tdsPpm = 350.0,
            turbidityNTU = 1.5
        )

        // Then: Optional fields should have values
        assertEquals(7.2, reading.dissolvedOxygenMgL!!, 0.01)
        assertEquals(0.25, reading.ammoniaMgL!!, 0.01)
        assertEquals(50.0, reading.waterLevelCm!!, 0.01)
        assertEquals(350.0, reading.tdsPpm!!, 0.01)
        assertEquals(1.5, reading.turbidityNTU!!, 0.01)
    }

    @Test
    fun `water reading - optional fields default to null`() {
        // Given: Reading with only required fields
        val reading = WaterReading(
            tankId = "tank003",
            timestampMs = System.currentTimeMillis(),
            pH = 7.8,
            salinityPpt = 33.5,
            temperatureC = 23.0
        )

        // Then: Optional fields should be null
        assertNull("Dissolved oxygen should be null", reading.dissolvedOxygenMgL)
        assertNull("Ammonia should be null", reading.ammoniaMgL)
        assertNull("Water level should be null", reading.waterLevelCm)
        assertNull("TDS should be null", reading.tdsPpm)
        assertNull("Turbidity should be null", reading.turbidityNTU)
    }

    // ====================
    // Parameter Range Validation Tests
    // ====================

    @Test
    fun `pH - accepts normal range values`() {
        // Given: pH in normal seawater range (7.5-8.4)
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 8.1,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: pH should be within range
        assertTrue("pH should be within seawater range", reading.pH in 7.5..8.4)
    }

    @Test
    fun `pH - accepts edge case minimum value`() {
        // Given: Minimum pH (0.0)
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 0.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: pH should be 0.0
        assertEquals(0.0, reading.pH, 0.01)
    }

    @Test
    fun `pH - accepts edge case maximum value`() {
        // Given: Maximum pH (14.0)
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 14.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: pH should be 14.0
        assertEquals(14.0, reading.pH, 0.01)
    }

    @Test
    fun `salinity - accepts normal seawater range`() {
        // Given: Salinity in normal range (30-40 ppt)
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: Salinity should be within range
        assertTrue("Salinity should be within normal range", reading.salinityPpt in 30.0..40.0)
    }

    @Test
    fun `temperature - accepts tropical range values`() {
        // Given: Temperature in tropical range (24-28°C)
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 26.0
        )

        // Then: Temperature should be within range
        assertTrue("Temperature should be within tropical range", reading.temperatureC in 24.0..28.0)
    }

    // ====================
    // Timestamp Tests
    // ====================

    @Test
    fun `timestamp - accurately reflects current time`() {
        // Given: Current timestamp
        val beforeMs = System.currentTimeMillis()
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = System.currentTimeMillis(),
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )
        val afterMs = System.currentTimeMillis()

        // Then: Timestamp should be between before and after
        assertTrue("Timestamp should be recent", reading.timestampMs in beforeMs..afterMs)
    }

    @Test
    fun `timestamp - uses millisecond precision`() {
        // Given: Specific millisecond timestamp
        val specificTimestamp = 1672531200123L // Includes milliseconds
        val reading = WaterReading(
            tankId = "tank001",
            timestampMs = specificTimestamp,
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: Exact timestamp should be preserved
        assertEquals(1672531200123L, reading.timestampMs)
    }

    // ====================
    // Data Class Tests
    // ====================

    @Test
    fun `equality - same readings are equal`() {
        // Given: Two identical readings
        val reading1 = WaterReading(
            tankId = "tank001",
            timestampMs = 1672531200000L,
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )
        val reading2 = WaterReading(
            tankId = "tank001",
            timestampMs = 1672531200000L,
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // Then: Should be equal
        assertEquals("Readings should be equal", reading1, reading2)
    }

    @Test
    fun `copy - creates modified reading`() {
        // Given: Original reading
        val original = WaterReading(
            tankId = "tank001",
            timestampMs = 1672531200000L,
            pH = 8.0,
            salinityPpt = 35.0,
            temperatureC = 24.0
        )

        // When: Copying with modifications
        val modified = original.copy(pH = 7.8, temperatureC = 25.0)

        // Then: Modified fields should differ
        assertEquals(7.8, modified.pH, 0.01)
        assertEquals(25.0, modified.temperatureC, 0.01)
        // Unchanged fields should remain same
        assertEquals("tank001", modified.tankId)
        assertEquals(35.0, modified.salinityPpt, 0.01)
    }
}
