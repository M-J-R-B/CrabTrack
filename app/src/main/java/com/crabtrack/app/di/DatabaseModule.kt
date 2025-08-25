package com.crabtrack.app.di

import android.content.Context
import androidx.room.Room
import com.crabtrack.app.data.local.database.AppDatabase
import com.crabtrack.app.data.local.database.dao.SensorReadingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideSensorReadingDao(database: AppDatabase): SensorReadingDao {
        return database.sensorReadingDao()
    }
}