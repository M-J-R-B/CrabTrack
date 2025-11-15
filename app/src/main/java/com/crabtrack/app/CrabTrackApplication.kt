package com.crabtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.crabtrack.app.data.repository.TelemetryRepository
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class CrabTrackApplication : Application() {

    // Application-scoped coroutine for long-running operations
    @Singleton
    val applicationScope = CoroutineScope(SupervisorJob())

    // Eagerly inject TelemetryRepository to start Firebase stream immediately
    @Inject
    lateinit var telemetryRepository: TelemetryRepository

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase with enhanced logging
        android.util.Log.i("CrabTrackApp", "Application onCreate - Initializing Firebase")

        // Force TelemetryRepository initialization to start Firebase stream immediately
        // This is critical because Hilt singletons are lazy by default
        android.util.Log.i("CrabTrackApp", "Forcing TelemetryRepository initialization...")
        telemetryRepository.let {
            android.util.Log.i("CrabTrackApp", "TelemetryRepository initialized: ${it::class.simpleName}")
        }

        // Enable Firebase Realtime Database persistence for offline support
        try {
            val firebaseDatabase = FirebaseDatabase.getInstance()
            android.util.Log.d("CrabTrackApp", "Firebase Database instance obtained")

            // Enable persistence only if not already enabled
            firebaseDatabase.setPersistenceEnabled(true)
            android.util.Log.i("CrabTrackApp", "Firebase persistence enabled successfully")

            // Keep synced to maintain connection
            firebaseDatabase.getReference(".info/connected").addValueEventListener(
                object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            android.util.Log.i("CrabTrackApp", "Firebase Database connected")
                        } else {
                            android.util.Log.w("CrabTrackApp", "Firebase Database disconnected")
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("CrabTrackApp", "Firebase connection listener error: ${error.message}")
                    }
                }
            )

        } catch (e: com.google.firebase.database.DatabaseException) {
            // Persistence already enabled
            android.util.Log.i("CrabTrackApp", "Firebase persistence already enabled")
        } catch (e: Exception) {
            android.util.Log.e("CrabTrackApp", "Firebase persistence setup error: ${e.message}", e)
        }

        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Alerts channel
            val alertsChannel = NotificationChannel(
                "alerts",
                "Crab Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for crab health monitoring"
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                "general",
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }
            
            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }
}