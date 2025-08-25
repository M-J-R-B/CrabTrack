package com.crabtrack.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.data.repository.MoltRepository
import com.crabtrack.app.domain.usecase.ComposeMoltGuidanceUseCase
import com.crabtrack.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifier for high-priority molting events.
 * 
 * Posts notifications when crabs enter ECDYSIS or POSTMOLT_RISK states
 * with guidance from ComposeMoltGuidanceUseCase and deep-links to MoltingFragment.
 */
@Singleton
class MoltingNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moltRepository: MoltRepository,
    private val composeMoltGuidanceUseCase: ComposeMoltGuidanceUseCase
) {
    
    companion object {
        private const val CHANNEL_ID = "molting"
        private const val NOTIFICATION_ID_BASE = 1002
        private const val ACTION_OPEN_MOLTING = "com.crabtrack.app.OPEN_MOLTING"
        private const val MOLTING_NOTIFICATION_ID = 1002
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var moltingCollectionJob: Job? = null
    private var monitoringJob: Job? = null
    private val notifiedEvents = mutableSetOf<String>()
    private var lastNotifiedState: MoltState? = null
    
    fun startCollection(scope: CoroutineScope) {
        stopCollection()
        
        // Start monitoring with default tank
        moltRepository.startMonitoring("tank_001")
        
        moltingCollectionJob = combine(
            moltRepository.currentState,
            moltRepository.riskLevel,
            moltRepository.currentEvent
        ) { state, riskLevel, currentEvent ->
            Triple(state, riskLevel, currentEvent)
        }
        .onEach { (state, riskLevel, currentEvent) ->
            handleMoltStateChange(state, riskLevel, currentEvent)
        }
        .launchIn(scope)
    }
    
    fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            moltRepository.currentState
                .filter { it == MoltState.ECDYSIS || it == MoltState.POSTMOLT_RISK }
                .distinctUntilChanged()
                .collect { state ->
                    if (lastNotifiedState != state) {
                        showMoltingNotification(state, AlertSeverity.CRITICAL, null)
                        lastNotifiedState = state
                    }
                }
        }
    }
    
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    fun stopCollection() {
        moltingCollectionJob?.cancel()
        moltingCollectionJob = null
        monitoringJob?.cancel()
        monitoringJob = null
        moltRepository.stopMonitoring()
    }
    
    private fun handleMoltStateChange(
        state: MoltState,
        riskLevel: AlertSeverity,
        currentEvent: MoltEvent?
    ) {
        // Only notify for critical state transitions
        if (shouldNotify(state, currentEvent)) {
            showMoltingNotification(state, riskLevel, currentEvent)
            lastNotifiedState = state
        }
    }
    
    private fun shouldNotify(state: MoltState, currentEvent: MoltEvent?): Boolean {
        // Only notify for ECDYSIS and POSTMOLT_RISK states
        val isCriticalState = state == MoltState.ECDYSIS || state == MoltState.POSTMOLT_RISK
        
        if (!isCriticalState) return false
        
        // Don't notify if we already notified for this state
        if (lastNotifiedState == state) return false
        
        // Don't notify if we already notified for this specific event
        currentEvent?.let { event ->
            if (notifiedEvents.contains(event.id)) return false
            notifiedEvents.add(event.id)
        }
        
        return true
    }
    
    private fun showMoltingNotification(
        state: MoltState,
        riskLevel: AlertSeverity,
        currentEvent: MoltEvent?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_MOLTING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "molting")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            state.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val (title, text) = createNotificationContent(state, riskLevel, currentEvent)
        
        val iconRes = when (state) {
            MoltState.ECDYSIS -> R.drawable.ic_critical
            MoltState.POSTMOLT_RISK -> R.drawable.ic_warning
            else -> R.drawable.ic_molting
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\nTap to view molt monitoring")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE + state.ordinal
        
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
        }
    }
    
    private fun showSimpleMoltingNotification(state: MoltState) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "molting")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val (title, message) = when (state) {
            MoltState.ECDYSIS -> 
                "🦀 Molting Alert!" to "Your crab is currently molting - do not disturb!"
            MoltState.POSTMOLT_RISK -> 
                "🦀 Post-Molt Risk" to "Monitor closely - crab is vulnerable after molting"
            else -> "🦀 Molting Update" to "Molting status changed"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_molting)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        notificationManager.notify(MOLTING_NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationContent(
        state: MoltState,
        riskLevel: AlertSeverity,
        currentEvent: MoltEvent?
    ): Pair<String, String> {
        val stateName = getStateDisplayName(state)
        val confidence = currentEvent?.let { "${(it.confidence * 100).toInt()}%" } ?: "Unknown"
        
        val title = when (state) {
            MoltState.ECDYSIS -> "🦀 CRITICAL: Active Molting Detected"
            MoltState.POSTMOLT_RISK -> "⚠️ POST-MOLT: Vulnerable Period"
            else -> "🦀 Molting Alert: $stateName"
        }
        
        val guidance = composeMoltGuidanceUseCase(state, riskLevel)
        val shortGuidance = guidance.split(" • ").firstOrNull() ?: "Monitor closely"
        
        val text = "$stateName detected ($confidence confidence). $shortGuidance"
        
        return Pair(title, text)
    }
    
    private fun getStateDisplayName(state: MoltState): String {
        return when (state) {
            MoltState.NONE -> "Normal"
            MoltState.PREMOLT -> "Pre-molt"
            MoltState.ECDYSIS -> "Active Molting"
            MoltState.POSTMOLT_RISK -> "Post-molt Risk"
            MoltState.POSTMOLT_SAFE -> "Post-molt Safe"
        }
    }
    
    fun clearMoltingNotification(state: MoltState) {
        val notificationId = NOTIFICATION_ID_BASE + state.ordinal
        notificationManager.cancel(notificationId)
    }
    
    fun clearAllMoltingNotifications() {
        notifiedEvents.clear()
        lastNotifiedState = null
        
        // Clear notifications by canceling all with our ID range
        MoltState.values().forEach { state ->
            val notificationId = NOTIFICATION_ID_BASE + state.ordinal
            notificationManager.cancel(notificationId)
        }
        // Also clear the simple molting notification
        notificationManager.cancel(MOLTING_NOTIFICATION_ID)
    }
    
    fun getNotifiedEventsCount(): Int = notifiedEvents.size
    
    fun hasNotifiedEvents(): Boolean = notifiedEvents.isNotEmpty()
    
    fun getCurrentNotifiedState(): MoltState? = lastNotifiedState
}