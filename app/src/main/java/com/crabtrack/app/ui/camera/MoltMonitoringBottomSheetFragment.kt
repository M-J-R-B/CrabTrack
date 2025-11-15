package com.crabtrack.app.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.databinding.BottomSheetMoltMonitoringBinding
import com.crabtrack.app.ui.molting.adapter.MoltEventAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Bottom sheet dialog for viewing molt monitoring details
 */
@AndroidEntryPoint
class MoltMonitoringBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMoltMonitoringBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private lateinit var moltEventAdapter: MoltEventAdapter

    private var tankName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMoltMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTitle()
        setupRecyclerView()
        setupCloseButton()
        observeViewModel()
    }

    private fun setupTitle() {
        binding.titleText.text = if (tankName != null) {
            "Molt Monitoring - $tankName"
        } else {
            "Molt Monitoring"
        }
    }

    private fun setupRecyclerView() {
        moltEventAdapter = MoltEventAdapter()

        binding.moltEventsRecyclerView.apply {
            adapter = moltEventAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupCloseButton() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    updateMoltStatus(uiState)
                }
            }
        }
    }

    private fun updateMoltStatus(uiState: CameraUiState) {
        binding.apply {
            // Update molt state chip
            moltStateChip.text = getMoltStateDisplayName(uiState.moltState)
            moltStateChip.setChipBackgroundColorResource(getMoltStateColor(uiState.moltState))

            // Update risk chip
            moltRiskChip.text = getRiskLevelDisplayName(uiState.moltRiskLevel)
            moltRiskChip.setChipBackgroundColorResource(getRiskLevelColor(uiState.moltRiskLevel))

            // Update care window
            moltCareWindowText.text = formatCareWindow(uiState.moltCareWindowRemaining)
                ?: getString(R.string.no_care_window)

            // Update molt events
            moltEventAdapter.submitList(uiState.moltEvents)

            // Show/hide empty state
            val hasEvents = uiState.moltEvents.isNotEmpty()
            moltEventsRecyclerView.isVisible = hasEvents
            moltEmptyStateText.isVisible = !hasEvents
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

    private fun getRiskLevelDisplayName(severity: AlertSeverity): String {
        return when (severity) {
            AlertSeverity.INFO -> "Normal"
            AlertSeverity.WARNING -> "Warning"
            AlertSeverity.CRITICAL -> "Critical"
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

    private fun getRiskLevelColor(severity: AlertSeverity): Int {
        return when (severity) {
            AlertSeverity.INFO -> android.R.color.holo_green_light
            AlertSeverity.WARNING -> android.R.color.holo_orange_light
            AlertSeverity.CRITICAL -> android.R.color.holo_red_dark
        }
    }

    private fun formatCareWindow(remainingMs: Long?): String? {
        remainingMs ?: return null

        if (remainingMs <= 0) return "00:00:00"

        val hours = remainingMs / (1000 * 60 * 60)
        val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (remainingMs % (1000 * 60)) / 1000

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(tankName: String? = null): MoltMonitoringBottomSheetFragment {
            return MoltMonitoringBottomSheetFragment().apply {
                this.tankName = tankName
            }
        }
    }
}
