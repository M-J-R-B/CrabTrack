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
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.RecurrenceType
import com.crabtrack.app.databinding.FragmentSettingsBinding
import com.crabtrack.app.ui.settings.adapter.FeedingReminderAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.crabtrack.app.data.util.NetworkTypeDetector
import com.crabtrack.app.data.util.DataUsageTracker
import com.crabtrack.app.data.util.NetworkType
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.storage.FirebaseStorage
import com.bumptech.glide.Glide
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream



@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val viewModel: SettingsViewModel by viewModels()
    private var dispensingJob: kotlinx.coroutines.Job? = null
    private lateinit var reminderAdapter: FeedingReminderAdapter

    @Inject
    lateinit var networkTypeDetector: NetworkTypeDetector

    private lateinit var storage: FirebaseStorage
    private var imageUri: Uri? = null

    @Inject
    lateinit var dataUsageTracker: DataUsageTracker

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }


    // Convert Base64 string to Bitmap
    private fun base64ToBitmap(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    private fun uploadProfileImage() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uri = imageUri ?: return

        val storageRef = FirebaseStorage.getInstance()
            .getReference("profile_images/${user.uid}.jpg")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    // ✅ Save to database
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(user.uid)
                        .child("profileImage")
                        .setValue(downloadUrl.toString())
                        .addOnSuccessListener {
                            Snackbar.make(binding.root, "Profile picture updated!", Snackbar.LENGTH_SHORT).show()
                            // ✅ Update the UI instantly
                            Glide.with(this)
                                .load(downloadUrl)
                                .circleCrop()
                                .into(binding.imageProfile)
                        }
                }
            }
            .addOnFailureListener {
                Snackbar.make(binding.root, "Upload failed: ${it.message}", Snackbar.LENGTH_SHORT).show()
            }
    }


    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val uri = result.data!!.data ?: return@registerForActivityResult
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

            // Show selected image immediately
            binding.imageProfile.setImageBitmap(bitmap)

            // Convert to Base64 string
            val base64String = bitmapToBase64(bitmap)

            // Save Base64 string to Firebase Realtime Database
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                FirebaseDatabase.getInstance().getReference("users")
                    .child(user.uid)
                    .child("profileImageBase64")
                    .setValue(base64String)
                    .addOnSuccessListener {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Profile picture updated!",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Failed to save image: ${it.message}",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
            }
        }
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
        setupRemindersRecyclerView()
        setupDataUsageMonitoring()

        // ✅ Initialize Firebase references
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")

        // ✅ Load user data from Firebase
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid

            database.child(uid).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val username = snapshot.child("username").value?.toString() ?: "Unknown User"
                    val email = snapshot.child("email").value?.toString() ?: currentUser.email ?: "No Email"
                    val role = snapshot.child("role").value?.toString() ?: "Farmer"
                    val profileImageBase64 = snapshot.child("profileImageBase64").value?.toString()
                    val dailyLimit = snapshot.child("dailyLimitMB").value?.toString()

                    // ✅ Set user info
                    binding.textProfileName.text = username
                    binding.textProfileEmail.text = email
                    binding.textProfileRole.text = role
                    if (!dailyLimit.isNullOrEmpty()) {
                        binding.dailyLimitInput.setText(dailyLimit)
                    }
                    // ✅ Decode and display Base64 image if it exists
                    if (!profileImageBase64.isNullOrEmpty()) {
                        try {
                            val decodedBytes = android.util.Base64.decode(profileImageBase64, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            binding.imageProfile.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    binding.textProfileName.text = "User not found"
                    binding.textProfileEmail.text = currentUser.email ?: "No Email"
                    binding.textProfileRole.text = "Unknown Role"
                }
            }.addOnFailureListener {
                binding.textProfileName.text = "Error loading profile"
                binding.textProfileEmail.text = ""
                binding.textProfileRole.text = ""
            }
        } else {
            binding.textProfileName.text = "Guest"
            binding.textProfileEmail.text = "Not logged in"
            binding.textProfileRole.text = ""
        }

        // ✅ Tap image to pick a new one
        binding.imageProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }
    }

    private fun bitmapToBase64(bitmap: android.graphics.Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
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

                // Collection for feeding reminders
                launch {
                    viewModel.feedingReminders.collect { reminders ->
                        updateRemindersList(reminders)
                    }
                }

                // Collection for reminder messages
                launch {
                    viewModel.reminderMessage.collect { message ->
                        message?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearReminderMessage()
                        }
                    }
                }
            }
        }
    }

    private fun setupRemindersRecyclerView() {
        reminderAdapter = FeedingReminderAdapter { reminder ->
            // Show confirmation dialog before deleting
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Reminder")
                .setMessage("Are you sure you want to delete this reminder?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteReminder(reminder)
                    // Cancel the alarm
                    cancelAlarm(reminder.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reminderAdapter
        }
    }

    private fun updateRemindersList(reminders: List<com.crabtrack.app.data.model.FeedingReminder>) {
        reminderAdapter.submitList(reminders)

        // Show/hide empty state
        if (reminders.isEmpty()) {
            binding.emptyRemindersMessage.visibility = View.VISIBLE
            binding.remindersRecyclerView.visibility = View.GONE
        } else {
            binding.emptyRemindersMessage.visibility = View.GONE
            binding.remindersRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun cancelAlarm(reminderId: String) {
        val intent = Intent(requireContext(), FeedingAlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            requireContext(),
            reminderId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
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

    private fun dispenseWithRelay(volumeMl: Double) {
        // Cancel any ongoing dispensing operation
        dispensingJob?.cancel()

        // Calculate duration based on pump flow rate (29ml/s)
        val flowRatePerSecond = 29.0 // ml/s (average of 25-33 ml/s from 1.5-2L/min spec)
        val durationSeconds = (volumeMl / flowRatePerSecond).toInt()

        // Get Firebase reference for relay control
        val relayRef = FirebaseDatabase.getInstance()
            .getReference("crabtrack")
            .child("controls")
            .child("relay1")

        // Disable volume input and update button state
        binding.mlPh.isEnabled = false
        binding.buttonPhDispense.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor("#FF0000") // Red for cancel
        )

        // Start dispensing
        dispensingJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Turn relay ON
                relayRef.setValue(1).await()

                // Countdown timer
                for (secondsRemaining in durationSeconds downTo 1) {
                    binding.buttonPhDispense.text = "Cancel (${secondsRemaining}s remaining)"
                    kotlinx.coroutines.delay(1000)
                }

                // Turn relay OFF
                relayRef.setValue(0).await()

                // Show success message
                binding.buttonPhDispense.text = "Dispense Solution"
                binding.buttonPhDispense.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(com.crabtrack.app.R.color.blu)
                )
                binding.mlPh.isEnabled = true

                Snackbar.make(binding.root, "Dispensed ${volumeMl}ml successfully", Snackbar.LENGTH_SHORT).show()

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Dispensing was cancelled - use NonCancellable to ensure cleanup completes
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    relayRef.setValue(0).await()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        binding.buttonPhDispense.text = "Dispense Solution"
                        binding.buttonPhDispense.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            requireContext().getColor(com.crabtrack.app.R.color.blu)
                        )
                        binding.mlPh.isEnabled = true
                        binding.mlPh.text?.clear() // Clear volume field to force new input
                        Snackbar.make(binding.root, "Dispensing cancelled", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Error occurred - use NonCancellable to ensure cleanup completes
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    relayRef.setValue(0).await()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        binding.buttonPhDispense.text = "Dispense Solution"
                        binding.buttonPhDispense.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            requireContext().getColor(com.crabtrack.app.R.color.blu)
                        )
                        binding.mlPh.isEnabled = true
                        Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                dispensingJob = null
            }
        }
    }

    private fun cancelDispensing() {
        dispensingJob?.cancel()
    }




    private fun setupClickListeners() {
        binding.buttonSave.setOnClickListener {
            viewModel.saveThresholds()
        }

        binding.buttonResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }

        // Section expand/collapse handlers
        binding.waterQualityHeader.setOnClickListener {
            toggleWaterQuality()
        }

        binding.dispenserHeader.setOnClickListener {
            toggleDispenser()
        }

        binding.feedingReminderHeader.setOnClickListener {
            toggleFeedingReminder()
        }

        binding.advancedSettingsHeader.setOnClickListener {
            toggleAdvancedSettings()
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
            // If currently dispensing, cancel it
            if (dispensingJob?.isActive == true) {
                cancelDispensing()
            } else {
                // Start new dispensing operation
                val mlText = binding.mlPh.text.toString()
                if (mlText.isNotBlank()) {
                    val ml = mlText.toDoubleOrNull()
                    if (ml != null && ml > 0) {
                        dispenseWithRelay(ml)
                    } else {
                        Snackbar.make(binding.root, "Invalid volume. Please enter a positive number.", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Snackbar.make(binding.root, "Please enter volume in ml", Snackbar.LENGTH_SHORT).show()
                }
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

        // ✅ When edit icon is clicked → show dialog to edit name or change photo
        // ✅ Handle edit name button
        binding.buttonEditProfile.setOnClickListener {
            val dialogView = layoutInflater.inflate(com.crabtrack.app.R.layout.dialog_edit_profile, null)
            val editName = dialogView.findViewById<EditText>(com.crabtrack.app.R.id.edit_profile_name)
            editName.setText(binding.textProfileName.text)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Name")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val newName = editName.text.toString().trim()
                    val user = FirebaseAuth.getInstance().currentUser ?: return@setPositiveButton

                    if (newName.isNotEmpty()) {
                        // ✅ Update Firebase
                        FirebaseDatabase.getInstance().getReference("users")
                            .child(user.uid)
                            .child("username")
                            .setValue(newName)
                            .addOnSuccessListener {
                                // ✅ Update UI instantly
                                binding.textProfileName.text = newName
                                Snackbar.make(binding.root, "Name updated successfully!", Snackbar.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Snackbar.make(binding.root, "Failed to update name: ${it.message}", Snackbar.LENGTH_SHORT).show()
                            }
                    } else {
                        Snackbar.make(binding.root, "Name cannot be empty.", Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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

            // Get selected recurrence type
            val recurrenceType = when (binding.recurrenceRadioGroup.checkedRadioButtonId) {
                binding.radioDaily.id -> RecurrenceType.DAILY
                binding.radioWeekly.id -> RecurrenceType.WEEKLY
                else -> RecurrenceType.NONE
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
                "recurrence" to recurrenceType.name,
                "status" to "scheduled",
                "createdAt" to System.currentTimeMillis()
            )

            val remindersRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.uid)
                .child("feeding_reminders")
                .push()

            val reminderId = remindersRef.key ?: return@setOnClickListener

            remindersRef.setValue(reminderData)
                .addOnSuccessListener {
                    // ✅ Schedule local alarm notification
                    val intent = Intent(requireContext(), FeedingAlarmReceiver::class.java).apply {
                        putExtra("reminder_id", reminderId)
                        putExtra("recurrence_type", recurrenceType.name)
                        putExtra("timestamp", triggerTime)
                    }

                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        requireContext(),
                        reminderId.hashCode(),
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
                                val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${requireContext().packageName}")
                                }
                                startActivity(settingsIntent)
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

                        // Clear input fields
                        binding.feedingDateInput.text?.clear()
                        binding.feedingTimeInput.text?.clear()
                        binding.recurrenceRadioGroup.check(binding.radioOneTime.id)

                        val recurrenceText = when (recurrenceType) {
                            RecurrenceType.DAILY -> " (Daily)"
                            RecurrenceType.WEEKLY -> " (Weekly)"
                            RecurrenceType.NONE -> ""
                        }
                        Snackbar.make(binding.root, "Reminder set for $date at $time$recurrenceText", Snackbar.LENGTH_SHORT).show()

                    } catch (se: SecurityException) {
                        Snackbar.make(
                            binding.root,
                            "Exact alarm permission not granted. Enable it in Settings.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
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

    private fun setupDataUsageMonitoring() {
        // Observe network type changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    networkTypeDetector.observeNetworkType().collect { networkType ->
                        updateNetworkTypeUI(networkType)
                    }
                }

                launch {
                    dataUsageTracker.getUsageStats().collect { stats ->
                        updateDataUsageUI(stats)
                    }
                }

                launch {
                    dataUsageTracker.isDataSaverEnabled().collect { enabled ->
                        binding.switchDataSaver.isChecked = enabled
                    }
                }
            }
        }

        // Data Saver toggle listener
        binding.switchDataSaver.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                dataUsageTracker.setDataSaverMode(isChecked)
                val message = if (isChecked) {
                    "Data Saver Mode enabled - Camera quality reduced, MQTT intervals increased"
                } else {
                    "Data Saver Mode disabled"
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }

        // Set Daily Limit button listener
        binding.buttonSetLimit.setOnClickListener {
            val limitText = binding.dailyLimitInput.text.toString().trim()
            val user = FirebaseAuth.getInstance().currentUser

            // 🧩 1. Check if field is empty
            if (limitText.isEmpty()) {
                binding.dailyLimitInput.error = "This field cannot be empty"
                Snackbar.make(binding.root, "Please enter your daily data limit.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🧩 2. Convert to number safely
            val limitMB = limitText.toLongOrNull()
            if (limitMB == null) {
                binding.dailyLimitInput.error = "Please enter a valid number"
                Snackbar.make(binding.root, "Invalid number format. Please enter digits only.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🧩 3. Validate range (1–10,000 MB for sanity)
            if (limitMB <= 0 || limitMB > 10000) {
                binding.dailyLimitInput.error = "Enter a value between 1 and 10,000 MB"
                Snackbar.make(binding.root, "Please enter a limit between 1 MB and 10,000 MB.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🧩 4. Clear error if all is good
            binding.dailyLimitInput.error = null

            // 🧩 5. Save to Firebase Database
            if (user != null) {
                val ref = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
                ref.child("dailyLimitMB").setValue(limitMB)
                    .addOnSuccessListener {
                        // ✅ Also save locally via DataStore
                        viewLifecycleOwner.lifecycleScope.launch {
                            dataUsageTracker.setDailyLimit(limitMB)
                        }

                        // ✅ Keep the input text (don’t clear)
                        Snackbar.make(binding.root, "Daily limit saved: $limitMB MB", Snackbar.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Snackbar.make(binding.root, "Failed to save limit: ${it.message}", Snackbar.LENGTH_SHORT).show()
                    }
            } else {
                Snackbar.make(binding.root, "User not logged in. Cannot save limit.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateNetworkTypeUI(networkType: NetworkType) {
        val networkText = when (networkType) {
            NetworkType.WIFI -> "WiFi"
            NetworkType.MOBILE_DATA -> "Mobile Data"
            NetworkType.ROAMING -> "Roaming"
            NetworkType.ETHERNET -> "Ethernet"
            NetworkType.NONE -> "No Connection"
        }
        binding.textNetworkType.text = networkText

        // Change color based on network type
        val color = when (networkType) {
            NetworkType.WIFI, NetworkType.ETHERNET -> android.graphics.Color.parseColor("#4CAF50") // Green
            NetworkType.MOBILE_DATA -> android.graphics.Color.parseColor("#FF9800") // Orange
            NetworkType.ROAMING -> android.graphics.Color.parseColor("#F44336") // Red
            NetworkType.NONE -> android.graphics.Color.parseColor("#9E9E9E") // Gray
        }
        binding.textNetworkType.setTextColor(color)
    }

    private fun updateDataUsageUI(stats: com.crabtrack.app.data.util.DataUsageStats) {
        binding.textTodayUsage.text = "%.2f MB".format(stats.todayMB)
        binding.textMonthUsage.text = "%.2f MB".format(stats.monthMB)
        binding.textSessionUsage.text = "%.2f MB".format(stats.sessionMB)

        // Change color based on usage percentage
        val color = when {
            stats.isOverDailyLimit() -> android.graphics.Color.parseColor("#F44336") // Red
            stats.isNearDailyLimit() -> android.graphics.Color.parseColor("#FF9800") // Orange
            else -> android.graphics.Color.parseColor("#4CAF50") // Green
        }
        binding.textTodayUsage.setTextColor(color)
    }

    /**
     * Toggle the visibility of the Water Quality section
     */
    private fun toggleWaterQuality() {
        val content = binding.waterQualityContent
        val icon = binding.waterQualityExpandIcon

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.rotation = 0f
        } else {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        }
    }

    /**
     * Toggle the visibility of the Dispenser section
     */
    private fun toggleDispenser() {
        val content = binding.dispenserContent
        val icon = binding.dispenserExpandIcon

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.rotation = 0f
        } else {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        }
    }

    /**
     * Toggle the visibility of the Feeding Reminder section
     */
    private fun toggleFeedingReminder() {
        val content = binding.feedingReminderContent
        val icon = binding.feedingReminderExpandIcon

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.rotation = 0f
        } else {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        }
    }

    /**
     * Toggle the visibility of the Advanced Settings section
     */
    private fun toggleAdvancedSettings() {
        val content = binding.advancedSettingsContent
        val icon = binding.advancedSettingsExpandIcon

        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.rotation = 0f
        } else {
            content.visibility = View.VISIBLE
            icon.rotation = 180f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any ongoing dispensing to ensure relay is turned off
        cancelDispensing()
        _binding = null
    }


}