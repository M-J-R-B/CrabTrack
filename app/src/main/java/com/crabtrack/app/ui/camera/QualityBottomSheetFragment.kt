package com.crabtrack.app.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.crabtrack.app.data.model.VideoQuality
import com.crabtrack.app.databinding.BottomSheetQualitySelectorBinding
import com.crabtrack.app.databinding.ItemQualityOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog for selecting video quality
 */
class QualityBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQualitySelectorBinding? = null
    private val binding get() = _binding!!

    private var currentQuality: VideoQuality = VideoQuality.MEDIUM
    private var onQualitySelected: ((VideoQuality) -> Unit)? = null
    private var networkType: String? = null
    private var recommendedQuality: VideoQuality? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetQualitySelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNetworkInfo()
        setupQualityOptions()
        setupCloseButton()
    }

    private fun setupNetworkInfo() {
        if (networkType != null) {
            binding.networkInfoLayout.isVisible = true
            binding.networkInfoText.text = "Connected via $networkType"
        } else {
            binding.networkInfoLayout.isVisible = false
        }
    }

    private fun setupQualityOptions() {
        binding.qualityRadioGroup.removeAllViews()

        VideoQuality.entries.forEach { quality ->
            val itemBinding = ItemQualityOptionBinding.inflate(
                layoutInflater,
                binding.qualityRadioGroup,
                false
            )

            itemBinding.apply {
                // Set quality info
                qualityName.text = quality.displayName
                qualityDetails.text = "${quality.resolution} • ~${quality.estimatedMBPerMinute.toInt()} MB/min"

                // Set radio button state
                qualityRadio.isChecked = quality == currentQuality

                // Show recommended chip if this quality is recommended
                recommendedChip.isVisible = quality == recommendedQuality

                // Handle selection
                qualityCard.setOnClickListener {
                    onQualitySelected?.invoke(quality)
                    dismiss()
                }

                // Update card appearance for selected item
                if (quality == currentQuality) {
                    qualityCard.strokeWidth = 4
                    qualityCard.setStrokeColor(
                        androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(),
                            com.crabtrack.app.R.color.primary
                        )
                    )
                }
            }

            binding.qualityRadioGroup.addView(itemBinding.root)
        }
    }

    private fun setupCloseButton() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            currentQuality: VideoQuality,
            networkType: String? = null,
            recommendedQuality: VideoQuality? = null,
            onQualitySelected: (VideoQuality) -> Unit
        ): QualityBottomSheetFragment {
            return QualityBottomSheetFragment().apply {
                this.currentQuality = currentQuality
                this.networkType = networkType
                this.recommendedQuality = recommendedQuality
                this.onQualitySelected = onQualitySelected
            }
        }
    }
}
