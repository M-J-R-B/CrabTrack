package com.crabtrack.app.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crabtrack.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class FeedingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // AUTH CHECK: Don't show notification if no user logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.w(
                "FeedingAlarmReceiver",
                "Alarm triggered but no user logged in - skipping notification"
            )
            return
        }

        // Get reminder details from intent
        val reminderId = intent.getStringExtra("reminder_id") ?: ""
        val recurrenceType = intent.getStringExtra("recurrence_type") ?: "NONE"
        val originalTimestamp = intent.getLongExtra("timestamp", 0L)

        // 🔹 NEW: read action type from intent (default to FEED)
        val actionType = intent.getStringExtra("action_type") ?: "FEED"

        // 🔹 Decide texts based on FEED / CLEAN
        val (title, shortText, bigText) = when (actionType) {
            "CLEAN" -> Triple(
                "Cleaning Time!",
                "It's time to clean the tank!",
                "It's time to clean the tank! Make sure to remove debris and refresh the water as needed."
            )
            else -> Triple(
                "Feeding Time!",
                "Time to feed your crabs!",
                "Time to feed your crabs! Don't forget to provide fresh food and clean water."
            )
        }

        // Build and show notification
        val notification = NotificationCompat.Builder(context, "feeding_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
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
            android.util.Log.w("FeedingAlarmReceiver", "Notification permission not granted.")
        }

        // Handle recurring reminders
        when (recurrenceType) {
            "DAILY" -> {
                scheduleNextReminder(
                    context = context,
                    reminderId = reminderId,
                    originalTimestamp = originalTimestamp,
                    intervalMillis = TimeUnit.DAYS.toMillis(1),
                    actionType = actionType
                )
            }
            "WEEKLY" -> {
                scheduleNextReminder(
                    context = context,
                    reminderId = reminderId,
                    originalTimestamp = originalTimestamp,
                    intervalMillis = TimeUnit.DAYS.toMillis(7),
                    actionType = actionType
                )
            }
            "NONE" -> {
                // One-time reminder - update status in Firebase
                updateReminderStatus(reminderId, "completed")
            }
        }
    }

    private fun scheduleNextReminder(
        context: Context,
        reminderId: String,
        originalTimestamp: Long,
        intervalMillis: Long,
        actionType: String
    ) {
        val nextTimestamp = originalTimestamp + intervalMillis

        val intent = Intent(context, FeedingAlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra(
                "recurrence_type",
                if (intervalMillis == TimeUnit.DAYS.toMillis(1)) "DAILY" else "WEEKLY"
            )
            putExtra("timestamp", nextTimestamp)
            putExtra("action_type", actionType) // 🔹 keep FEED/CLEAN for next alarm too
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTimestamp,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTimestamp,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FeedingAlarmReceiver", "Error scheduling next reminder: ${e.message}")
        }
    }

    private fun updateReminderStatus(reminderId: String, status: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
            .child("feeding_reminders")
            .child(reminderId)
            .child("status")
            .setValue(status)
    }
}
