package com.crabtrack.app.ui.molting

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.crabtrack.app.R
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.databinding.FragmentMoltingBinding
import com.crabtrack.app.presentation.dashboard.adapter.MoltingAlertAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class MoltingFragment : Fragment() {

    private var _binding: FragmentMoltingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoltingViewModel by viewModels()
    private lateinit var moltingAlertAdapter: MoltingAlertAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoltingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeUiState()
    }

    private fun setupRecyclerView() {
        moltingAlertAdapter = MoltingAlertAdapter { alert ->
            viewModel.onMoltingAlertClicked(alert)
            showMoltingDetailDialog(alert)
        }
        binding.recyclerViewMoltingAlerts.apply {
            adapter = moltingAlertAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun showMoltingDetailDialog(alert: MoltingAlert) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_molting_detail, null)

        // Get view references
        val imageMolting = dialogView.findViewById<ImageView>(R.id.image_molting)
        val textNoImage = dialogView.findViewById<TextView>(R.id.text_no_image)
        val textTankName = dialogView.findViewById<TextView>(R.id.text_tank_name)
        val textDetectionClass = dialogView.findViewById<TextView>(R.id.text_detection_class)
        val textConfidence = dialogView.findViewById<TextView>(R.id.text_confidence)
        val textTimestamp = dialogView.findViewById<TextView>(R.id.text_timestamp)
        val buttonClose = dialogView.findViewById<MaterialButton>(R.id.button_close)
        val buttonGoToTank = dialogView.findViewById<MaterialButton>(R.id.button_go_to_tank)

        // Set tank name (format: "tank1" -> "Tank 1")
        val tankName = alert.tankId.replace("tank", "Tank ").trim()
        textTankName.text = tankName

        // Set detection class
        textDetectionClass.text = alert.detectionClass

        // Set confidence
        textConfidence.text = "${(alert.confidence * 100).toInt()}%"

        // Set timestamp (relative time)
        textTimestamp.text = formatRelativeTime(alert.timestamp)

        // Load image using Glide
        if (!alert.imagePath.isNullOrBlank()) {
            imageMolting.visibility = View.VISIBLE
            textNoImage.visibility = View.GONE
            Glide.with(requireContext())
                .load(alert.imagePath)
                .placeholder(R.drawable.ic_schedule)
                .error(R.drawable.ic_error_outline)
                .centerCrop()
                .into(imageMolting)
        } else {
            imageMolting.visibility = View.GONE
            textNoImage.visibility = View.VISIBLE
        }

        // Create and show dialog
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)

        buttonClose.setOnClickListener {
            dialog.dismiss()
        }

        buttonGoToTank.setOnClickListener {
            dialog.dismiss()
            viewModel.navigateToTank(alert.tankId)
        }

        dialog.show()
    }

    private fun formatRelativeTime(timestamp: String): String {
        return try {
            // Try parsing with milliseconds first
            val formatWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            formatWithMillis.timeZone = TimeZone.getTimeZone("UTC")
            val date = try {
                formatWithMillis.parse(timestamp)
            } catch (e: Exception) {
                // Fallback to format without milliseconds
                val formatWithoutMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                formatWithoutMillis.timeZone = TimeZone.getTimeZone("UTC")
                formatWithoutMillis.parse(timestamp)
            }
            date?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } ?: "Unknown time"
        } catch (e: Exception) {
            "Unknown time"
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUI(uiState)
                    }
                }

                launch {
                    viewModel.navigationEvents.collect { event ->
                        handleNavigationEvent(event)
                    }
                }
            }
        }
    }

    private fun handleNavigationEvent(event: MoltingNavigationEvent) {
        when (event) {
            is MoltingNavigationEvent.NavigateToCamera -> {
                navigateToCamera(event.tankId)
            }
        }
    }

    private fun navigateToCamera(tankId: String) {
        try {
            // Store tankId in fragment result for CameraFragment to read
            parentFragmentManager.setFragmentResult(
                "molting_alert_navigation",
                bundleOf("tankId" to tankId)
            )

            // Use bottom navigation's selectedItemId to switch tabs
            // This ensures proper back stack behavior (no stacking)
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav.selectedItemId = R.id.cameraFragment

            android.util.Log.i("MoltingFragment", "Navigating to camera with tankId: $tankId")
        } catch (e: Exception) {
            android.util.Log.e("MoltingFragment", "Failed to navigate to camera: ${e.message}", e)
        }
    }

    private fun updateUI(uiState: MoltingUiState) {
        android.util.Log.d("MoltingFragment", "updateUI called - alertCount: ${uiState.moltingAlerts.size}, isLoading: ${uiState.isLoading}")

        // Show/hide empty state
        if (uiState.moltingAlerts.isEmpty() && !uiState.isLoading) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewMoltingAlerts.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewMoltingAlerts.visibility = View.VISIBLE
        }

        moltingAlertAdapter.submitList(uiState.moltingAlerts)

        uiState.errorMessage?.let { message ->
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Dismiss") { viewModel.clearError() }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
