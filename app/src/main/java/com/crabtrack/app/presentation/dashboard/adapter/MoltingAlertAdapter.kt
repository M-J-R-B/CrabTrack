package com.crabtrack.app.presentation.dashboard.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.databinding.ItemMoltingAlertCardBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adapter for displaying molting alerts on the dashboard.
 * Uses a compact single-row layout.
 *
 * @param onAlertClick Callback when an alert card is clicked
 */
class MoltingAlertAdapter(
    private val onAlertClick: (MoltingAlert) -> Unit
) : ListAdapter<MoltingAlert, MoltingAlertAdapter.MoltingAlertViewHolder>(MoltingAlertDiffCallback()) {

    companion object {
        private val ISO_8601_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val ISO_8601_FORMAT_NO_MILLIS = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoltingAlertViewHolder {
        val binding = ItemMoltingAlertCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoltingAlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoltingAlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MoltingAlertViewHolder(
        private val binding: ItemMoltingAlertCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: MoltingAlert) {
            binding.apply {
                // Tank name + detection combined - format "tank1" as "Tank 1 - Molting Detected"
                val tankNumber = alert.tankId.replace("tank", "").trim()
                val detectionText = when {
                    alert.detectionClass.contains("Molting", ignoreCase = true) -> "Molting Detected"
                    else -> alert.detectionClass
                }
                textTankName.text = "Tank $tankNumber - $detectionText"

                // Confidence percentage badge
                val confidencePercent = (alert.confidence * 100).toInt()
                textConfidence.text = "$confidencePercent%"

                // Time ago display
                val timeAgo = parseTimestampToRelative(alert.timestamp)
                textTimeAgo.text = timeAgo

                // Click handler
                root.setOnClickListener {
                    onAlertClick(alert)
                }
            }
        }

        /**
         * Parses the ISO 8601 timestamp and returns a relative time string.
         * e.g., "2 minutes ago", "1 hour ago"
         */
        private fun parseTimestampToRelative(timestamp: String): CharSequence {
            val timeMs = try {
                // Try with milliseconds first
                ISO_8601_FORMAT.parse(timestamp)?.time
                    // Try without milliseconds as fallback
                    ?: ISO_8601_FORMAT_NO_MILLIS.parse(timestamp)?.time
                    ?: 0L
            } catch (e: Exception) {
                0L
            }

            return if (timeMs > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    timeMs,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } else {
                "Unknown time"
            }
        }
    }
}

private class MoltingAlertDiffCallback : DiffUtil.ItemCallback<MoltingAlert>() {
    override fun areItemsTheSame(oldItem: MoltingAlert, newItem: MoltingAlert): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MoltingAlert, newItem: MoltingAlert): Boolean {
        return oldItem == newItem
    }
}
