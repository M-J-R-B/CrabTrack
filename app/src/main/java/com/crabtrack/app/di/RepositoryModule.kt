package com.crabtrack.app.di

import com.crabtrack.app.data.local.MockTelemetryDataSource
import com.crabtrack.app.data.local.database.dao.SensorReadingDao
import com.crabtrack.app.data.local.datastore.PreferencesDataStore
import com.crabtrack.app.data.repository.SensorRepository
import com.crabtrack.app.data.repository.ThresholdRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideSensorRepository(
        mockDataSource: MockTelemetryDataSource,
        sensorReadingDao: SensorReadingDao
    ): SensorRepository {
        return SensorRepository(mockDataSource, sensorReadingDao)
    }
    
    @Provides
    @Singleton
    fun provideThresholdRepository(
        preferencesDataStore: PreferencesDataStore,
        firebaseThresholdRepository: com.crabtrack.app.data.repository.FirebaseThresholdRepository
    ): ThresholdRepository {
        return ThresholdRepository(preferencesDataStore, firebaseThresholdRepository)
    }
}