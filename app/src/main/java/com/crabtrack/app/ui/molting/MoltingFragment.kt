package com.crabtrack.app.ui.molting

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
import com.crabtrack.app.databinding.FragmentMoltingBinding
import com.crabtrack.app.ui.molting.adapter.MoltEventAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


/**
 * Fragment displaying molt monitoring information including current state,
 * risk level, care window countdown, and recent events timeline.
 */
@AndroidEntryPoint
class MoltingFragment : Fragment() {
    
    private var _binding: FragmentMoltingBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MoltingViewModel by viewModels()
    private lateinit var eventAdapter: MoltEventAdapter
    
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
        setupClickListeners()
        observeViewModel()
        observeLifecycleEvents()
    }
    
    private fun setupClickListeners() {
        binding.stateChip.setOnClickListener {
            // Show more details about current state
            viewModel.showStateDetails()
        }
        
        binding.riskChip.setOnClickListener {
            // Show risk mitigation actions
            viewModel.showRiskActions()
        }
    }
    
    private fun observeLifecycleEvents() {
        // Auto-refresh data when fragment becomes visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refresh()
            }
        }
        
        // Handle background/foreground transitions
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Fragment is visible, start real-time updates
                viewModel.startRealTimeUpdates()
            }
        }
    }
    
    private fun setupRecyclerView() {
        eventAdapter = MoltEventAdapter(viewModel)
        
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        updateStateChip(state)
                    }
                }
                
                launch {
                    viewModel.riskLevel.collect { riskLevel ->
                        updateRiskChip(riskLevel)
                    }
                }
                
                launch {
                    viewModel.careWindowFormatted.collect { formattedTime ->
                        updateCareWindow(formattedTime)
                    }
                }
                
                launch {
                    viewModel.recentEvents.collect { events ->
                        updateEvents(events)
                    }
                }
                
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.loadingIndicator.isVisible = isLoading
                    }
                }
                
                launch {
                    viewModel.errorMessage.collect { error ->
                        error?.let {
                            // Only show error if fragment is in foreground to avoid memory leaks
                            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                showError(it)
                                viewModel.clearError()
                            }
                        }
                    }
                }
                
                // Additional collections for UI events
                launch {
                    viewModel.uiEvents.collect { event ->
                        event?.let { handleUiEvent(it) }
                    }
                }
                
                // Collection for critical alerts
                launch {
                    viewModel.criticalAlerts.collect { alert ->
                        handleCriticalAlert(alert)
                    }
                }
            }
        }
    }
    
    private fun updateStateChip(state: com.crabtrack.app.data.model.MoltState) {
        binding.stateChip.apply {
            text = viewModel.getMoltStateDisplayName(state)
            setChipBackgroundColorResource(viewModel.getMoltStateColor(state))
        }
    }
    
    private fun updateRiskChip(riskLevel: com.crabtrack.app.data.model.AlertSeverity) {
        binding.riskChip.apply {
            text = viewModel.getRiskLevelDisplayName(riskLevel)
            setChipBackgroundColorResource(viewModel.getRiskLevelColor(riskLevel))
        }
    }
    
    private fun updateCareWindow(formattedTime: String?) {
        binding.careWindowText.text = formattedTime ?: getString(R.string.no_care_window)
    }
    
    private fun updateEvents(events: List<com.crabtrack.app.data.model.MoltEvent>) {
        eventAdapter.submitList(events)
        
        val hasEvents = events.isNotEmpty()
        binding.eventsRecyclerView.isVisible = hasEvents
        binding.emptyStateText.isVisible = !hasEvents
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                viewModel.refresh()
            }
            .show()
    }
    
    private fun handleUiEvent(event: MoltingUiEvent) {
        when (event) {
            is MoltingUiEvent.ShowStateDetails -> {
                showStateDetailsDialog(event.state, event.details)
            }
            is MoltingUiEvent.ShowRiskActions -> {
                showRiskActionsDialog(event.riskLevel, event.actions)
            }
            is MoltingUiEvent.NavigateToGuidance -> {
                // Navigate to molting guidance screen
            }
        }
    }
    
    private fun handleCriticalAlert(alert: CriticalMoltAlert?) {
        alert?.let {
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showCriticalAlertDialog(it)
            }
        }
    }
    
    private fun showStateDetailsDialog(state: com.crabtrack.app.data.model.MoltState, details: String) {
        // Show bottom sheet or dialog with state details
        Snackbar.make(binding.root, details, Snackbar.LENGTH_LONG)
            .setAction("Got it") { }
            .show()
    }
    
    private fun showRiskActionsDialog(riskLevel: com.crabtrack.app.data.model.AlertSeverity, actions: List<String>) {
        // Show dialog with recommended actions
        val actionText = actions.joinToString("\n• ", "• ")
        Snackbar.make(binding.root, actionText, Snackbar.LENGTH_LONG)
            .setAction("Dismiss") { }
            .show()
    }
    
    private fun showCriticalAlertDialog(alert: CriticalMoltAlert) {
        // Show high-priority alert dialog
        Snackbar.make(binding.root, "CRITICAL: ${alert.message}", Snackbar.LENGTH_INDEFINITE)
            .setAction("Acknowledge") { 
                viewModel.acknowledgeCriticalAlert(alert.id)
            }
            .show()
    }
    
    override fun onStop() {
        super.onStop()
        // Stop real-time updates when fragment is no longer visible
        viewModel.stopRealTimeUpdates()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up any pending operations
        viewModel.cleanup()
        _binding = null
    }
}