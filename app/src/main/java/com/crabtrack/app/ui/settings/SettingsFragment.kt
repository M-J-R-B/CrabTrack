package com.crabtrack.app.ui.settings

import android.os.Bundle
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
import com.crabtrack.app.data.model.Thresholds
import com.crabtrack.app.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
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
        
        setupTextWatchers()
        setupObservers()
        setupClickListeners()
        setupInputValidation()
    }

    private fun setupTextWatchers() {
        // Bind EditTexts to ViewModel StateFlows with real-time validation
        binding.phMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePhMin(text?.toString() ?: "")
        }
        binding.phMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePhMax(text?.toString() ?: "")
        }
        binding.doMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDoMin(text?.toString() ?: "")
        }
        binding.salinityMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSalinityMin(text?.toString() ?: "")
        }
        binding.salinityMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateSalinityMax(text?.toString() ?: "")
        }
        binding.ammoniaMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateAmmoniaMax(text?.toString() ?: "")
        }
        binding.tempMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTempMin(text?.toString() ?: "")
        }
        binding.tempMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateTempMax(text?.toString() ?: "")
        }
        binding.levelMinInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateLevelMin(text?.toString() ?: "")
        }
        binding.levelMaxInput.doOnTextChanged { text, _, _, _ ->
            viewModel.updateLevelMax(text?.toString() ?: "")
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
            doMinInput.setText(thresholds.doMin.toString())
            salinityMinInput.setText(thresholds.salinityMin.toString())
            salinityMaxInput.setText(thresholds.salinityMax.toString())
            ammoniaMaxInput.setText(thresholds.ammoniaMax.toString())
            tempMinInput.setText(thresholds.tempMin.toString())
            tempMaxInput.setText(thresholds.tempMax.toString())
            levelMinInput.setText(thresholds.levelMin.toString())
            levelMaxInput.setText(thresholds.levelMax.toString())
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        with(binding) {
            phMinInput.isEnabled = enabled
            phMaxInput.isEnabled = enabled
            doMinInput.isEnabled = enabled
            salinityMinInput.isEnabled = enabled
            salinityMaxInput.isEnabled = enabled
            ammoniaMaxInput.isEnabled = enabled
            tempMinInput.isEnabled = enabled
            tempMaxInput.isEnabled = enabled
            levelMinInput.isEnabled = enabled
            levelMaxInput.isEnabled = enabled
            buttonSave.isEnabled = enabled
            buttonResetDefaults.isEnabled = enabled
        }
    }

    private fun showValidationErrors(errors: Map<String, String>) {
        with(binding) {
            // Clear all errors first
            phMinInputLayout.error = null
            phMaxInputLayout.error = null
            doMinInputLayout.error = null
            salinityMinInputLayout.error = null
            salinityMaxInputLayout.error = null
            ammoniaMaxInputLayout.error = null
            tempMinInputLayout.error = null
            tempMaxInputLayout.error = null
            levelMinInputLayout.error = null
            levelMaxInputLayout.error = null
            
            // Show validation errors
            errors["ph"]?.let { 
                phMinInputLayout.error = it
                phMaxInputLayout.error = it
            }
            errors["do"]?.let { doMinInputLayout.error = it }
            errors["salinity"]?.let { 
                salinityMinInputLayout.error = it
                salinityMaxInputLayout.error = it
            }
            errors["ammonia"]?.let { ammoniaMaxInputLayout.error = it }
            errors["temperature"]?.let { 
                tempMinInputLayout.error = it
                tempMaxInputLayout.error = it
            }
            errors["level"]?.let { 
                levelMinInputLayout.error = it
                levelMaxInputLayout.error = it
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
                "doMin" -> setEditTextError(binding.doMinInput, errorMessage)
                "salinityMin" -> setEditTextError(binding.salinityMinInput, errorMessage)
                "salinityMax" -> setEditTextError(binding.salinityMaxInput, errorMessage)
                "ammoniaMax" -> setEditTextError(binding.ammoniaMaxInput, errorMessage)
                "tempMin" -> setEditTextError(binding.tempMinInput, errorMessage)
                "tempMax" -> setEditTextError(binding.tempMaxInput, errorMessage)
                "levelMin" -> setEditTextError(binding.levelMinInput, errorMessage)
                "levelMax" -> setEditTextError(binding.levelMaxInput, errorMessage)
            }
        }
    }
    
    private fun clearEditTextErrors() {
        listOf(
            binding.phMinInput, binding.phMaxInput, binding.doMinInput,
            binding.salinityMinInput, binding.salinityMaxInput, binding.ammoniaMaxInput,
            binding.tempMinInput, binding.tempMaxInput, binding.levelMinInput, binding.levelMaxInput
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

    private fun setupClickListeners() {
        binding.buttonSave.setOnClickListener {
            viewModel.saveThresholds()
        }
        
        binding.buttonResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }
    }

    private fun setupInputValidation() {
        with(binding) {
            listOf(
                phMinInput, phMaxInput, doMinInput, salinityMinInput, salinityMaxInput,
                ammoniaMaxInput, tempMinInput, tempMaxInput, levelMinInput, levelMaxInput
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