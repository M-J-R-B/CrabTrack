package com.crabtrack.app.ui.molting.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.MoltEvent
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.databinding.ItemMoltEventBinding

/**
 * Adapter for displaying molt events in a RecyclerView timeline
 */
class MoltEventAdapter : ListAdapter<MoltEvent, MoltEventAdapter.MoltEventViewHolder>(MoltEventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoltEventViewHolder {
        val binding = ItemMoltEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoltEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoltEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for molt event items
     */
    class MoltEventViewHolder(
        private val binding: ItemMoltEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: MoltEvent) {
            binding.apply {
                // State chip
                eventStateChip.text = getMoltStateDisplayName(event.state)
                val stateColor = getMoltStateColor(event.state)
                eventStateChip.setChipBackgroundColorResource(stateColor)

                // Confidence
                confidenceText.text = root.context.getString(
                    R.string.confidence_format,
                    (event.confidence * 100).toInt()
                )

                // Time
                eventTimeText.text = formatEventTime(event.startedAtMs)

                // Event details
                eventDetailsText.text = event.notes ?: "Molt state transition"

                // Event ID (truncated)
                eventIdText.text = "ID: ${event.id.take(8)}..."

                // Timeline indicator color matches state
                root.findViewById<android.view.View>(R.id.timeline_indicator)?.apply {
                    backgroundTintList = ContextCompat.getColorStateList(context, stateColor)
                }
            }
        }

        private fun getMoltStateDisplayName(state: MoltState): String {
            return when (state) {
                MoltState.NONE -> "None"
                MoltState.PREMOLT -> "Pre-molt"
                MoltState.ECDYSIS -> "Ecdysis"
                MoltState.POSTMOLT_RISK -> "Post-molt Risk"
                MoltState.POSTMOLT_SAFE -> "Post-molt Safe"
            }
        }

        private fun getMoltStateColor(state: MoltState): Int {
            return when (state) {
                MoltState.NONE -> android.R.color.holo_green_light
                MoltState.PREMOLT -> android.R.color.holo_orange_light
                MoltState.ECDYSIS -> android.R.color.holo_red_dark
                MoltState.POSTMOLT_RISK -> android.R.color.holo_red_light
                MoltState.POSTMOLT_SAFE -> android.R.color.holo_blue_light
            }
        }

        private fun formatEventTime(timestampMs: Long): String {
            val now = System.currentTimeMillis()
            val diffMs = now - timestampMs

            return when {
                diffMs < 60_000 -> "Just now"
                diffMs < 3600_000 -> "${diffMs / 60_000}m ago"
                diffMs < 86400_000 -> "${diffMs / 3600_000}h ago"
                else -> "${diffMs / 86400_000}d ago"
            }
        }
    }
    
    /**
     * DiffUtil callback for efficient list updates
     */
    private class MoltEventDiffCallback : DiffUtil.ItemCallback<MoltEvent>() {
        override fun areItemsTheSame(oldItem: MoltEvent, newItem: MoltEvent): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: MoltEvent, newItem: MoltEvent): Boolean {
            return oldItem == newItem
        }
    }
}