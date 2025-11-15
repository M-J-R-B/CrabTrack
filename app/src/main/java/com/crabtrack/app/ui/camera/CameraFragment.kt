package com.crabtrack.app.ui.camera

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.MoltState
import com.crabtrack.app.databinding.FragmentCameraBinding
import com.crabtrack.app.ui.camera.adapter.CameraStreamAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment displaying live camera streams for tank monitoring
 */
@AndroidEntryPoint
class CameraFragment : Fragment() {
    
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CameraViewModel by viewModels()
    private lateinit var streamAdapter: CameraStreamAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupMoltMonitoringCard()
        setupTankChips()
        observeViewModel()
        observeLifecycleEvents()
    }
    
    private fun setupRecyclerView() {
        streamAdapter = CameraStreamAdapter(
            fragmentManager = childFragmentManager,
            onPlayPause = { stream ->
                viewModel.toggleStream(stream)
            },
            onQualityChange = { stream, quality ->
                viewModel.changeStreamQuality(stream.id, quality)
            },
            onSnapshot = { stream ->
                viewModel.takeSnapshot(stream.id)
            },
            recommendedQuality = viewModel.uiState.value.recommendedQuality,
            networkType = getNetworkTypeDisplayName(viewModel.uiState.value.networkType)
        )

        binding.streamsRecyclerView.apply {
            adapter = streamAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun getNetworkTypeDisplayName(networkType: com.crabtrack.app.data.util.NetworkType): String {
        return when (networkType) {
            com.crabtrack.app.data.util.NetworkType.WIFI -> "WiFi"
            com.crabtrack.app.data.util.NetworkType.MOBILE_DATA -> "Mobile Data"
            com.crabtrack.app.data.util.NetworkType.ETHERNET -> "Ethernet"
            com.crabtrack.app.data.util.NetworkType.ROAMING -> "Roaming"
            com.crabtrack.app.data.util.NetworkType.NONE -> "No Connection"
        }
    }

    private fun setupMoltMonitoringCard() {
        // Find views from included layout - use the include tag's ID
        val moltCard = binding.root.findViewById<View>(R.id.moltMonitoringCardInclude)
        val moltHeaderLayout = moltCard?.findViewById<View>(R.id.moltHeaderLayout)

        // Setup click listener to open bottom sheet
        moltHeaderLayout?.setOnClickListener {
            showMoltMonitoringBottomSheet()
        }
    }

    private fun showMoltMonitoringBottomSheet() {
        val tankName = viewModel.uiState.value.selectedTankId?.let { "Tank ${it.takeLast(3)}" }
        val bottomSheet = MoltMonitoringBottomSheetFragment.newInstance(tankName)
        bottomSheet.show(childFragmentManager, "MoltMonitoringBottomSheet")
    }
    
    private fun setupTankChips() {
        binding.tankChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                selectedChip?.tag?.let { tag ->
                    val tankId = tag as String
                    viewModel.selectTank(tankId)
                }
            }
        }
    }

    private fun showAddTankDialog() {
        // Show a simple message for now - in production this would open a tank setup dialog
        showMessage("Add tank functionality - coming soon!")
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe UI state
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUI(uiState)
                    }
                }
                
                // Observe UI events
                launch {
                    viewModel.uiEvents.collect { event ->
                        event?.let { handleUiEvent(it) }
                    }
                }
                
                // Observe stream metrics
                launch {
                    viewModel.streamMetrics.collect { metricsMap ->
                        metricsMap.forEach { (streamId, metrics) ->
                            streamAdapter.updateStreamMetrics(streamId, metrics)
                        }
                    }
                }
                
                // Observe available tanks
                launch {
                    viewModel.availableTanks.collect { tanks ->
                        updateTankChips(tanks)
                    }
                }
            }
        }
    }
    
    private fun observeLifecycleEvents() {
        // Auto-refresh when fragment becomes visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshStreams()
            }
        }
    }
    
    private fun updateUI(uiState: CameraUiState) {
        binding.apply {
            // Loading state
            loadingIndicator.isVisible = uiState.isLoading

            // Streams
            streamAdapter.submitList(uiState.streams)

            // Empty state
            val hasStreams = uiState.streams.isNotEmpty()
            streamsRecyclerView.isVisible = hasStreams && !uiState.isLoading
            emptyStateLayout.isVisible = !hasStreams && !uiState.isLoading

            // Error handling
            uiState.errorMessage?.let { message ->
                if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    showError(message)
                    viewModel.clearError()
                }
            }
        }

        // Update molt monitoring card
        updateMoltMonitoringCard(uiState)
    }

    private fun updateMoltMonitoringCard(uiState: CameraUiState) {
        val moltCard = binding.root.findViewById<View>(R.id.moltMonitoringCardInclude) ?: return

        // Find views
        val stateChip = moltCard.findViewById<Chip>(R.id.moltStateChip)
        val riskChip = moltCard.findViewById<Chip>(R.id.moltRiskChip)
        val careWindowText = moltCard.findViewById<android.widget.TextView>(R.id.moltCareWindowText)

        // Update molt state chip
        stateChip?.apply {
            text = getMoltStateDisplayName(uiState.moltState)
            setChipBackgroundColorResource(getMoltStateColor(uiState.moltState))
        }

        // Update risk chip
        riskChip?.apply {
            text = getRiskLevelDisplayName(uiState.moltRiskLevel)
            setChipBackgroundColorResource(getRiskLevelColor(uiState.moltRiskLevel))
        }

        // Update care window
        careWindowText?.text = formatCareWindow(uiState.moltCareWindowRemaining)
            ?: getString(R.string.no_care_window)
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
    
    private fun updateTankChips(tanks: List<String>) {
        binding.tankChipGroup.removeAllViews()

        tanks.forEachIndexed { index, tankId ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = "Tank ${index + 1}"
                tag = tankId
                isCheckable = true
                chipStrokeWidth = 2f.dpToPx()
                chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            }
            binding.tankChipGroup.addView(chip)
        }

        // Add "Add Tank" chip at the end
        val addChip = Chip(requireContext()).apply {
            id = View.generateViewId()
            text = "+ Add Tank"
            isCheckable = false
            chipStrokeWidth = 1f.dpToPx()
            chipStrokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            setOnClickListener {
                showAddTankDialog()
            }
        }
        binding.tankChipGroup.addView(addChip)

        // Select first tank by default
        if (tanks.isNotEmpty() && binding.tankChipGroup.checkedChipId == View.NO_ID) {
            binding.tankChipGroup.check(binding.tankChipGroup.getChildAt(0).id)
        }
    }

    // Extension function to convert dp to pixels
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
    
    private fun handleUiEvent(event: CameraUiEvent) {
        when (event) {
            is CameraUiEvent.ShowMessage -> {
                showMessage(event.message)
            }
            is CameraUiEvent.StreamStarted -> {
                showMessage("Stream started")
            }
            is CameraUiEvent.StreamStopped -> {
                showMessage("Stream stopped")
            }
            is CameraUiEvent.SnapshotTaken -> {
                showMessage("Snapshot saved")
            }
        }
        viewModel.clearUiEvent()
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                viewModel.refreshStreams()
            }
            .show()
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop all streams when fragment goes to background to save bandwidth
        viewModel.stopAllStreams()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}