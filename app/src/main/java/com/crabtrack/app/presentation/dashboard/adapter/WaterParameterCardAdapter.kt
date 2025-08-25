package com.crabtrack.app.presentation.dashboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.databinding.ItemSensorCardBinding
import com.crabtrack.app.presentation.utils.FormatUtils

data class WaterParameterCard(
    val parameter: String,
    val value: String,
    val severity: AlertSeverity,
    val timestampMs: Long
)

class WaterParameterCardAdapter(
    private val onParameterClick: (String) -> Unit = {}
) : ListAdapter<WaterParameterCard, WaterParameterCardAdapter.ParameterCardViewHolder>(
    ParameterCardDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParameterCardViewHolder {
        val binding = ItemSensorCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParameterCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParameterCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParameterCardViewHolder(
        private val binding: ItemSensorCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: WaterParameterCard) {
            binding.apply {
                textSensorName.text = FormatUtils.getParameterLabel(card.parameter)
                textSensorValue.text = card.value
                textLastUpdate.text = "Updated: ${FormatUtils.formatTimeOnly(card.timestampMs)}"

                when (card.severity) {
                    AlertSeverity.INFO -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.normal_background)
                        )
                        textAlertStatus.text = "Normal"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, R.color.normal_text)
                        )
                    }
                    AlertSeverity.WARNING -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.warning_background)
                        )
                        textAlertStatus.text = "Warning"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, R.color.warning_text)
                        )
                    }
                    AlertSeverity.CRITICAL -> {
                        cardView.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.critical_background)
                        )
                        textAlertStatus.text = "Critical"
                        textAlertStatus.setTextColor(
                            ContextCompat.getColor(root.context, R.color.critical_text)
                        )
                    }
                }

                root.setOnClickListener { onParameterClick(card.parameter) }
            }
        }
    }

    companion object {
        fun createCardsFromReading(reading: WaterReading, getSeverity: (String) -> AlertSeverity): List<WaterParameterCard> {
            return listOf(
                WaterParameterCard("pH", FormatUtils.formatpH(reading.pH), getSeverity("pH"), reading.timestampMs),
                WaterParameterCard("Dissolved Oxygen", FormatUtils.formatDissolvedOxygen(reading.dissolvedOxygenMgL), getSeverity("Dissolved Oxygen"), reading.timestampMs),
                WaterParameterCard("Salinity", FormatUtils.formatSalinity(reading.salinityPpt), getSeverity("Salinity"), reading.timestampMs),
                WaterParameterCard("Ammonia", FormatUtils.formatAmmonia(reading.ammoniaMgL), getSeverity("Ammonia"), reading.timestampMs),
                WaterParameterCard("Temperature", FormatUtils.formatTemperature(reading.temperatureC), getSeverity("Temperature"), reading.timestampMs),
                WaterParameterCard("Water Level", FormatUtils.formatWaterLevel(reading.waterLevelCm), getSeverity("Water Level"), reading.timestampMs)
            )
        }
    }
}

private class ParameterCardDiffCallback : DiffUtil.ItemCallback<WaterParameterCard>() {
    override fun areItemsTheSame(oldItem: WaterParameterCard, newItem: WaterParameterCard): Boolean {
        return oldItem.parameter == newItem.parameter
    }

    override fun areContentsTheSame(oldItem: WaterParameterCard, newItem: WaterParameterCard): Boolean {
        return oldItem == newItem
    }
}