package com.crabtrack.app.ui.camera

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.crabtrack.app.R
import com.crabtrack.app.data.model.CrabDetails
import com.crabtrack.app.databinding.FragmentCameraBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fragment displaying RTSP video stream with polished UI
 */
@UnstableApi
@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()
    private var isStreamActive = false

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

        setupClickListeners()
        observeViewModel()
    }

    private fun showProfileSuccessDialog(
        title: String = "Profile updated",
        message: String = "Your profile changes have been saved successfully."
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CrabTrack_AlertDialog   // or your existing CrabTrack_LogoutDialog
        )
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        val textTitle = dialogView.findViewById<TextView>(R.id.text_title)
        val textMessage = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        textTitle.text = title
        textMessage.text = message

        buttonOk.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupClickListeners() {
        // Play/Stop button
        binding.playButton.setOnClickListener {
            if (isStreamActive) {
                stopStream()
            } else {
                startStream()
            }
        }

        // Retry button
        binding.retryButton.setOnClickListener {
            viewModel.retry()
            isStreamActive = true
            updatePlayButton(isPlaying = true)
        }

        // Crab details card - show crab details dialog
        binding.crabDetailsCard.setOnClickListener {
            showCrabDetailsDialog("tank1")
        }

        // Add Tank button (placeholder)
        binding.addTankChip.setOnClickListener {
            showProfileSuccessDialog(
                title = "Under Development",
                message = "This feature is coming soon!"
            )
        }
    }

    private fun showCrabDetailsDialog(tankId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_crab_details, null)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CrabTrack_AlertDialog
        )
            .setView(dialogView)
            .create()

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)

        // Get references to views
        val titleText = dialogView.findViewById<TextView>(R.id.text_title)
        val crabNameInput = dialogView.findViewById<TextInputEditText>(R.id.crab_name_input)
        val placedDateInput = dialogView.findViewById<TextInputEditText>(R.id.placed_date_input)
        val initialWeightInput = dialogView.findViewById<TextInputEditText>(R.id.initial_weight_input)
        val removalDateInput = dialogView.findViewById<TextInputEditText>(R.id.removal_date_input)
        val removalWeightInput = dialogView.findViewById<TextInputEditText>(R.id.removal_weight_input)
        val weightChangeText = dialogView.findViewById<TextView>(R.id.weight_change_text)
        val buttonReset = dialogView.findViewById<MaterialButton>(R.id.button_reset)
        val buttonCancel = dialogView.findViewById<MaterialButton>(R.id.button_cancel)
        val buttonSave = dialogView.findViewById<MaterialButton>(R.id.button_save)

        // Set title based on tank
        val tankNumber = tankId.replace("tank", "")
        titleText.text = "Tank $tankNumber - Crab Details"

        // Variables to store timestamps
        var placedDateMs: Long? = null
        var removalDateMs: Long? = null

        // Populate existing data
        val existingDetails = viewModel.getCrabDetails(tankId)
        existingDetails?.let { details ->
            crabNameInput.setText(details.crabName ?: "")
            placedDateInput.setText(details.placedDate ?: "")
            placedDateMs = details.placedDateMs
            initialWeightInput.setText(details.initialWeightGrams?.toString() ?: "")
            removalDateInput.setText(details.removalDate ?: "")
            removalDateMs = details.removalDateMs
            removalWeightInput.setText(details.removalWeightGrams?.toString() ?: "")

            // Show weight change if both weights exist
            details.getWeightChange()?.let { change ->
                val changeText = if (change >= 0) "+%.1fg".format(change) else "%.1fg".format(change)
                val color = if (change >= 0) R.color.normal_text else R.color.warning_text
                weightChangeText.text = "Weight Change: $changeText"
                weightChangeText.setTextColor(ContextCompat.getColor(requireContext(), color))
                weightChangeText.visibility = View.VISIBLE
            }
        }

        // Date pickers
        placedDateInput.setOnClickListener {
            showDatePicker { selectedDate, timestampMs ->
                placedDateInput.setText(selectedDate)
                placedDateMs = timestampMs
            }
        }

        removalDateInput.setOnClickListener {
            showDatePicker { selectedDate, timestampMs ->
                removalDateInput.setText(selectedDate)
                removalDateMs = timestampMs
            }
        }

        // Reset button
        buttonReset.setOnClickListener {
            showResetConfirmDialog(tankNumber) {
                viewModel.resetCrabDetails(tankId)
                dialog.dismiss()
                showProfileSuccessDialog(
                    title = "Details Reset",
                    message = "Crab details for Tank $tankNumber have been cleared."
                )
                updateCrabInfoDisplay()
            }
        }

        // Cancel button
        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Save button
        buttonSave.setOnClickListener {
            val details = CrabDetails.create(
                tankId = tankId,
                crabName = crabNameInput.text?.toString()?.takeIf { it.isNotBlank() },
                placedDate = placedDateInput.text?.toString()?.takeIf { it.isNotBlank() },
                placedDateMs = placedDateMs,
                initialWeightGrams = initialWeightInput.text?.toString()?.toDoubleOrNull(),
                removalDate = removalDateInput.text?.toString()?.takeIf { it.isNotBlank() },
                removalDateMs = removalDateMs,
                removalWeightGrams = removalWeightInput.text?.toString()?.toDoubleOrNull(),
                existingCreatedAt = existingDetails?.createdAt
            )

            viewModel.saveCrabDetails(details)
            dialog.dismiss()

            showProfileSuccessDialog(
                title = "Details Saved",
                message = "Crab details for Tank $tankNumber have been saved."
            )
        }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (String, Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val timestampMs = calendar.timeInMillis
                onDateSelected(selectedDate, timestampMs)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun showResetConfirmDialog(tankNumber: String, onConfirm: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = "Reset Crab Details?"
        messageView.text = "This will clear all crab details for Tank $tankNumber. Use this when adding a new crab."

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

    private fun updateCrabInfoDisplay() {
        val tank1Details = viewModel.getCrabDetails("tank1")

        if (tank1Details?.hasDetails() == true) {
            // Show crab details
            binding.crabNameText.text = tank1Details.crabName ?: "Crab"

            // Build details subtitle
            val detailParts = mutableListOf<String>()
            tank1Details.placedDate?.let { detailParts.add("Placed: $it") }
            tank1Details.initialWeightGrams?.let { detailParts.add("%.1fg".format(it)) }

            if (detailParts.isNotEmpty()) {
                binding.crabDetailsText.text = detailParts.joinToString("  •  ")
                binding.crabDetailsText.visibility = View.VISIBLE
            } else {
                binding.crabDetailsText.visibility = View.GONE
            }
        } else {
            // Show empty state
            binding.crabNameText.text = "Tap to add crab details"
            binding.crabDetailsText.visibility = View.GONE
        }
    }

    private fun startStream() {
        viewModel.initializePlayer()
        binding.playerView.player = viewModel.player
        isStreamActive = true
        updatePlayButton(isPlaying = true)
    }

    private fun stopStream() {
        binding.playerView.player = null
        viewModel.releasePlayer()
        isStreamActive = false
        updatePlayButton(isPlaying = false)
    }

    private fun updatePlayButton(isPlaying: Boolean) {
        binding.playButton.apply {
            if (isPlaying) {
                text = "Stop"
                setIconResource(android.R.drawable.ic_media_pause)
            } else {
                text = "Play"
                setIconResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    updateUI(uiState)
                }
            }
        }

        // Observe crab details
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.crabDetailsMap.collect {
                    updateCrabInfoDisplay()
                }
            }
        }
    }

    private fun updateUI(uiState: CameraUiState) {
        binding.apply {
            // Loading indicator
            loadingIndicator.isVisible = uiState.isLoading

            // Connection status overlay (only show when not playing)
            connectionStatusText.apply {
                text = uiState.connectionStatus
                isVisible = uiState.isLoading && !uiState.isPlaying
            }

            // Status badge
            statusBadge.apply {
                when {
                    uiState.isPlaying -> {
                        text = "Live"
                        setChipBackgroundColorResource(android.R.color.holo_green_light)
                    }
                    uiState.isLoading -> {
                        text = "Connecting"
                        setChipBackgroundColorResource(android.R.color.holo_orange_light)
                    }
                    uiState.errorMessage != null -> {
                        text = "Error"
                        setChipBackgroundColorResource(android.R.color.holo_red_light)
                    }
                    else -> {
                        text = "Offline"
                        chipBackgroundColor = android.content.res.ColorStateList.valueOf(0xFFE0E0E0.toInt())
                    }
                }
            }

            // Quality badge - show when stream is active
            qualityBadge.isVisible = uiState.isPlaying

            // Error handling
            if (uiState.errorMessage != null) {
                errorLayout.isVisible = true
                errorMessageText.text = uiState.errorMessage
                // Hide player when error occurs
                playerView.isVisible = false
            } else {
                errorLayout.isVisible = false
                playerView.isVisible = isStreamActive
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep stream running if user navigates away but wants to come back
        // Only stop if they explicitly pressed stop button
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up player when fragment is destroyed
        if (isStreamActive) {
            stopStream()
        }
        _binding = null
    }
}
