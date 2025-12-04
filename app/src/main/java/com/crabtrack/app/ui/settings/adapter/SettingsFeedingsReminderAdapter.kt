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

class SettingsFeedingReminderAdapter(
    private val onDeleteClick: ((FeedingReminder) -> Unit)? = null,
    private val readOnly: Boolean = false
) : ListAdapter<FeedingReminder, SettingsFeedingReminderAdapter.ReminderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feeding_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val textDate: TextView = itemView.findViewById(R.id.text_date)
        private val textRecurrence: TextView = itemView.findViewById(R.id.reminder_recurrence)
        private val deleteButton: MaterialButton =
            itemView.findViewById(R.id.button_delete_reminder)

        fun bind(reminder: FeedingReminder) {
            textDate.text = "${reminder.date} at ${reminder.time}"

            textRecurrence.text = when (reminder.recurrence?.name) {
                "DAILY" -> "Daily"
                "WEEKLY" -> "Weekly"
                else -> "One-time"
            }

            if (readOnly || onDeleteClick == null) {
                deleteButton.visibility = View.GONE
            } else {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener {
                    onDeleteClick?.invoke(reminder)    // ✅ FIXED
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FeedingReminder>() {
        override fun areItemsTheSame(oldItem: FeedingReminder, newItem: FeedingReminder) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FeedingReminder, newItem: FeedingReminder) =
            oldItem == newItem
    }
}
