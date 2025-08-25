package com.crabtrack.app.presentation.settings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.crabtrack.app.data.model.SensorType
import com.crabtrack.app.data.model.Threshold
import com.crabtrack.app.databinding.ItemThresholdBinding
import java.text.DecimalFormat

class ThresholdAdapter(
    private val onEditThreshold: (SensorType) -> Unit
) : ListAdapter<Threshold, ThresholdAdapter.ThresholdViewHolder>(ThresholdDiffCallback()) {

    private val decimalFormat = DecimalFormat("#.##")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThresholdViewHolder {
        val binding = ItemThresholdBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThresholdViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThresholdViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThresholdViewHolder(
        private val binding: ItemThresholdBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(threshold: Threshold) {
            binding.apply {
                textSensorName.text = threshold.sensorType.displayName
                textSensorUnit.text = threshold.sensorType.unit

                // Warning thresholds
                textWarningMin.text = threshold.warningMin?.let { 
                    decimalFormat.format(it) 
                } ?: "Not set"
                
                textWarningMax.text = threshold.warningMax?.let { 
                    decimalFormat.format(it) 
                } ?: "Not set"

                // Critical thresholds
                textCriticalMin.text = threshold.criticalMin?.let { 
                    decimalFormat.format(it) 
                } ?: "Not set"
                
                textCriticalMax.text = threshold.criticalMax?.let { 
                    decimalFormat.format(it) 
                } ?: "Not set"

                // Edit button
                buttonEdit.setOnClickListener {
                    onEditThreshold(threshold.sensorType)
                }

                root.setOnClickListener {
                    onEditThreshold(threshold.sensorType)
                }
            }
        }
    }
}

private class ThresholdDiffCallback : DiffUtil.ItemCallback<Threshold>() {
    override fun areItemsTheSame(oldItem: Threshold, newItem: Threshold): Boolean {
        return oldItem.sensorType == newItem.sensorType
    }

    override fun areContentsTheSame(oldItem: Threshold, newItem: Threshold): Boolean {
        return oldItem == newItem
    }
}