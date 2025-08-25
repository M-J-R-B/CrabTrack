package com.crabtrack.app.domain.usecase

import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EvaluateThresholdsUseCaseTest {

    private lateinit var evaluateThresholdsUseCase: EvaluateThresholdsUseCase
    private lateinit var defaultThresholds: Thresholds
    private val testTankId = "tank-123"
    private val testTimestamp = 1672531200000L // Jan 1, 2023

    @Before
    fun setUp() {
        evaluateThresholdsUseCase = EvaluateThresholdsUseCase()
        defaultThresholds = Thresholds(
            pHMin = 7.0,
            pHMax = 8.5,
            doMin = 5.0,
            salinityMin = 30.0,
            salinityMax = 35.0,
            ammoniaMax = 0.5,
            tempMin = 20.0,
            tempMax = 25.0,
            levelMin = 10.0,
            levelMax = 50.0
        )
    }

    @Test
    fun `evaluate returns null when all parameters are within thresholds`() {
        val normalReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(normalReading, defaultThresholds)

        assertNull(result)
    }

    @Test
    fun `evaluate returns CRITICAL alert for ammonia spike`() {
        val ammoniaSpike = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 1.0, // Above 0.5 threshold
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(ammoniaSpike, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.CRITICAL, result!!.severity)
        assertEquals("Ammonia", result.parameter)
        assertEquals(testTankId, result.tankId)
        assertEquals(testTimestamp, result.timestampMs)
    }

    @Test
    fun `evaluate returns CRITICAL alert for dissolved oxygen below minimum`() {
        val lowDoReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 3.0, // Below 5.0 threshold
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(lowDoReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.CRITICAL, result!!.severity)
        assertEquals("Dissolved Oxygen", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for pH below minimum`() {
        val lowPHReading = createWaterReading(
            pH = 6.5, // Below 7.0 threshold
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(lowPHReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("pH", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for pH above maximum`() {
        val highPHReading = createWaterReading(
            pH = 9.0, // Above 8.5 threshold
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(highPHReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("pH", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for salinity below minimum`() {
        val lowSalinityReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 25.0, // Below 30.0 threshold
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(lowSalinityReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Salinity", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for salinity above maximum`() {
        val highSalinityReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 40.0, // Above 35.0 threshold
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(highSalinityReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Salinity", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for temperature below minimum`() {
        val lowTempReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 18.0, // Below 20.0 threshold
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(lowTempReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Temperature", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for temperature above maximum`() {
        val highTempReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 28.0, // Above 25.0 threshold
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(highTempReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Temperature", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for water level below minimum`() {
        val lowLevelReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 8.0 // Below 10.0 threshold
        )

        val result = evaluateThresholdsUseCase.evaluate(lowLevelReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Water Level", result.parameter)
    }

    @Test
    fun `evaluate returns WARNING alert for water level above maximum`() {
        val highLevelReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 55.0 // Above 50.0 threshold
        )

        val result = evaluateThresholdsUseCase.evaluate(highLevelReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.WARNING, result!!.severity)
        assertEquals("Water Level", result.parameter)
    }

    @Test
    fun `evaluate returns highest severity alert when multiple violations exist`() {
        val multipleViolationsReading = createWaterReading(
            pH = 6.0, // WARNING - below minimum
            dissolvedOxygenMgL = 3.0, // CRITICAL - below minimum
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 18.0, // WARNING - below minimum
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(multipleViolationsReading, defaultThresholds)

        assertNotNull(result)
        assertEquals(AlertSeverity.CRITICAL, result!!.severity)
        assertEquals("Dissolved Oxygen", result.parameter)
    }

    @Test
    fun `evaluateAll returns all alerts for multiple violations`() {
        val multipleViolationsReading = createWaterReading(
            pH = 6.0, // WARNING - below minimum
            dissolvedOxygenMgL = 3.0, // CRITICAL - below minimum
            salinityPpt = 25.0, // WARNING - below minimum
            ammoniaMgL = 0.2,
            temperatureC = 18.0, // WARNING - below minimum
            waterLevelCm = 30.0
        )

        val results = evaluateThresholdsUseCase.evaluateAll(multipleViolationsReading, defaultThresholds)

        assertEquals(4, results.size)
        
        val pHAlert = results.find { it.parameter == "pH" }
        val doAlert = results.find { it.parameter == "Dissolved Oxygen" }
        val salinityAlert = results.find { it.parameter == "Salinity" }
        val tempAlert = results.find { it.parameter == "Temperature" }

        assertNotNull(pHAlert)
        assertEquals(AlertSeverity.WARNING, pHAlert!!.severity)

        assertNotNull(doAlert)
        assertEquals(AlertSeverity.CRITICAL, doAlert!!.severity)

        assertNotNull(salinityAlert)
        assertEquals(AlertSeverity.WARNING, salinityAlert!!.severity)

        assertNotNull(tempAlert)
        assertEquals(AlertSeverity.WARNING, tempAlert!!.severity)
    }

    @Test
    fun `evaluateAll returns empty list when no violations exist`() {
        val normalReading = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 0.2,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val results = evaluateThresholdsUseCase.evaluateAll(normalReading, defaultThresholds)

        assertEquals(0, results.size)
    }

    @Test
    fun `alert messages contain proper formatting and values`() {
        val ammoniaSpike = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 1.25,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(ammoniaSpike, defaultThresholds)

        assertNotNull(result)
        assertEquals("Ammonia level (1.25 mg/L) is critically high", result!!.message)
    }

    @Test
    fun `alert contains valid UUID and correct timestamp`() {
        val ammoniaSpike = createWaterReading(
            pH = 7.5,
            dissolvedOxygenMgL = 6.0,
            salinityPpt = 32.0,
            ammoniaMgL = 1.0,
            temperatureC = 22.0,
            waterLevelCm = 30.0
        )

        val result = evaluateThresholdsUseCase.evaluate(ammoniaSpike, defaultThresholds)

        assertNotNull(result)
        assertNotNull(result!!.id)
        assert(result.id.isNotEmpty())
        assertEquals(testTimestamp, result.timestampMs)
        assertEquals(testTankId, result.tankId)
    }

    private fun createWaterReading(
        pH: Double,
        dissolvedOxygenMgL: Double,
        salinityPpt: Double,
        ammoniaMgL: Double,
        temperatureC: Double,
        waterLevelCm: Double
    ) = WaterReading(
        tankId = testTankId,
        timestampMs = testTimestamp,
        pH = pH,
        dissolvedOxygenMgL = dissolvedOxygenMgL,
        salinityPpt = salinityPpt,
        ammoniaMgL = ammoniaMgL,
        temperatureC = temperatureC,
        waterLevelCm = waterLevelCm
    )
}