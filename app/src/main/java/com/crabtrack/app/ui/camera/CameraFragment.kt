package com.crabtrack.app.ui.camera

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
import com.crabtrack.app.data.model.MoltingAlert
import com.crabtrack.app.data.model.Tank
import com.crabtrack.app.databinding.FragmentCameraBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
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

    // Map to store dynamically created tank chips
    private val tankChips = mutableMapOf<String, Chip>()

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

        // Handle navigation argument for tank selection
        handleTankIdArgument()

        // Listen for fragment result from molting alert navigation
        setupMoltingAlertListener()

        setupClickListeners()
        observeViewModel()
    }

    /**
     * Handle tankId argument passed from dashboard when a molting alert is tapped.
     * Selects the appropriate tank chip and shows a molting indicator.
     */
    private fun handleTankIdArgument() {
        // Get tankId from navigation arguments (if using direct navigation)
        val tankId = arguments?.getString("tankId")

        tankId?.let {
            android.util.Log.i("CameraFragment", "Navigated with tankId: $it")
            viewModel.selectTank(it)
            showMoltingIndicator(true)
        }
    }

    /**
     * Listen for fragment result when navigating from molting alert via bottom nav.
     */
    private fun setupMoltingAlertListener() {
        parentFragmentManager.setFragmentResultListener(
            "molting_alert_navigation",
            viewLifecycleOwner
        ) { _, bundle ->
            val tankId = bundle.getString("tankId")
            tankId?.let {
                android.util.Log.i("CameraFragment", "Received tankId from fragment result: $it")
                viewModel.selectTank(it)
                // Load the current alert for this tank to enable feedback actions
                viewModel.loadCurrentMoltingAlert(it)
                showMoltingIndicator(true)
            }
        }
    }

    /**
     * Shows or hides the molting action buttons based on tank's molting status.
     */
    private fun updateMoltingStatusButton(tankId: String) {
        if (viewModel.hasMoltingStatus(tankId)) {
            binding.moltingButtonsContainer.visibility = View.VISIBLE

            // Still Molting button (Blue)
            binding.buttonStillMolting.setOnClickListener {
                showStillMoltingDialog()
            }

            // Molting Complete button (Green)
            binding.buttonMoltingComplete.setOnClickListener {
                showMoltingCompleteDialog()
            }

            // False Alarm button (Yellow)
            binding.buttonFalseAlarm.setOnClickListener {
                showFalseAlarmDialog()
            }

            // Dismiss button (Gray)
            binding.buttonDismiss.setOnClickListener {
                showDismissDialog()
            }
        } else {
            binding.moltingButtonsContainer.visibility = View.GONE
        }
    }

    /**
     * Shows confirmation dialog for "Still Molting" action.
     */
    private fun showStillMoltingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = getString(R.string.dialog_still_molting_title)
        messageView.text = getString(R.string.dialog_still_molting_message)
        buttonYes.text = "Confirm"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.onStillMoltingAction()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Shows confirmation dialog for "Molting Complete" action.
     */
    private fun showMoltingCompleteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = "Molting Complete?"
        messageView.text = "This confirms the detection was correct and the crab has finished molting."
        buttonYes.text = "Confirm"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.onMoltingCompleteAction()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Shows confirmation dialog for "False Alarm" action.
     */
    private fun showFalseAlarmDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = "False Alarm?"
        messageView.text = "This will mark the detection as incorrect and help improve future accuracy."
        buttonYes.text = "Confirm"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.onFalseAlarmAction()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Shows confirmation dialog for "Dismiss" action (no feedback).
     */
    private fun showDismissDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = getString(R.string.dialog_dismiss_title)
        messageView.text = getString(R.string.dialog_dismiss_message)
        buttonYes.text = "Dismiss"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.onDismissAction()
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Shows or hides a molting indicator on the crab details card.
     */
    private fun showMoltingIndicator(show: Boolean) {
        if (show) {
            // Add visual indication that this tank has a molting alert
            binding.crabDetailsCard.strokeWidth = 4
            binding.crabDetailsCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.critical_text)
            android.util.Log.i("CameraFragment", "Molting indicator shown")
        } else {
            binding.crabDetailsCard.strokeWidth = 0
        }
    }

    private fun showProfileSuccessDialog(
        title: String = "Profile updated",
        message: String = "Your profile changes have been saved successfully."
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CrabTrack_AlertDialog
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

        // Crab details card - show crab details dialog for selected tank
        binding.crabDetailsCard.setOnClickListener {
            val selectedTankId = viewModel.selectedTankId.value
            showCrabDetailsDialog(selectedTankId)
        }

        // Add Tank button
        binding.addTankChip.setOnClickListener {
            showAddTankConfirmDialog()
        }
    }

    /**
     * Updates the tank chip container with dynamic chips based on tank list
     */
    private fun updateTankChips(tanks: List<Tank>) {
        val container = binding.tankChipContainer

        // Remove all existing tank chips (keep addTankChip)
        tankChips.values.forEach { container.removeView(it) }
        tankChips.clear()

        // Get the index of addTankChip to insert before it
        val addChipIndex = container.indexOfChild(binding.addTankChip)

        // Create chips for each tank
        tanks.forEachIndexed { index, tank ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = tank.displayName
                isCheckable = true
                isChecked = tank.id == viewModel.selectedTankId.value
                chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width_default)
                setChipStrokeColorResource(R.color.primary)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = (8 * resources.displayMetrics.density).toInt()
                layoutParams = params

                // Click to select tank
                setOnClickListener {
                    viewModel.selectTank(tank.id)
                }

                // Long press for options menu
                setOnLongClickListener {
                    showTankOptionsMenu(it, tank)
                    true
                }
            }

            tankChips[tank.id] = chip
            container.addView(chip, addChipIndex + index)
        }
    }

    /**
     * Updates chip selection state based on selected tank
     */
    private fun updateSelectedTankChip(selectedTankId: String) {
        tankChips.forEach { (tankId, chip) ->
            chip.isChecked = tankId == selectedTankId
        }

        // Update crab details display for selected tank
        updateCrabInfoDisplay()

        // Update molting status button for selected tank
        updateMoltingStatusButton(selectedTankId)

        // Update stream title
        val tank = viewModel.getSelectedTank()
        tank?.let {
            binding.streamTitleText.text = "${it.displayName} - Top View"
        }
    }

    /**
     * Shows popup menu with tank options (Edit IP, Delete)
     */
    private fun showTankOptionsMenu(anchor: View, tank: Tank) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_tank_options, popup.menu)

        // Hide delete option for tank1
        if (tank.number == 1) {
            popup.menu.findItem(R.id.action_delete_tank)?.isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_ip -> {
                    showEditTankDialog(tank)
                    true
                }
                R.id.action_delete_tank -> {
                    showDeleteTankDialog(tank)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    /**
     * Shows dialog to add a new tank
     */
    private fun showAddTankConfirmDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        val nextNumber = (viewModel.tanks.value.maxOfOrNull { it.number } ?: 0) + 1
        titleView.text = "Add Tank $nextNumber?"
        messageView.text = "This will create a new tank for monitoring. New tanks use the same camera IP as Tank 1 by default."
        buttonYes.text = "Add"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.addTank()
            dialog.dismiss()
            showProfileSuccessDialog(
                title = "Tank Added",
                message = "Tank $nextNumber has been created. Long-press to edit its camera IP."
            )
        }

        dialog.show()
    }

    /**
     * Shows dialog to edit tank camera IP
     */
    private fun showEditTankDialog(tank: Tank) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_tank, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val cameraIpLayout = dialogView.findViewById<TextInputLayout>(R.id.camera_ip_layout)
        val cameraIpInput = dialogView.findViewById<TextInputEditText>(R.id.camera_ip_input)
        val buttonCancel = dialogView.findViewById<MaterialButton>(R.id.button_cancel)
        val buttonSave = dialogView.findViewById<MaterialButton>(R.id.button_save)

        titleView.text = "Edit ${tank.displayName}"
        cameraIpInput.setText(tank.cameraIp)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonCancel.setOnClickListener { dialog.dismiss() }
        buttonSave.setOnClickListener {
            val newIp = cameraIpInput.text?.toString()?.trim() ?: ""

            // Basic IP validation
            if (newIp.isEmpty()) {
                cameraIpLayout.error = "IP address is required"
                return@setOnClickListener
            }

            if (!isValidIpAddress(newIp)) {
                cameraIpLayout.error = "Invalid IP address format"
                return@setOnClickListener
            }

            cameraIpLayout.error = null
            viewModel.updateTankIp(tank.id, newIp)
            dialog.dismiss()

            showProfileSuccessDialog(
                title = "IP Updated",
                message = "${tank.displayName} camera IP set to $newIp"
            )
        }

        dialog.show()
    }

    /**
     * Validates IP address format
     */
    private fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }

    /**
     * Shows confirmation dialog to delete a tank
     */
    private fun showDeleteTankDialog(tank: Tank) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = "Delete ${tank.displayName}?"
        messageView.text = "This will delete the tank and all associated crab details. This action cannot be undone."
        buttonYes.text = "Delete"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonNo.setOnClickListener { dialog.dismiss() }
        buttonYes.setOnClickListener {
            viewModel.deleteTank(tank.id)
            dialog.dismiss()
            showProfileSuccessDialog(
                title = "Tank Deleted",
                message = "${tank.displayName} has been removed."
            )
        }

        dialog.show()
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
        val placedDateLayout = dialogView.findViewById<TextInputLayout>(R.id.placed_date_layout)
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
            // Validate required field
            val placedDateText = placedDateInput.text?.toString()
            if (placedDateText.isNullOrBlank()) {
                placedDateLayout.error = "Date Placed in Tank is required"
                return@setOnClickListener
            }
            placedDateLayout.error = null

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
        val selectedTankId = viewModel.selectedTankId.value
        val tankDetails = viewModel.getCrabDetails(selectedTankId)

        if (tankDetails?.hasDetails() == true) {
            // Show crab details
            binding.crabNameText.text = tankDetails.crabName ?: "Crab"

            // Build details subtitle
            val detailParts = mutableListOf<String>()
            tankDetails.placedDate?.let { detailParts.add("Placed: $it") }
            tankDetails.initialWeightGrams?.let { detailParts.add("%.1fg".format(it)) }

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
        // Observe UI state
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

        // Observe tanks list
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tanks.collect { tanks ->
                    updateTankChips(tanks)
                }
            }
        }

        // Observe selected tank
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedTankId.collect { selectedTankId ->
                    updateSelectedTankChip(selectedTankId)
                }
            }
        }

        // Observe events (toast messages, hide buttons, etc.)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    handleCameraEvent(event)
                }
            }
        }
    }

    /**
     * Handles events from the ViewModel (toasts, UI changes, etc.)
     */
    private fun handleCameraEvent(event: CameraEvent) {
        when (event) {
            is CameraEvent.ShowToast -> {
                val message = when (event.type) {
                    CameraEvent.ToastType.STILL_MOLTING -> getString(R.string.toast_still_molting)
                    CameraEvent.ToastType.MOLTING_COMPLETE -> getString(R.string.toast_molting_complete)
                    CameraEvent.ToastType.FALSE_ALARM -> getString(R.string.toast_false_alarm)
                    CameraEvent.ToastType.DISMISSED -> getString(R.string.toast_alert_dismissed)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            is CameraEvent.ShowError -> {
                Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
            }
            is CameraEvent.HideMoltingButtons -> {
                binding.moltingButtonsContainer.visibility = View.GONE
                showMoltingIndicator(false)
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
        tankChips.clear()
        _binding = null
    }
}
