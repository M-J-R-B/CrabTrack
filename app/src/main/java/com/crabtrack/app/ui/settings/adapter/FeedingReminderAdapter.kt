package com.crabtrack.app.ui.settings.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.FeedingReminder
import com.google.android.material.button.MaterialButton

class FeedingReminderAdapter(
    private val onDeleteClick: (FeedingReminder) -> Unit
) : ListAdapter<FeedingReminder, FeedingReminderAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feeding_reminder, parent, false)
        return ReminderViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReminderViewHolder(
        itemView: View,
        private val onDeleteClick: (FeedingReminder) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val dateTimeText: TextView = itemView.findViewById(R.id.reminder_date_time)
        private val recurrenceText: TextView = itemView.findViewById(R.id.reminder_recurrence)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.button_delete_reminder)

        fun bind(reminder: FeedingReminder) {
            // Format: "2025-10-22 at 14:30"
            dateTimeText.text = "${reminder.date} at ${reminder.time}"

            // Show recurrence type
            recurrenceText.text = reminder.getRecurrenceDisplayText()

            // Set delete button click listener
            deleteButton.setOnClickListener {
                onDeleteClick(reminder)
            }
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<FeedingReminder>() {
        override fun areItemsTheSame(oldItem: FeedingReminder, newItem: FeedingReminder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FeedingReminder, newItem: FeedingReminder): Boolean {
            return oldItem == newItem
        }
    }
}
