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
import com.crabtrack.app.ui.molting.MoltingViewModel

/**
 * Adapter for displaying molt events in a RecyclerView timeline
 */
class MoltEventAdapter(
    private val viewModel: MoltingViewModel
) : ListAdapter<MoltEvent, MoltEventAdapter.MoltEventViewHolder>(MoltEventDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoltEventViewHolder {
        val binding = ItemMoltEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoltEventViewHolder(binding, viewModel)
    }
    
    override fun onBindViewHolder(holder: MoltEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    /**
     * ViewHolder for molt event items
     */
    class MoltEventViewHolder(
        private val binding: ItemMoltEventBinding,
        private val viewModel: MoltingViewModel
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(event: MoltEvent) {
            binding.apply {
                // State chip
                eventStateChip.text = viewModel.getMoltStateDisplayName(event.state)
                val stateColor = ContextCompat.getColor(
                    root.context,
                    viewModel.getMoltStateColor(event.state)
                )
                eventStateChip.setChipBackgroundColorResource(viewModel.getMoltStateColor(event.state))
                
                // Confidence
                confidenceText.text = root.context.getString(
                    R.string.confidence_format,
                    (event.confidence * 100).toInt()
                )
                
                // Time
                eventTimeText.text = viewModel.formatEventTime(event.startedAtMs)
                
                // Event details
                eventDetailsText.text = event.notes ?: "Molt state transition"
                
                // Event ID (truncated)
                eventIdText.text = "ID: ${event.id.take(8)}..."
                
                // Timeline indicator color matches state
                root.findViewById<android.view.View>(R.id.timeline_indicator)?.apply {
                    backgroundTintList = ContextCompat.getColorStateList(context, viewModel.getMoltStateColor(event.state))
                }
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