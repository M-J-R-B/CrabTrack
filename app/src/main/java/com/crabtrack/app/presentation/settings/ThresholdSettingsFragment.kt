package com.crabtrack.app.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.databinding.FragmentThresholdSettingsBinding
import com.crabtrack.app.presentation.settings.adapter.ThresholdAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ThresholdSettingsFragment : Fragment() {

    private var _binding: FragmentThresholdSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ThresholdSettingsViewModel by viewModels()
    private lateinit var thresholdAdapter: ThresholdAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThresholdSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupButtons()
        observeUiState()
    }

    private fun setupRecyclerView() {
        thresholdAdapter = ThresholdAdapter(
            onEditThreshold = { sensorType ->
                viewModel.startEditingThreshold(sensorType)
            }
        )
        
        binding.recyclerViewThresholds.apply {
            adapter = thresholdAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupButtons() {
        binding.buttonResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    updateUI(uiState)
                }
            }
        }
    }

    private fun updateUI(uiState: ThresholdSettingsUiState) {
        // Show/hide loading indicator
        binding.progressBar.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE
        
        // Update threshold list
        if (!uiState.isLoading) {
            thresholdAdapter.submitList(uiState.thresholds.values.toList())
        }

        // Enable/disable reset button
        binding.buttonResetDefaults.isEnabled = !uiState.isSaving

        // Show success message
        uiState.successMessage?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }

        // Show error message
        uiState.errorMessage?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction("Dismiss") { viewModel.clearMessages() }
                .show()
        }

        // Handle threshold editing
        uiState.editingThreshold?.let { threshold ->
            showThresholdEditDialog(threshold)
        }
    }

    private fun showThresholdEditDialog(threshold: com.crabtrack.app.data.model.Threshold) {
        // This would typically show a dialog fragment for editing thresholds
        // For now, we'll use a simplified approach with Snackbar
        Snackbar.make(
            binding.root,
            "Editing ${threshold.sensorType.displayName} thresholds",
            Snackbar.LENGTH_SHORT
        ).show()
        
        // Cancel editing for this demo
        viewModel.cancelEditing()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}