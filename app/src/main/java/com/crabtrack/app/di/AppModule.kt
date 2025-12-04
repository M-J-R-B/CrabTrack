package com.crabtrack.app.di

import android.content.Context
import com.crabtrack.app.BuildConfig
import com.crabtrack.app.core.Defaults
import com.crabtrack.app.data.local.MockTelemetryDataSource
import com.crabtrack.app.data.local.ThresholdsStore
import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.repository.MoltRepository
import com.crabtrack.app.data.repository.TelemetryRepository
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.mock.MockTelemetrySource
import com.crabtrack.app.data.source.molt.MockMoltSource
import com.crabtrack.app.data.source.molt.MoltSource
import com.crabtrack.app.data.source.molt.VisionMoltSource
import com.crabtrack.app.data.source.firebase.FirebaseTelemetrySource
import com.crabtrack.app.data.source.mqtt.MqttTelemetrySource
import com.crabtrack.app.data.source.mqtt.MqttConfig
import com.crabtrack.app.data.util.NetworkTypeDetector
import com.crabtrack.app.data.util.DataUsageTracker
import com.crabtrack.app.domain.usecase.ComposeMoltGuidanceUseCase
import com.crabtrack.app.domain.usecase.EvaluateMoltRiskUseCase
import com.crabtrack.app.domain.usecase.EvaluateThresholdsUseCase
import com.crabtrack.app.notification.AlertsNotifier
import com.crabtrack.app.notification.MoltingNotifier
import com.crabtrack.app.CrabTrackApplication
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideMockTelemetryDataSource(): MockTelemetryDataSource {
        return MockTelemetryDataSource()
    }
    
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): PreferencesDataStore {
        return PreferencesDataStore(context)
    }
    
    @Provides
    @Singleton
    fun provideThresholdsStore(
        @ApplicationContext context: Context
    ): ThresholdsStore {
        return ThresholdsStore(context)
    }
    
    @Provides
    @Singleton
    fun provideThresholds(thresholdsStore: ThresholdsStore): Flow<Thresholds> {
        return thresholdsStore.getThresholds()
    }
    
    @Provides
    @Singleton
    fun provideTelemetrySource(
        mockTelemetrySource: MockTelemetrySource,
        firebaseTelemetrySource: FirebaseTelemetrySource,
        mqttTelemetrySource: MqttTelemetrySource
    ): TelemetrySource {
        return when (BuildConfig.TELEMETRY_SOURCE) {
            "MQTT" -> mqttTelemetrySource
            "FIREBASE" -> firebaseTelemetrySource
            "MOCK" -> mockTelemetrySource
            else -> mockTelemetrySource // Default to MOCK for safety
        }
    }
    
    @Provides
    @Singleton
    fun provideFirebaseTelemetrySource(): FirebaseTelemetrySource {
        return FirebaseTelemetrySource()
    }
    
    @Provides
    @Singleton
    fun provideMockTelemetrySource(): MockTelemetrySource {
        return MockTelemetrySource()
    }

    @Provides
    @Singleton
    fun provideMqttTelemetrySource(
        @ApplicationContext context: Context,
        networkTypeDetector: NetworkTypeDetector,
        dataUsageTracker: DataUsageTracker
    ): MqttTelemetrySource {
        return MqttTelemetrySource(context, networkTypeDetector, dataUsageTracker)
    }

    @Provides
    @Singleton
    fun provideTelemetryRepository(
        telemetrySource: TelemetrySource,
        evaluateThresholdsUseCase: EvaluateThresholdsUseCase,
        @ApplicationScope applicationScope: CoroutineScope,
        thresholdsStore: ThresholdsStore
    ): TelemetryRepository {
        return TelemetryRepository(telemetrySource, evaluateThresholdsUseCase, applicationScope, thresholdsStore)
    }
    
    // Molt-related providers
    
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    
    
    @Provides
    @Singleton
    fun provideMoltSource(
        mockMoltSource: MockMoltSource,
        visionMoltSource: VisionMoltSource
    ): MoltSource {
        return when (BuildConfig.MOLT_SOURCE) {
            "VISION" -> visionMoltSource
            "MOCK" -> mockMoltSource
            else -> mockMoltSource // Default to MOCK for safety
        }
    }
    
    
    @Provides
    @Singleton
    fun provideMoltRepository(
        moltSource: MoltSource,
        evaluateMoltRiskUseCase: EvaluateMoltRiskUseCase,
        @ApplicationScope applicationScope: CoroutineScope
    ): MoltRepository {
        return MoltRepository(moltSource, evaluateMoltRiskUseCase, applicationScope)
    }
    
    @Provides
    @Singleton
    fun provideMoltingNotifier(
        @ApplicationContext context: Context,
        moltRepository: MoltRepository,
        composeMoltGuidanceUseCase: ComposeMoltGuidanceUseCase
    ): MoltingNotifier {
        return MoltingNotifier(context, moltRepository, composeMoltGuidanceUseCase)
    }

    @Provides
    @Singleton
    fun provideAlertsNotifier(
        @ApplicationContext context: Context,
        telemetryRepository: TelemetryRepository
    ): AlertsNotifier {
        return AlertsNotifier(context, telemetryRepository)
    }

    // Firebase Threshold Sync providers

    @Provides
    @Singleton
    fun provideFirebaseThresholdRepository(
        firebaseAuth: com.google.firebase.auth.FirebaseAuth,
        firebaseDatabase: com.google.firebase.database.FirebaseDatabase,
        preferencesDataStore: PreferencesDataStore,
        @ApplicationScope scope: CoroutineScope
    ): com.crabtrack.app.data.repository.FirebaseThresholdRepository {
        return com.crabtrack.app.data.repository.FirebaseThresholdRepository(
            firebaseAuth,
            firebaseDatabase,
            preferencesDataStore,
            scope
        )
    }

    @Provides
    @Singleton
    fun provideThresholdMigrationManager(
        preferencesDataStore: PreferencesDataStore,
        firebaseThresholdRepository: com.crabtrack.app.data.repository.FirebaseThresholdRepository,
        @ApplicationContext context: Context
    ): com.crabtrack.app.data.migration.ThresholdMigrationManager {
        return com.crabtrack.app.data.migration.ThresholdMigrationManager(
            preferencesDataStore,
            firebaseThresholdRepository,
            context
        )
    }
}