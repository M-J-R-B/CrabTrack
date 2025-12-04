
package com.crabtrack.app.ui.settings

import android.Manifest
import android.app.Activity
import com.crabtrack.app.data.model.FeedingReminder
import com.crabtrack.app.ui.settings.adapter.SettingsFeedingReminderAdapter
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.data.model.RecurrenceType
import com.crabtrack.app.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.firebase.storage.FirebaseStorage
import com.bumptech.glide.Glide
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.crabtrack.app.R
import com.crabtrack.app.presentation.auth.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.crabtrack.app.ui.settings.adapter.FeedingReminderAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar

private var isEditingProfile = false

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private val viewModel: SettingsViewModel by viewModels()
    private var dispensingJob: Job? = null
    private lateinit var reminderAdapter: SettingsFeedingReminderAdapter

    private lateinit var storage: FirebaseStorage
    private var imageUri: Uri? = null

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
                showProfileSuccessDialog(
                    title = "Upload Failed",
                    message = "Your profile picture has not been uploaded!."
                )
            }
    }


    private fun showThresholdSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        titleView.text = "Success!"
        messageView.text = "Your inputs has been saved!"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
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

    private fun showDispenseSuccessDialog(
        title: String = "Success!",
        message: String = "Solution dispensed smoothly."
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_signup, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        titleView.text = title
        messageView.text = message

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showResetDefaultsSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_success_profile, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonOk = dialogView.findViewById<MaterialButton>(R.id.button_ok)

        titleView.text = "Defaults Restored"
        messageView.text = "All threshold values have been reset to CrabTrack’s recommended safe ranges."

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
            .setView(dialogView)
            .create()

        buttonOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }


    private fun showDeleteReminderConfirm(
        reminder: FeedingReminder,
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        titleView.text = "Delete reminder?"
        messageView.text = "Are you sure you want to delete this reminder?\n\n${reminder.date} at ${reminder.time}"

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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data!!.data ?: return@registerForActivityResult
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Show selected image immediately
            binding.imageProfile.setImageBitmap(bitmap)
            binding.imageProfileEdit.setImageBitmap(bitmap)

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
                        showProfileSuccessDialog(
                            title = "Profile updated",
                            message = "Your profile picture has been updated."
                        )
                    }
                    .addOnFailureListener {
                        showProfileSuccessDialog(
                            title = "Failed to save Image",
                            message = "Your profile picture has not been updated."
                        )
                    }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "SettingsFragment onViewCreated")

        binding.profileDetailsContent.visibility = View.GONE

        binding.profileDetailsContent.visibility = View.GONE

        // Lock edit UI by default
        binding.editProfileNameInput.isEnabled = false
        binding.buttonProfileSave.isEnabled = false
        binding.buttonProfileSave.alpha = 0.5f

        binding.imageProfileEdit.isClickable = false
        binding.imageProfileEdit.isFocusable = false

        // ✅ 1. Create notification channel (required once)
        createNotificationChannel()

        // ✅ 2. Request notification permission (Android 13+)
        ensureNotificationPermission()

        // ✅ 3. Initialize all other features
        setupTextWatchers()
        setupObservers()
        setupSwipeRefresh()
        setupClickListeners()
        setupInputValidation()
        setupRemindersRecyclerView()

        // ✅ Initialize Firebase references
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")

        // ✅ Load user data from Firebase
        // ✅ Load user data from Firebase
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid

            database.child(uid).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val username = snapshot.child("username").value?.toString() ?: "Unknown User"
                    val email = snapshot.child("email").value?.toString() ?: currentUser.email ?: "No Email"
                    val role = snapshot.child("role").value?.toString() ?: "Farmer"

                    val profileImageBase64 = snapshot.child("profileImageBase64").getValue(String::class.java)
                    val profileImageUrl = snapshot.child("profileImage").getValue(String::class.java)

                    // ✅ Set user info
                    binding.textProfileName.text = username
                    binding.textProfileEmail.text = email
                    binding.textProfileRole.text = role

                    // ✅ 1) Try Base64 first
                    if (!profileImageBase64.isNullOrEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            binding.imageProfile.setImageBitmap(bitmap)
                            binding.imageProfileEdit.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    // ✅ 2) If no Base64, try URL from Firebase Storage
                    else if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(binding.imageProfile)

                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(binding.imageProfileEdit)
                    }
                    // ✅ 3) Else → leave default icon
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
    }

    private fun enterProfileEditMode() {
        isEditingProfile = true

        // Enable name field + save button
        binding.editProfileNameInput.isEnabled = true
        binding.buttonProfileSave.isEnabled = true
        binding.buttonProfileSave.alpha = 1f

        // Only now make the edit image clickable
        binding.imageProfileEdit.isClickable = true
        binding.imageProfileEdit.isFocusable = true

        binding.imageProfileEdit.setOnClickListener {
            // Only act if actually in edit mode
            if (!isEditingProfile) return@setOnClickListener

            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }

        // Pre-fill name in edit field from header name
        binding.editProfileNameInput.setText(binding.textProfileName.text)
    }

    private fun showReminderSuccessDialog(
        title: String = "Reminder",
        message: String
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

    private fun exitProfileEditMode() {
        isEditingProfile = false
        binding.editProfileNameInput.isEnabled = false
        binding.buttonProfileSave.isEnabled = false
        binding.buttonProfileSave.alpha = 0.5f
        binding.imageProfileEdit.isClickable = false
        binding.imageProfileEdit.isFocusable = false
    }


    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    // ------------------------------------------------------------
// Notification setup helpers
// ------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "feeding_channel", // must match the one used in FeedingAlarmReceiver
                "Feeding Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when it's time to feed your crabs"
            }

            val manager =
                requireContext().getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = requireContext().checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun toggleProfileSection() {
        val content = binding.profileDetailsContent
        content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                        Log.d(TAG, "UI State updated: isLoading=${state.isLoading}, thresholds=${state.thresholds?.pHMin}")
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

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshThresholdsFromFirebase()
        }
    }

    private fun setupRemindersRecyclerView() {
        reminderAdapter = SettingsFeedingReminderAdapter(
            onDeleteClick = { reminder ->

                // 🔹 Inflate your custom delete dialog layout
                val dialogView = layoutInflater.inflate(
                    R.layout.dialog_delete_reminder_confirm,  // <-- use your XML here
                    null
                )

                val titleView = dialogView.findViewById<TextView>(R.id.text_title)
                val messageView = dialogView.findViewById<TextView>(R.id.text_message)
                val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
                val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

                // Optional: customize text if you want
                titleView.text = "Delete reminder?"
                messageView.text = "Are you sure you want to delete this reminder?"

                val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CrabTrack_AlertDialog)
                    .setView(dialogView)
                    .create()

                buttonNo.setOnClickListener {
                    dialog.dismiss()
                }

                buttonYes.setOnClickListener {
                    // ✅ same behavior as before
                    viewModel.deleteReminder(reminder)
                    cancelAlarm(reminder.id)
                    dialog.dismiss()
                }

                dialog.show()
            },
            readOnly = false
        )

        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reminderAdapter
        }
    }



    private fun updateRemindersList(reminders: List<FeedingReminder>) {
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
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    private fun updateUI(state: SettingsUiState) {
        // Update swipe refresh state
        binding.swipeRefreshLayout.isRefreshing = state.isRefreshing

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
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "Save"
            }
            SaveState.Saving -> {
                binding.buttonSave.isEnabled = false
                binding.buttonSave.text = "Saving..."
            }
            SaveState.Success -> {
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "Save"

                showProfileSuccessDialog(
                    title = "Water quality saved",
                    message = "Your water quality thresholds have been saved successfully."
                )

                viewModel.resetSaveState()
            }
            SaveState.Error -> {
                binding.buttonSave.isEnabled = true
                binding.buttonSave.text = "Save"

                // 👇 Check if this is a validation error (invalid user input)
                val uiState = viewModel.uiState.value
                val isValidationError =
                    uiState.validationErrors.isNotEmpty() ||
                            uiState.errorMessage?.contains("Invalid input", ignoreCase = true) == true ||
                            uiState.errorMessage?.contains("Please fill all fields", ignoreCase = true) == true

                if (isValidationError) {
                    showProfileSuccessDialog(
                        title = "Invalid Input!",
                        message = """
                        Please check your values:
                        
                        • All fields must be filled
                        • Use numbers only (no letters or symbols)
                        • Minimum must be **less than** maximum
                        
                        Example:
                        • pH Min: 7.0, pH Max: 8.5
                        • Temp Min: 22, Temp Max: 28
                    """.trimIndent()
                    )
                } else if (uiState.errorMessage != null) {
                    // other errors like Firebase failure
                    showProfileSuccessDialog(
                        title = "Save failed",
                        message = uiState.errorMessage ?: "Something went wrong while saving your thresholds."
                    )
                }

                viewModel.resetSaveState()
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
        binding.buttonPhDispense.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor("#FF0000") // Red for cancel
        )

        // Start dispensing
        dispensingJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Turn relay ON
                relayRef.setValue(1).await()

                // Countdown timer
                for (secondsRemaining in durationSeconds downTo 1) {
                    binding.buttonPhDispense.text = "Cancel (${secondsRemaining}s remaining)"
                    delay(1000)
                }

                // Turn relay OFF
                relayRef.setValue(0).await()

                // Show success message
                binding.buttonPhDispense.text = "Dispense Solution"
                binding.buttonPhDispense.backgroundTintList = ColorStateList.valueOf(
                    requireContext().getColor(R.color.blu)
                )
                binding.mlPh.isEnabled = true

                showDispenseSuccessDialog(
                    title = "Success!",
                    message = "Dispensed ${volumeMl}ml smoothly."
                )

            } catch (e: CancellationException) {
                // Dispensing was cancelled - use NonCancellable to ensure cleanup completes
                withContext(NonCancellable) {
                    relayRef.setValue(0).await()
                    withContext(Dispatchers.Main) {
                        binding.buttonPhDispense.text = "Dispense Solution"
                        binding.buttonPhDispense.backgroundTintList = ColorStateList.valueOf(
                            requireContext().getColor(R.color.blu)
                        )
                        binding.mlPh.isEnabled = true
                        binding.mlPh.text?.clear() // Clear volume field to force new input
                        showProfileSuccessDialog(
                            title = "Dispense Cancelled",
                            message = "The solution will not be Dispensed"
                        )
                    }
                }
            } catch (e: Exception) {
                // Error occurred - use NonCancellable to ensure cleanup completes
                withContext(NonCancellable) {
                    relayRef.setValue(0).await()
                    withContext(Dispatchers.Main) {
                        binding.buttonPhDispense.text = "Dispense Solution"
                        binding.buttonPhDispense.backgroundTintList = ColorStateList.valueOf(
                            requireContext().getColor(R.color.blu)
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

        // Header tap: just expand/collapse, no edit mode
        binding.profileHeaderCard.setOnClickListener {
            toggleProfileSection()
        }

// Edit icon: expand + enable edit mode
        // Header tap: just expand/collapse, no edit mode
        binding.profileHeaderCard.setOnClickListener {
            toggleProfileSection()
        }

// Edit icon: toggle the inline edit card
        binding.buttonEditProfile.setOnClickListener {
            val content = binding.profileDetailsContent
            if (content.visibility == View.VISIBLE) {
                content.visibility = View.GONE
                exitProfileEditMode()
            } else {
                content.visibility = View.VISIBLE
                enterProfileEditMode()
            }
        }


        binding.buttonProfileSave.setOnClickListener {
            val newName = binding.editProfileNameInput.text.toString().trim()
            val user = FirebaseAuth.getInstance().currentUser ?: return@setOnClickListener

            if (newName.isEmpty()) {
                showProfileSuccessDialog(
                    title = "Invalid Input!",
                    message = "Name cannot be Empty"
                )
                return@setOnClickListener
            }

            FirebaseDatabase.getInstance().getReference("users")
                .child(user.uid)
                .child("username")
                .setValue(newName)
                .addOnSuccessListener {
                    // Update header UI
                    binding.textProfileName.text = newName

                    // Optional: lock editing again
                    isEditingProfile = false
                    binding.editProfileNameInput.isEnabled = false
                    binding.buttonProfileSave.isEnabled = false
                    binding.buttonProfileSave.alpha = 0.5f
                    binding.imageProfileEdit.isClickable = false
                    binding.imageProfileEdit.isFocusable = false
                    binding.profileDetailsContent.visibility = View.GONE   // 👈 collapse after save

                    // You already have a success dialog for profile
                    showProfileSuccessDialog(
                        title = "Profile updated",
                        message = "Your profile changes have been saved successfully."
                    )
                }
                .addOnFailureListener {
                    showProfileSuccessDialog(
                        title = "Failed to Update Picture",
                        message = "Your profile picture has not been updated."
                    )                }
        }

        binding.buttonSave.setOnClickListener {
            viewModel.saveThresholds()
        }

        binding.buttonResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
            showResetDefaultsSuccessDialog()
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


        binding.buttonLogout.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirm, null)

            val dialog = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.CrabTrack_LogoutDialog   // or remove this line if you haven't created the style yet
            )
                .setView(dialogView)
                .create()

            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)

            val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)
            val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)

            buttonYes.setOnClickListener {
                val authViewModel: AuthViewModel by activityViewModels()
                authViewModel.logout()
                dialog.dismiss()
            }

            buttonNo.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
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
                        showProfileSuccessDialog(
                            title = "Invalid Volume",
                            message = "Please enter a positive number."
                        )
                    }
                } else {
                    showProfileSuccessDialog(
                        title = "Invalid Input!",
                        message = "Please Enter Volume in ml"
                    )
                }
            }
        }

        // Feeding Reminder - Date & Time pickers
        binding.feedingDateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selectedDate = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                    binding.feedingDateInput.setText(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        // ✅ When edit icon is clicked → show dialog to edit name or change photo
        // ✅ Handle edit name button



        binding.feedingTimeInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                    binding.feedingTimeInput.setText(selectedTime)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )
            timePicker.show()
        }

        binding.buttonSetFeedingReminder.setOnClickListener {
            val date = binding.feedingDateInput.text.toString()
            val time = binding.feedingTimeInput.text.toString()

            if (date.isBlank() || time.isBlank()) {
                showProfileSuccessDialog(
                    title = "Invalid Input!",
                    message = "Please select both Date and Time"
                )
                return@setOnClickListener
            }

            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                showProfileSuccessDialog(
                    title = "Invalid Login",
                    message = "User not Logged In"
                )
                return@setOnClickListener
            }

            // ✅ Recurrence (One-time / Daily / Weekly)
            val recurrenceType = when (binding.recurrenceRadioGroup.checkedRadioButtonId) {
                binding.radioDaily.id -> RecurrenceType.DAILY
                binding.radioWeekly.id -> RecurrenceType.WEEKLY
                else -> RecurrenceType.NONE
            }

            // ✅ Action type (Feed / Clean)
            val actionType = when (binding.actionRadioGroup.checkedRadioButtonId) {
                binding.radioFeed.id -> "FEED"
                binding.radioClean.id -> "CLEAN"
                else -> "FEED"
            }

            // ✅ Parse date + time
            val dateParts = date.split("-")   // yyyy-MM-dd
            val timeParts = time.split(":")   // HH:mm

            if (dateParts.size != 3 || timeParts.size != 2) {
                showProfileSuccessDialog(
                    title = "Invalid Input!",
                    message = "Invalid Date or Time Input"
                )
                return@setOnClickListener
            }

            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1   // 0-based
            val day = dateParts[2].toInt()
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
            }

            val triggerTime = calendar.timeInMillis
            if (triggerTime <= System.currentTimeMillis()) {
                showProfileSuccessDialog(
                    title = "Invalid Input!",
                    message = "Please select a Future Time"
                )
                return@setOnClickListener
            }

            // ✅ Show your custom confirm dialog BEFORE saving
            showSaveScheduleConfirm(date, time, recurrenceType, actionType) {

                val reminderData = mapOf(
                    "date" to date,
                    "time" to time,
                    "timestamp" to triggerTime,
                    "recurrence" to recurrenceType.name,
                    "actionType" to actionType,
                    "status" to "scheduled",
                    "createdAt" to System.currentTimeMillis()
                )

                val remindersRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.uid)
                    .child("feeding_reminders")
                    .push()

                val reminderId = remindersRef.key ?: return@showSaveScheduleConfirm

                remindersRef.setValue(reminderData)
                    .addOnSuccessListener {
                        val intent = Intent(requireContext(), FeedingAlarmReceiver::class.java).apply {
                            putExtra("reminder_id", reminderId)
                            putExtra("recurrence_type", recurrenceType.name)
                            putExtra("timestamp", triggerTime)
                            putExtra("action_type", actionType)
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            requireContext(),
                            reminderId.hashCode(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                                    val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${requireContext().packageName}")
                                    }
                                    startActivity(settingsIntent)
                                    showProfileSuccessDialog(
                                        title = "Invalid Input!",
                                        message = "Please allow exact alarm for CrabTrack"
                                    )
                                    return@addOnSuccessListener
                                }
                            } else {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                                )
                            }

                            // Clear fields + reset radios
                            binding.feedingDateInput.text?.clear()
                            binding.feedingTimeInput.text?.clear()
                            binding.recurrenceRadioGroup.check(binding.radioOneTime.id)
                            binding.actionRadioGroup.check(binding.radioFeed.id)


                            val recurrenceText = when (recurrenceType) {
                                RecurrenceType.DAILY -> "Daily"
                                RecurrenceType.WEEKLY -> "Weekly"
                                RecurrenceType.NONE -> "One-time"
                            }
                            val actionLabelSnack = if (actionType == "CLEAN") "cleaning" else "feeding"

                            showReminderSuccessDialog(
                                title = if (actionType == "CLEAN") "Cleaning reminder saved"
                                else "Feeding reminder saved",
                                message = "Reminder set for $date at $time $recurrenceText."
                            )

                            viewModel.loadFeedingReminders()

                            val newReminder = FeedingReminder(
                                id = reminderId,
                                date = date,
                                time = time,
                                timestamp = triggerTime,
                                recurrence = recurrenceType,
                                status = "scheduled",
                                createdAt = System.currentTimeMillis(),
                                actionType = actionType
                            )

                            val updatedList = reminderAdapter.currentList.toMutableList().apply {
                                add(0, newReminder)
                            }
                            reminderAdapter.submitList(updatedList)

                            binding.emptyRemindersMessage.visibility = View.GONE
                            binding.remindersRecyclerView.visibility = View.VISIBLE
                            // 🔹🔹 REAL-TIME UPDATE ENDS HERE 🔹🔹

                        } catch (se: SecurityException) {
                            showProfileSuccessDialog(
                                title = "Invalid Input!",
                                message = "Exact alarm permission not granted, Enable it in Settings"
                            )
                        }
                    }
                    .addOnFailureListener {
                        showProfileSuccessDialog(
                            title = "Invalid ",
                            message = "Failed to Set Reminder"
                        )
                    }
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


    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any ongoing dispensing to ensure relay is turned off
        cancelDispensing()
        _binding = null
    }


    private fun showSaveScheduleConfirm(
        date: String,
        time: String,
        recurrenceType: RecurrenceType,
        actionType: String,
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder_confirm, null)

        val titleView = dialogView.findViewById<TextView>(R.id.text_title)
        val messageView = dialogView.findViewById<TextView>(R.id.text_message)
        val buttonNo = dialogView.findViewById<MaterialButton>(R.id.button_confirm_no)
        val buttonYes = dialogView.findViewById<MaterialButton>(R.id.button_confirm_yes)

        val recurrenceLabel = when (recurrenceType) {
            RecurrenceType.DAILY -> "Daily"
            RecurrenceType.WEEKLY -> "Weekly"
            RecurrenceType.NONE -> "One-time"
        }

        val actionLabel = when (actionType) {
            "CLEAN" -> "Cleaning"
            "FEED" -> "Feeding"
            else -> actionType
        }

        titleView.text = "Save?"
        messageView.text = "Are you sure you wanna save this $actionLabel schedule?\n\n$date at $time • $recurrenceLabel"

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


}