package com.crabtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@HiltAndroidApp
class CrabTrackApplication : Application() {
    
    // Application-scoped coroutine for long-running operations
    @Singleton
    val applicationScope = CoroutineScope(SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
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