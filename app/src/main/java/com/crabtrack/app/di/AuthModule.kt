package com.crabtrack.app.di

import android.content.Context
import com.crabtrack.app.data.local.datastore.AuthDataStore
import com.crabtrack.app.data.repository.AuthRepository
import com.crabtrack.app.notification.NotificationCleanupManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Hilt module providing authentication-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /**
     * Provide Firebase Authentication singleton instance
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    /**
     * Provide Firebase Realtime Database singleton instance
     */
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }

    /**
     * Provide AuthDataStore for session persistence
     */
    @Provides
    @Singleton
    fun provideAuthDataStore(
        @ApplicationContext context: Context
    ): AuthDataStore {
        return AuthDataStore(context)
    }

    /**
     * Provide AuthRepository singleton
     * This is the single source of truth for auth state across the app
     */
    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        database: FirebaseDatabase,
        authDataStore: AuthDataStore,
        thresholdMigrationManager: com.crabtrack.app.data.migration.ThresholdMigrationManager,
        notificationCleanupManager: NotificationCleanupManager,
        @ApplicationScope applicationScope: CoroutineScope
    ): AuthRepository {
        return AuthRepository(firebaseAuth, database, authDataStore, thresholdMigrationManager, notificationCleanupManager, applicationScope)
    }
}
