package com.crabtrack.app.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataUsageStore: DataStore<Preferences> by preferencesDataStore(name = "data_usage")

/**
 * Data usage statistics
 */
data class DataUsageStats(
    val todayBytes: Long = 0L,
    val monthBytes: Long = 0L,
    val sessionBytes: Long = 0L,
    val lastResetDate: String = "",
    val lastResetMonth: String = "",
    val dailyLimitBytes: Long = 200L * 1024 * 1024, // 200 MB default
    val dataSaverEnabled: Boolean = false
) {
    val todayMB: Double get() = todayBytes / (1024.0 * 1024.0)
    val monthMB: Double get() = monthBytes / (1024.0 * 1024.0)
    val sessionMB: Double get() = sessionBytes / (1024.0 * 1024.0)
    val dailyLimitMB: Double get() = dailyLimitBytes / (1024.0 * 1024.0)

    fun percentageOfDailyLimit(): Int {
        if (dailyLimitBytes == 0L) return 0
        return ((todayBytes.toDouble() / dailyLimitBytes.toDouble()) * 100).toInt()
    }

    fun isOverDailyLimit(): Boolean = todayBytes >= dailyLimitBytes
    fun isNearDailyLimit(): Boolean = percentageOfDailyLimit() >= 80
}

/**
 * Component types for tracking data usage by feature
 */
enum class DataComponent {
    CAMERA_STREAM,
    MQTT_TELEMETRY,
    FIREBASE_SYNC,
    FIREBASE_ANALYTICS,
    OTHER
}

/**
 * Tracks and limits data usage across the app
 */
@Singleton
class DataUsageTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _sessionUsage = MutableStateFlow(0L)
    val sessionUsage = _sessionUsage.asStateFlow()

    private val _currentStats = MutableStateFlow(DataUsageStats())
    val currentStats = _currentStats.asStateFlow()

    companion object {
        private val KEY_TODAY_BYTES = longPreferencesKey("today_bytes")
        private val KEY_MONTH_BYTES = longPreferencesKey("month_bytes")
        private val KEY_LAST_RESET_DATE = longPreferencesKey("last_reset_date")
        private val KEY_LAST_RESET_MONTH = longPreferencesKey("last_reset_month")
        private val KEY_DAILY_LIMIT = longPreferencesKey("daily_limit_bytes")
        private val KEY_DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
    }

    /**
     * Record data usage
     */
    suspend fun recordUsage(bytes: Long, component: DataComponent = DataComponent.OTHER) {
        if (bytes <= 0) return

        // Update session counter
        _sessionUsage.value += bytes

        // Check and reset daily/monthly counters if needed
        val today = getTodayTimestamp()
        val thisMonth = getThisMonthTimestamp()

        context.dataUsageStore.edit { preferences ->
            val lastResetDate = preferences[KEY_LAST_RESET_DATE] ?: 0L
            val lastResetMonth = preferences[KEY_LAST_RESET_MONTH] ?: 0L

            // Reset daily counter if new day
            val todayBytes = if (lastResetDate != today) {
                preferences[KEY_LAST_RESET_DATE] = today
                bytes
            } else {
                (preferences[KEY_TODAY_BYTES] ?: 0L) + bytes
            }

            // Reset monthly counter if new month
            val monthBytes = if (lastResetMonth != thisMonth) {
                preferences[KEY_LAST_RESET_MONTH] = thisMonth
                bytes
            } else {
                (preferences[KEY_MONTH_BYTES] ?: 0L) + bytes
            }

            preferences[KEY_TODAY_BYTES] = todayBytes
            preferences[KEY_MONTH_BYTES] = monthBytes
        }

        // Update current stats
        updateCurrentStats()
    }

    /**
     * Get current usage statistics as Flow
     */
    fun getUsageStats(): Flow<DataUsageStats> {
        return context.dataUsageStore.data.map { preferences ->
            val today = getTodayTimestamp()
            val thisMonth = getThisMonthTimestamp()
            val lastResetDate = preferences[KEY_LAST_RESET_DATE] ?: 0L
            val lastResetMonth = preferences[KEY_LAST_RESET_MONTH] ?: 0L

            // Reset counters if outdated
            val todayBytes = if (lastResetDate == today) {
                preferences[KEY_TODAY_BYTES] ?: 0L
            } else 0L

            val monthBytes = if (lastResetMonth == thisMonth) {
                preferences[KEY_MONTH_BYTES] ?: 0L
            } else 0L

            DataUsageStats(
                todayBytes = todayBytes,
                monthBytes = monthBytes,
                sessionBytes = _sessionUsage.value,
                lastResetDate = LocalDate.ofEpochDay(lastResetDate).toString(),
                lastResetMonth = LocalDate.ofEpochDay(lastResetMonth).toString(),
                dailyLimitBytes = preferences[KEY_DAILY_LIMIT] ?: (200L * 1024 * 1024),
                dataSaverEnabled = preferences[KEY_DATA_SAVER] ?: false
            )
        }
    }

    /**
     * Set daily data limit in bytes
     */
    suspend fun setDailyLimit(megabytes: Long) {
        context.dataUsageStore.edit { preferences ->
            preferences[KEY_DAILY_LIMIT] = megabytes * 1024 * 1024
        }
        updateCurrentStats()
    }

    /**
     * Enable or disable data saver mode
     */
    suspend fun setDataSaverMode(enabled: Boolean) {
        context.dataUsageStore.edit { preferences ->
            preferences[KEY_DATA_SAVER] = enabled
        }
        updateCurrentStats()
    }

    /**
     * Get data saver mode status
     */
    fun isDataSaverEnabled(): Flow<Boolean> {
        return context.dataUsageStore.data.map { preferences ->
            preferences[KEY_DATA_SAVER] ?: false
        }
    }

    /**
     * Reset daily statistics manually
     */
    suspend fun resetDailyStats() {
        context.dataUsageStore.edit { preferences ->
            preferences[KEY_TODAY_BYTES] = 0L
            preferences[KEY_LAST_RESET_DATE] = getTodayTimestamp()
        }
        updateCurrentStats()
    }

    /**
     * Reset session statistics
     */
    fun resetSessionStats() {
        _sessionUsage.value = 0L
    }

    /**
     * Estimate data usage for camera streaming
     */
    fun estimateStreamingUsage(quality: String, durationMinutes: Int): Long {
        val bytesPerSecond = when (quality) {
            "ULTRA_LOW" -> 30L * 1024 // ~30 KB/sec
            "LOW" -> 75L * 1024 // ~75 KB/sec
            "MEDIUM" -> 250L * 1024 // ~250 KB/sec
            "HIGH" -> 1024L * 1024 // ~1 MB/sec
            "ULTRA" -> 3L * 1024 * 1024 // ~3 MB/sec
            else -> 250L * 1024
        }
        return bytesPerSecond * 60 * durationMinutes
    }

    /**
     * Check if should block operation due to data limit
     */
    suspend fun shouldBlockForDataLimit(): Boolean {
        var shouldBlock = false
        context.dataUsageStore.data.map { preferences ->
            val today = getTodayTimestamp()
            val lastResetDate = preferences[KEY_LAST_RESET_DATE] ?: 0L
            val todayBytes = if (lastResetDate == today) {
                preferences[KEY_TODAY_BYTES] ?: 0L
            } else 0L
            val dailyLimit = preferences[KEY_DAILY_LIMIT] ?: (200L * 1024 * 1024)

            shouldBlock = todayBytes >= dailyLimit
        }
        return shouldBlock
    }

    private fun getTodayTimestamp(): Long {
        return LocalDate.now().toEpochDay()
    }

    private fun getThisMonthTimestamp(): Long {
        return LocalDate.now().withDayOfMonth(1).toEpochDay()
    }

    private suspend fun updateCurrentStats() {
        getUsageStats().collect { stats ->
            _currentStats.value = stats
        }
    }
}
