package com.crabtrack.app.ui.settings

import android.R.attr.data
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 1. Create notification channel (required once)
        createNotificationChannel()

        // ✅ 2. Request notification permission (Android 13+)
        ensureNotificationPermission()

        // ✅ 3. Initialize all other features
        setupTextWatchers()
        setupObservers()
        setupClickListeners()
        setupInputValidation()

        // Note: Thresholds are automatically loaded from DataStore in ViewModel.init()
        // No need to load from Firebase on startup - DataStore is the source of truth
    }

    // ------------------------------------------------------------
// Notification setup helpers
// ------------------------------------------------------------

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "feeding_channel", // must match the one used in FeedingAlarmReceiver
                "Feeding Reminders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when it's time to feed your crabs"
            }

            val manager =
                requireContext().getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = requireContext().checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }


    private fun setupTextWatchers() {
        // Bind EditTexts to ViewModel StateFlows with real-time validation
        binding.phMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePhMin(text?.toString() ?: "")
        }
        binding.phMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePhMax(text?.toString() ?: "")
        }
        binding.salinityMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSalinityMin(text?.toString() ?: "")
        }
        binding.salinityMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSalinityMax(text?.toString() ?: "")
        }
        binding.tempMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTempMin(text?.toString() ?: "")
        }
        binding.tempMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTempMax(text?.toString() ?: "")
        }
        binding.tdsMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTdsMin(text?.toString() ?: "")
        }
        binding.tdsMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTdsMax(text?.toString() ?: "")
        }
        binding.turbidityMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTurbidityMax(text?.toString() ?: "")
        }
    }

    private fun setupObservers() {
        // Properly scoped collection using viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUI(state)
                    }
                }

                // Separate collection for real-time validation errors
                launch {
                    viewModel.validationErrors.collect { errors ->
                        updateValidationErrors(errors)
                    }
                }

                // Collection for save state
                launch {
                    viewModel.saveState.collect { saveState ->
                        updateSaveState(saveState)
                    }
                }
            }
        }
    }

    private fun updateUI(state: SettingsUiState) {
        // Show/hide loading
        binding.progressIndicator.visibility = if (state.isLoading || state.isSaving) View.VISIBLE else View.GONE

        // Enable/disable inputs
        val enabled = !state.isLoading && !state.isSaving
        setInputsEnabled(enabled)

        // Update input values
        state.thresholds?.let { thresholds ->
            populateInputs(thresholds)
        }

        // Show validation errors
        showValidationErrors(state.validationErrors)

        // Show messages with lifecycle awareness
        state.errorMessage?.let { message ->
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        viewModel.saveThresholds()
                    }
                    .show()
                viewModel.clearMessages()
            }
        }

        state.successMessage?.let { message ->
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun populateInputs(thresholds: Thresholds) {
        with(binding) {
            phMinInput.setText(thresholds.pHMin.toString())
            phMaxInput.setText(thresholds.pHMax.toString())
            salinityMinInput.setText(thresholds.salinityMin.toString())
            salinityMaxInput.setText(thresholds.salinityMax.toString())
            tempMinInput.setText(thresholds.tempMin.toString())
            tempMaxInput.setText(thresholds.tempMax.toString())
            tdsMinInput.setText(thresholds.tdsMin.toString())
            tdsMaxInput.setText(thresholds.tdsMax.toString())
            turbidityMaxInput.setText(thresholds.turbidityMax.toString())
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        with(binding) {
            phMinInput.isEnabled = enabled
            phMaxInput.isEnabled = enabled
            salinityMinInput.isEnabled = enabled
            salinityMaxInput.isEnabled = enabled
            tempMinInput.isEnabled = enabled
            tempMaxInput.isEnabled = enabled
            tdsMinInput.isEnabled = enabled
            tdsMaxInput.isEnabled = enabled
            turbidityMaxInput.isEnabled = enabled
            buttonSave.isEnabled = enabled
            buttonResetDefaults.isEnabled = enabled
        }
    }

    private fun showValidationErrors(errors: Map<String, String>) {
        with(binding) {
            // Clear all errors first
            phMinInputLayout.error = null
            phMaxInputLayout.error = null
            salinityMinInputLayout.error = null
            salinityMaxInputLayout.error = null
            tempMinInputLayout.error = null
            tempMaxInputLayout.error = null
            tdsMinInputLayout.error = null
            tdsMaxInputLayout.error = null
            turbidityMaxInputLayout.error = null

            // Show validation errors
            errors["ph"]?.let {
                phMinInputLayout.error = it
                phMaxInputLayout.error = it
            }
            errors["salinity"]?.let {
                salinityMinInputLayout.error = it
                salinityMaxInputLayout.error = it
            }
            errors["temperature"]?.let {
                tempMinInputLayout.error = it
                tempMaxInputLayout.error = it
            }
            errors["tds"]?.let {
                tdsMinInputLayout.error = it
                tdsMaxInputLayout.error = it
            }
            errors["turbidity"]?.let {
                turbidityMaxInputLayout.error = it
            }
        }
    }

    private fun updateValidationErrors(errors: Map<String, String>) {
        // Clear all error states first
        clearEditTextErrors()

        // Set errors for invalid fields with specific field targeting
        errors.forEach { (field, errorMessage) ->
            when (field) {
                "phMin" -> setEditTextError(binding.phMinInput, errorMessage)
                "phMax" -> setEditTextError(binding.phMaxInput, errorMessage)
                "salinityMin" -> setEditTextError(binding.salinityMinInput, errorMessage)
                "salinityMax" -> setEditTextError(binding.salinityMaxInput, errorMessage)
                "tempMin" -> setEditTextError(binding.tempMinInput, errorMessage)
                "tempMax" -> setEditTextError(binding.tempMaxInput, errorMessage)
                "tdsMin" -> setEditTextError(binding.tdsMinInput, errorMessage)
                "tdsMax" -> setEditTextError(binding.tdsMaxInput, errorMessage)
                "turbidityMax" -> setEditTextError(binding.turbidityMaxInput, errorMessage)
            }
        }
    }

    private fun clearEditTextErrors() {
        listOf(
            binding.phMinInput, binding.phMaxInput,
            binding.salinityMinInput, binding.salinityMaxInput,
            binding.tempMinInput, binding.tempMaxInput,
            binding.tdsMinInput, binding.tdsMaxInput, binding.turbidityMaxInput
        ).forEach { editText ->
            editText.error = null
        }
    }

    private fun setEditTextError(editText: EditText, error: String) {
        editText.error = error
    }

    private fun updateSaveState(saveState: SaveState) {
        when (saveState) {
            SaveState.Idle -> {
                // Normal state
            }
            SaveState.Saving -> {
                binding.buttonSave.isEnabled = false
                binding.buttonSave.text = "Saving..."
            }
            SaveState.Success -> {
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "Save"
                Snackbar.make(binding.root, "Settings saved successfully!", Snackbar.LENGTH_SHORT).show()
            }
            SaveState.Error -> {
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "Save"
            }
        }
    }

    private fun sendDispenseCommand(type: String, ml: Double) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Snackbar.make(binding.root, "User not logged in.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
        val dbRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("dispense")
            .child(type)

        val command = mapOf(
            "ml" to ml,
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending"
        )

        dbRef.setValue(command)
            .addOnSuccessListener {
                Snackbar.make(binding.root, "$type solution dispensing...", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Snackbar.make(binding.root, "Failed to send command: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }




    private fun setupClickListeners() {
        binding.buttonSave.setOnClickListener {
            viewModel.saveThresholds()
        }

        binding.buttonResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }

        binding.buttonLogout.setOnClickListener {
            // Clear any saved session or data if needed here later

            // Navigate to the login screen
            val navController = requireActivity()
                .supportFragmentManager
                .findFragmentById(com.crabtrack.app.R.id.nav_host_fragment)
                ?.findNavController()

            navController?.navigate(com.crabtrack.app.R.id.action_global_loginFragment)
        }

        binding.buttonPhDispense.setOnClickListener {
            val mlText = binding.mlPh.text.toString()
            if (mlText.isNotBlank()) {
                val ml = mlText.toDoubleOrNull()
                if (ml != null) {
                    sendDispenseCommand("ph", ml)
                } else {
                    Snackbar.make(binding.root, "Invalid input for pH", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Snackbar.make(binding.root, "Please enter amount in ml", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Feeding Reminder - Date & Time pickers
        binding.feedingDateInput.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val datePicker = android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selectedDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                    binding.feedingDateInput.setText(selectedDate)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        binding.feedingTimeInput.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val timePicker = android.app.TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                    binding.feedingTimeInput.setText(selectedTime)
                },
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                true
            )
            timePicker.show()
        }

        binding.buttonSetFeedingReminder.setOnClickListener {
            val date = binding.feedingDateInput.text.toString()
            val time = binding.feedingTimeInput.text.toString()

            if (date.isBlank() || time.isBlank()) {
                Snackbar.make(binding.root, "Please select both date and time.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Snackbar.make(binding.root, "User not logged in.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Parse the date and time into a Calendar object
            val dateParts = date.split("-") // yyyy-MM-dd
            val timeParts = time.split(":") // HH:mm

            if (dateParts.size != 3 || timeParts.size != 2) {
                Snackbar.make(binding.root, "Invalid date or time format.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1 // Calendar months are 0-indexed
            val day = dateParts[2].toInt()
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val calendar = java.util.Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
            }

            val triggerTime = calendar.timeInMillis
            if (triggerTime <= System.currentTimeMillis()) {
                Snackbar.make(binding.root, "Please select a future time.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Save reminder to Firebase
            val reminderData = mapOf(
                "date" to date,
                "time" to time,
                "timestamp" to triggerTime,
                "status" to "scheduled"
            )

            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.uid)
                .child("feeding_reminders")
                .push()
                .setValue(reminderData)
                .addOnSuccessListener {
                    // ✅ Schedule local alarm notification
                    val intent = Intent(requireContext(), FeedingAlarmReceiver::class.java)
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        requireContext(),
                        System.currentTimeMillis().toInt(),
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                                )
                            } else {
                                // ✅ Ask user to allow exact alarms
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${requireContext().packageName}")
                                }
                                startActivity(intent)
                                Snackbar.make(
                                    binding.root,
                                    "Please allow exact alarms for CrabTrack, then try again.",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return@addOnSuccessListener
                            }
                        } else {
                            // ✅ Older Android versions: safe to schedule directly
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                pendingIntent
                            )
                        }

                        Snackbar.make(binding.root, "Feeding reminder set!", Snackbar.LENGTH_SHORT).show()

                    } catch (se: SecurityException) {
                        Snackbar.make(
                            binding.root,
                            "Exact alarm permission not granted. Enable it in Settings.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }


                    Snackbar.make(binding.root, "Reminder set for $date at $time.", Snackbar.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Snackbar.make(binding.root, "Failed to set reminder: ${it.message}", Snackbar.LENGTH_LONG).show()
                }
        }


    }

    private fun setupInputValidation() {
        with(binding) {
            listOf(
                phMinInput, phMaxInput, salinityMinInput, salinityMaxInput,
                tempMinInput, tempMaxInput, tdsMinInput, tdsMaxInput, turbidityMaxInput
            ).forEach { input ->
                input.addTextChangedListener {
                    // Clear validation errors when user starts typing
                    viewModel.clearMessages()
                }
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}