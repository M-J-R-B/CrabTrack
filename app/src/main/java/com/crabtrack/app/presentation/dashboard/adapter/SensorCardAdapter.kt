package com.crabtrack.app.presentation.dashboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertLevel
import com.crabtrack.app.data.model.SensorReading
import com.crabtrack.app.databinding.ItemSensorCardBinding
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SensorCardAdapter : ListAdapter<SensorReading, SensorCardAdapter.SensorCardViewHolder>(
    SensorReadingDiffCallback()
) {

    private val decimalFormat = DecimalFormat("#.##")
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorCardViewHolder {
        val binding = ItemSensorCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SensorCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SensorCardViewHolder(
        private val binding: ItemSensorCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(sensorReading: SensorReading) {
            binding.apply {
                // Set sensor name and value
                textSensorName.text = sensorReading.sensorType.displayName
                textSensorValue.text = "${decimalFormat.format(sensorReading.value)} ${sensorReading.sensorType.unit}"
                textLastUpdate.text = "Updated: ${sensorReading.timestamp.format(timeFormatter)}"

                // Set alert status and color
                when (sensorReading.alertLevel) {
                    AlertLevel.NORMAL -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, android.R.color.white)
                        )
                        textAlertStatus.text = "Normal"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, android.R.color.darker_gray)
                        )
                    }
                    AlertLevel.WARNING -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.warning_background)
                        )
                        textAlertStatus.text = "Warning"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, R.color.warning_text)
                        )
                    }
                    AlertLevel.CRITICAL -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.critical_background)
                        )
                        textAlertStatus.text = "Critical"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                    }
                }
            }
        }
    }
}

private class SensorReadingDiffCallback : DiffUtil.ItemCallback<SensorReading>() {
    override fun areItemsTheSame(oldItem: SensorReading, newItem: SensorReading): Boolean {
        return oldItem.sensorType == newItem.sensorType
    }

    override fun areContentsTheSame(oldItem: SensorReading, newItem: SensorReading): Boolean {
        return oldItem == newItem
    }
}