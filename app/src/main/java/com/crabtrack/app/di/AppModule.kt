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
import com.crabtrack.app.data.source.camera.CameraSource
import com.crabtrack.app.data.source.camera.MockCameraSource
import com.crabtrack.app.data.repository.CameraRepository
import com.crabtrack.app.data.source.remote.FirebaseTelemetrySource
import com.crabtrack.app.domain.usecase.ComposeMoltGuidanceUseCase
import com.crabtrack.app.domain.usecase.EvaluateMoltRiskUseCase
import com.crabtrack.app.domain.usecase.EvaluateThresholdsUseCase
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
        firebaseTelemetrySource: FirebaseTelemetrySource
    ): TelemetrySource {
        return when (BuildConfig.TELEMETRY_SOURCE) {
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
    fun provideTelemetryRepository(
        telemetrySource: TelemetrySource,
        evaluateThresholdsUseCase: EvaluateThresholdsUseCase,
        applicationScope: CoroutineScope,
        thresholdsStore: ThresholdsStore
    ): TelemetryRepository {
        return TelemetryRepository(telemetrySource, evaluateThresholdsUseCase, applicationScope, thresholdsStore)
    }
    
    // Molt-related providers
    
    @Provides
    @Singleton
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
        applicationScope: CoroutineScope
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
    
    // Camera-related providers
    
    @Provides
    @Singleton
    fun provideCameraSource(
        mockCameraSource: MockCameraSource
    ): CameraSource {
        return mockCameraSource // Default to mock for development
    }
    
    @Provides
    @Singleton
    fun provideCameraRepository(
        cameraSource: CameraSource,
        applicationScope: CoroutineScope
    ): CameraRepository {
        return CameraRepository(cameraSource, applicationScope)
    }
}