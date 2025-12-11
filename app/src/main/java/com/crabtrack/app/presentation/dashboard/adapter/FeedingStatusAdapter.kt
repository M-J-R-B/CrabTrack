package com.crabtrack.app.presentation.dashboard.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.FeedingCardStatus
import com.crabtrack.app.data.model.FeedingLog
import com.crabtrack.app.data.model.FeedingStatus
import com.crabtrack.app.data.model.TankFeedingStatus
import com.crabtrack.app.databinding.ItemFeedingStatusCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying per-tank feeding status cards on the dashboard.
 *
 * @param onConfirmClick Callback when the "Mark Fed" button is clicked
 * @param onCardClick Callback when a card is clicked (for schedule configuration)
 */
class FeedingStatusAdapter(
    private val onConfirmClick: (tankId: String, feedingLogId: String) -> Unit,
    private val onCardClick: (tankId: String) -> Unit
) : ListAdapter<TankFeedingStatus, FeedingStatusAdapter.FeedingStatusViewHolder>(
    FeedingStatusDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedingStatusViewHolder {
        val binding = ItemFeedingStatusCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeedingStatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedingStatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FeedingStatusViewHolder(
        private val binding: ItemFeedingStatusCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val slotAdapter = FeedingSlotAdapter()
        private val timeFormat12 = SimpleDateFormat("h:mm a", Locale.US)
        private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.US)
        private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

        init {
            binding.recyclerViewFeedingSlots.apply {
                adapter = slotAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
        }

        fun bind(status: TankFeedingStatus) {
            binding.apply {
                // Tank name
                textTankName.text = status.tankName

                // Today's date in header for clarity
                textTodayLabel.text = "Today's Feedings · ${dateFormat.format(Date())}"

                // Status badge styling
                when (status.overallStatus) {
                    FeedingCardStatus.ALL_FED -> {
                        textStatusBadge.text = "All Fed"
                        textStatusBadge.setTextColor(
                            ContextCompat.getColor(root.context, R.color.normal_text)
                        )
                        setStatusBadgeBackground(R.color.normal_background)
                        cardFeedingStatus.strokeWidth = 0
                    }
                    FeedingCardStatus.PENDING -> {
                        textStatusBadge.text = "Pending"
                        textStatusBadge.setTextColor(
                            ContextCompat.getColor(root.context, R.color.warning_text)
                        )
                        setStatusBadgeBackground(R.color.warning_background)
                        cardFeedingStatus.strokeWidth = 0
                    }
                    FeedingCardStatus.OVERDUE -> {
                        textStatusBadge.text = "Overdue"
                        textStatusBadge.setTextColor(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                        setStatusBadgeBackground(R.color.critical_background)
                        cardFeedingStatus.strokeColor =
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        cardFeedingStatus.strokeWidth = 2
                    }
                }

                // Today's feeding slots
                if (status.todayLogs.isNotEmpty()) {
                    recyclerViewFeedingSlots.visibility = View.VISIBLE
                    textNoSchedule.visibility = View.GONE
                    slotAdapter.submitList(status.todayLogs)
                } else {
                    recyclerViewFeedingSlots.visibility = View.GONE
                    textNoSchedule.visibility = View.VISIBLE
                }

                // Next scheduled section
                if (status.nextScheduledTime != null && status.nextScheduledTimestamp != null) {
                    val timeUntil = calculateTimeUntil(status.nextScheduledTimestamp)
                    val formattedTime = formatTime(status.nextScheduledTime)
                    textNextScheduled.text = "Next: $formattedTime ($timeUntil)"
                    layoutNextScheduled.visibility = View.VISIBLE

                    // Show confirm button for pending feeding
                    val pendingLog = status.todayLogs.firstOrNull {
                        it.status == FeedingStatus.PENDING
                    }
                    if (pendingLog != null) {
                        btnConfirmFeeding.visibility = View.VISIBLE
                        btnConfirmFeeding.setOnClickListener {
                            onConfirmClick(status.tankId, pendingLog.id)
                        }
                    } else {
                        btnConfirmFeeding.visibility = View.GONE
                    }
                } else if (status.todayLogs.isNotEmpty()) {
                    textNextScheduled.text = "All feedings complete for today"
                    layoutNextScheduled.visibility = View.VISIBLE
                    btnConfirmFeeding.visibility = View.GONE
                } else {
                    layoutNextScheduled.visibility = View.GONE
                    btnConfirmFeeding.visibility = View.GONE
                }

                // Overdue warning banner
                if (status.overallStatus == FeedingCardStatus.OVERDUE && status.overdueByMinutes > 0) {
                    layoutOverdueWarning.visibility = View.VISIBLE
                    val overdueText = if (status.overdueByMinutes >= 60) {
                        val hours = status.overdueByMinutes / 60
                        val mins = status.overdueByMinutes % 60
                        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                    } else {
                        "${status.overdueByMinutes} min"
                    }
                    textOverdueMessage.text = "Feeding overdue by $overdueText!"
                } else {
                    layoutOverdueWarning.visibility = View.GONE
                }

                // Card click for configuration
                root.setOnClickListener {
                    onCardClick(status.tankId)
                }
            }
        }

        private fun setStatusBadgeBackground(colorResId: Int) {
            val drawable = binding.textStatusBadge.background as? GradientDrawable
                ?: GradientDrawable().apply {
                    cornerRadius = 12f * binding.root.resources.displayMetrics.density
                }
            drawable.setColor(ContextCompat.getColor(binding.root.context, colorResId))
            binding.textStatusBadge.background = drawable
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
         * Calculates human-readable time until the given timestamp.
         */
        private fun calculateTimeUntil(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = timestamp - now

            return when {
                diff <= 0 -> "now"
                diff < 60 * 1000 -> "in <1 min"
                diff < 60 * 60 * 1000 -> "in ${diff / (60 * 1000)} min"
                else -> {
                    val hours = diff / (60 * 60 * 1000)
                    val mins = (diff % (60 * 60 * 1000)) / (60 * 1000)
                    if (mins > 0) "in ${hours}h ${mins}m" else "in ${hours}h"
                }
            }
        }
    }
}

private class FeedingStatusDiffCallback : DiffUtil.ItemCallback<TankFeedingStatus>() {
    override fun areItemsTheSame(oldItem: TankFeedingStatus, newItem: TankFeedingStatus): Boolean {
        return oldItem.tankId == newItem.tankId
    }

    override fun areContentsTheSame(oldItem: TankFeedingStatus, newItem: TankFeedingStatus): Boolean {
        return oldItem == newItem
    }
}
