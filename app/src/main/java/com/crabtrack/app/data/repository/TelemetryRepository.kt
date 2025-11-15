package com.crabtrack.app.data.repository

import com.crabtrack.app.core.Defaults
import com.crabtrack.app.data.local.ThresholdsStore
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import com.crabtrack.app.domain.usecase.EvaluateThresholdsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class TelemetryRepository(
    private val telemetrySource: TelemetrySource,
    private val evaluateThresholdsUseCase: EvaluateThresholdsUseCase,
    private val applicationScope: CoroutineScope,
    private val thresholdsStore: ThresholdsStore
) {

    // Shared readings flow with proper lifecycle management
    // Using Eagerly to start Firebase listener immediately
    // IMPORTANT: Declared before init block so it's initialized first
    val readings = telemetrySource.stream(TelemetrySourceConfig())
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    init {
        android.util.Log.i("TelemetryRepository", "=== TELEMETRY REPOSITORY CREATED ===")
        android.util.Log.i("TelemetryRepository", "Telemetry source: ${telemetrySource::class.simpleName}")

        // Force the readings flow to start immediately by accessing it
        // This is critical because Kotlin property initialization is lazy,
        // so even with SharingStarted.Eagerly, the flow won't start until accessed
        android.util.Log.i("TelemetryRepository", "Forcing readings flow initialization...")
        applicationScope.launch {
            android.util.Log.i("TelemetryRepository", "Accessing readings flow to trigger Firebase stream")
            readings.collect { reading ->
                android.util.Log.d("TelemetryRepository", "Reading received in init collector: pH=${reading.pH}")
            }
        }
        android.util.Log.i("TelemetryRepository", "Readings flow collector launched")
    }
    
    // Live threshold-aware alerts flow
    val alerts: Flow<Alert> = combine(
        readings,
        thresholdsStore.thresholds
    ) { reading, thresholds ->
        evaluateThresholdsUseCase.evaluate(reading, thresholds)
    }.mapNotNull { it }
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    // Live threshold-aware all alerts flow
    val allAlerts: Flow<List<Alert>> = combine(
        readings,
        thresholdsStore.thresholds
    ) { reading, thresholds ->
        evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
    }.transform { alerts ->
        if (alerts.isNotEmpty()) {
            emit(alerts)
        }
    }.shareIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        replay = 1
    )

    // Live threshold-aware readings with alerts flow
    val readingsWithAlerts: Flow<Pair<WaterReading, List<Alert>>> = combine(
        readings,
        thresholdsStore.thresholds
    ) { reading, thresholds ->
        android.util.Log.d("TelemetryRepository", "Combining reading (pH=${reading.pH}) with thresholds")
        val alerts = evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
        reading to alerts
    }.shareIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        replay = 1
    )
    
    // Legacy methods for backward compatibility
    @Deprecated("Use the property-based flows for better performance")
    fun readings(config: TelemetrySourceConfig = TelemetrySourceConfig()): Flow<WaterReading> {
        return telemetrySource.stream(config)
    }
    
    @Deprecated("Use the property-based flows with live thresholds")
    fun alerts(
        config: TelemetrySourceConfig = TelemetrySourceConfig(),
        thresholds: Thresholds = Defaults.createDefaultThresholds()
    ): Flow<Alert> {
        return readings(config).mapNotNull { reading ->
            evaluateThresholdsUseCase.evaluate(reading, thresholds)
        }
    }
    
    @Deprecated("Use the property-based flows with live thresholds")
    fun allAlerts(
        config: TelemetrySourceConfig = TelemetrySourceConfig(),
        thresholds: Thresholds = Defaults.createDefaultThresholds()
    ): Flow<List<Alert>> {
        return readings(config).transform { reading ->
            val alerts = evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
            if (alerts.isNotEmpty()) {
                emit(alerts)
            }
        }
    }
    
    @Deprecated("Use the property-based flows with live thresholds")
    fun readingsWithAlerts(
        config: TelemetrySourceConfig = TelemetrySourceConfig(),
        thresholds: Thresholds = Defaults.createDefaultThresholds()
    ): Flow<Pair<WaterReading, List<Alert>>> {
        return readings(config).transform { reading ->
            val alerts = evaluateThresholdsUseCase.evaluateAll(reading, thresholds)
            emit(reading to alerts)
        }
    }
}