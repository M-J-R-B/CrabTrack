package com.crabtrack.app.ui.alerts.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.Alert
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.databinding.ItemAlertBinding
import com.crabtrack.app.presentation.utils.FormatUtils

class AlertsAdapter : ListAdapter<Alert, AlertsAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(
        private val binding: ItemAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            binding.apply {
                textAlertParameter.text = alert.parameter
                textAlertMessage.text = alert.message
                textAlertTime.text = FormatUtils.formatTimestamp(alert.timestampMs)
                textAlertTank.text = "Tank: ${alert.tankId}"

                when (alert.severity) {
                    AlertSeverity.INFO -> {
                        cardAlert.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.normal_background)
                        )
                        textSeverityBadge.text = "INFO"
                        textSeverityBadge.setBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.normal_text)
                        )
                    }
                    AlertSeverity.WARNING -> {
                        cardAlert.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.warning_background)
                        )
                        textSeverityBadge.text = "WARNING"
                        textSeverityBadge.setBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.warning_text)
                        )
                    }
                    AlertSeverity.CRITICAL -> {
                        cardAlert.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.critical_background)
                        )
                        textSeverityBadge.text = "CRITICAL"
                        textSeverityBadge.setBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                    }
                }
            }
        }
    }
}

private class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
    override fun areItemsTheSame(oldItem: Alert, newItem: Alert): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Alert, newItem: Alert): Boolean {
        return oldItem == newItem
    }
}