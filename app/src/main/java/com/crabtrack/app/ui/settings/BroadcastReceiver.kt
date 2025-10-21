package com.crabtrack.app.ui.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crabtrack.app.R

class FeedingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notification = NotificationCompat.Builder(context, "feeding_channel")
            .setSmallIcon(R.drawable.img)

            .build()

        val notificationManager = NotificationManagerCompat.from(context)

        // ✅ Runtime permission check for Android 13+
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } else {
            // Optionally log or handle the case when permission is not granted
            android.util.Log.w("FeedingAlarmReceiver", "Notification permission not granted.")
        }
    }
}
