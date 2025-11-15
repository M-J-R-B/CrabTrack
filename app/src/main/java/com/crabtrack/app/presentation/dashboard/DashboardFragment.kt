package com.crabtrack.app.presentation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.databinding.FragmentDashboardBinding
import com.crabtrack.app.presentation.dashboard.adapter.WaterParameterCardAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var parameterCardAdapter: WaterParameterCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSwipeRefresh()
        observeUiState()
    }

    private fun setupRecyclerView() {
        parameterCardAdapter = WaterParameterCardAdapter { parameter ->
        }
        binding.recyclerViewSensors.apply {
            adapter = parameterCardAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun observeUiState() {
        // Properly scoped collection using viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUI(uiState)
                    }
                }
                
                // Additional collection for any separate flows
                launch {
                    viewModel.alertEvents.collect { event ->
                        event?.let { handleAlertEvent(it) }
                    }
                }
            }
        }
    }
    
    private fun handleAlertEvent(event: AlertEvent) {
        when (event) {
            is AlertEvent.ShowAlert -> {
                // Alert toast notifications removed - alerts are shown in dedicated alerts page
            }
            is AlertEvent.NavigateToAlerts -> {
                // Handle navigation to alerts if needed
            }
        }
    }

    private fun updateUI(uiState: DashboardUiState) {
        android.util.Log.d("DashboardFragment", "updateUI called - hasReading: ${uiState.latestReading != null}, isLoading: ${uiState.isLoading}")

        binding.swipeRefreshLayout.isRefreshing = uiState.isLoading

        // Show/hide empty state
        if (uiState.latestReading == null && uiState.errorMessage == null) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerViewSensors.visibility = View.GONE
            android.util.Log.d("DashboardFragment", "Showing empty state")
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerViewSensors.visibility = View.VISIBLE
        }

        uiState.latestReading?.let { reading ->
            android.util.Log.d("DashboardFragment", "Updating RecyclerView with reading data")
            val cards = WaterParameterCardAdapter.createCardsFromReading(reading) { parameter ->
                viewModel.getParameterSeverity(parameter)
            }
            parameterCardAdapter.submitList(cards)
        }

        binding.alertIndicator.visibility = if (uiState.overallSeverity != AlertSeverity.INFO) {
            View.VISIBLE
        } else {
            View.GONE
        }

        when (uiState.overallSeverity) {
            AlertSeverity.CRITICAL -> {
                binding.alertIndicator.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.critical_background)
                )
            }
            AlertSeverity.WARNING -> {
                binding.alertIndicator.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.warning_background)
                )
            }
            AlertSeverity.INFO -> {
            }
        }

        uiState.errorMessage?.let { message ->
            // Only show error if fragment is in foreground to avoid memory leaks
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") { 
                        viewModel.clearError()
                        viewModel.refreshData()
                    }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}