package com.crabtrack.app.presentation.dashboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.FeedingLog
import com.crabtrack.app.data.model.FeedingStatus
import com.crabtrack.app.databinding.ItemFeedingSlotBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying individual feeding time slots (e.g., Morning, Evening).
 * Shows the status icon and confirmation time for each feeding slot.
 */
class FeedingSlotAdapter : ListAdapter<FeedingLog, FeedingSlotAdapter.FeedingSlotViewHolder>(
    FeedingSlotDiffCallback()
) {

    private val timeFormat12 = SimpleDateFormat("h:mm a", Locale.US)
    private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.US)
    private val confirmTimeFormat = SimpleDateFormat("h:mm", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedingSlotViewHolder {
        val binding = ItemFeedingSlotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedingSlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedingSlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FeedingSlotViewHolder(
        private val binding: ItemFeedingSlotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: FeedingLog) {
            binding.apply {
                // Label (e.g., "Morning", "Evening")
                textLabel.text = log.label.ifEmpty {
                    // Derive label from time if not set
                    val hour = log.scheduledTime.split(":").firstOrNull()?.toIntOrNull() ?: 12
                    if (hour < 12) "Morning" else "Evening"
                }

                // Scheduled time in 12-hour format
                textTime.text = formatTime(log.scheduledTime)

                // Status icon and colors
                when (log.status) {
                    FeedingStatus.CONFIRMED -> {
                        iconStatus.setImageResource(R.drawable.ic_check_circle)
                        iconStatus.setColorFilter(
                            ContextCompat.getColor(root.context, R.color.normal_text)
                        )
                        // Show confirmation time
                        log.confirmedAt?.let { confirmedAt ->
                            textConfirmedTime.visibility = View.VISIBLE
                            textConfirmedTime.text = "Fed ${confirmTimeFormat.format(Date(confirmedAt))}"
                        } ?: run {
                            textConfirmedTime.visibility = View.GONE
                        }
                    }
                    FeedingStatus.PENDING -> {
                        iconStatus.setImageResource(R.drawable.ic_schedule)
                        iconStatus.setColorFilter(
                            ContextCompat.getColor(root.context, R.color.warning_text)
                        )
                        textConfirmedTime.visibility = View.GONE
                    }
                    FeedingStatus.OVERDUE -> {
                        iconStatus.setImageResource(R.drawable.ic_error_outline)
                        iconStatus.setColorFilter(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                        textConfirmedTime.visibility = View.VISIBLE
                        val missedAgo = calculateMissedTime(log.scheduledTimestamp)
                        textConfirmedTime.text = "Missed $missedAgo"
                        textConfirmedTime.setTextColor(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                    }
                    FeedingStatus.SKIPPED -> {
                        iconStatus.setImageResource(R.drawable.ic_skip)
                        iconStatus.setColorFilter(
                            ContextCompat.getColor(root.context, R.color.text_secondary)
                        )
                        textConfirmedTime.visibility = View.VISIBLE
                        textConfirmedTime.text = "Skipped"
                        textConfirmedTime.setTextColor(
                            ContextCompat.getColor(root.context, R.color.text_secondary)
                        )
                    }
                }
            }
        }

        /**
         * Converts 24-hour time format to 12-hour format with AM/PM.
         */
        private fun formatTime(time24: String): String {
            return try {
                val date = timeFormat24.parse(time24) ?: return time24
                timeFormat12.format(date)
            } catch (e: Exception) {
                time24
            }
        }

        /**
         * Calculates how long ago the scheduled feeding time was.
         */
        private fun calculateMissedTime(scheduledTimestamp: Long): String {
            val now = System.currentTimeMillis()
            val diffMinutes = ((now - scheduledTimestamp) / (1000 * 60)).toInt().coerceAtLeast(0)
            return when {
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffMinutes < 1440 -> {
                    val hours = diffMinutes / 60
                    val mins = diffMinutes % 60
                    if (mins > 0) "${hours}h ${mins}m ago" else "${hours}h ago"
                }
                else -> "yesterday"
            }
        }
    }
}

private class FeedingSlotDiffCallback : DiffUtil.ItemCallback<FeedingLog>() {
    override fun areItemsTheSame(oldItem: FeedingLog, newItem: FeedingLog): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FeedingLog, newItem: FeedingLog): Boolean {
        return oldItem == newItem
    }
}
