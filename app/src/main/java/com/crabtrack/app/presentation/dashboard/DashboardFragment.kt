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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AlertSeverity
import com.crabtrack.app.data.model.CleaningCardStatus
import com.crabtrack.app.data.model.CleaningStatus
import com.crabtrack.app.data.model.FarmCleaningStatus
import com.crabtrack.app.databinding.FragmentDashboardBinding
import com.crabtrack.app.databinding.ItemCleaningStatusCardBinding
import com.crabtrack.app.presentation.dashboard.adapter.FeedingStatusAdapter
import com.crabtrack.app.presentation.dashboard.adapter.WaterParameterCardAdapter
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var parameterCardAdapter: WaterParameterCardAdapter
    private lateinit var feedingStatusAdapter: FeedingStatusAdapter
    private var cleaningCardBinding: ItemCleaningStatusCardBinding? = null

    private val timeFormat12 = SimpleDateFormat("h:mm a", Locale.US)
    private val timeFormat24 = SimpleDateFormat("HH:mm", Locale.US)

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
        setupFeedingStatusRecyclerView()
        setupCleaningStatusCard()
        setupMoltingIndicator()
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

    private fun setupMoltingIndicator() {
        binding.moltingIndicator.setOnClickListener {
            navigateToMolting()
        }
    }

    private fun navigateToMolting() {
        try {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav.selectedItemId = R.id.moltingFragment
            android.util.Log.i("DashboardFragment", "Navigating to molting screen")
        } catch (e: Exception) {
            android.util.Log.e("DashboardFragment", "Failed to navigate to molting: ${e.message}", e)
        }
    }

    private fun setupFeedingStatusRecyclerView() {
        feedingStatusAdapter = FeedingStatusAdapter(
            onConfirmClick = { tankId, feedingLogId ->
                viewModel.confirmFeeding(tankId, feedingLogId)
            },
            onCardClick = { tankId ->
                viewModel.onFeedingCardClicked(tankId)
            }
        )
        binding.recyclerViewFeedingStatus.apply {
            adapter = feedingStatusAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupCleaningStatusCard() {
        cleaningCardBinding = ItemCleaningStatusCardBinding.bind(binding.cleaningStatusCard.root)
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

        // Update molting indicator
        binding.moltingIndicator.visibility = if (uiState.hasMoltingAlerts) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.moltingAlertCount.text = uiState.moltingAlerts.size.toString()
        android.util.Log.d("DashboardFragment", "Molting indicator updated: ${uiState.moltingAlerts.size} alerts")

        // Update feeding status section
        binding.feedingStatusContainer.visibility = if (uiState.feedingStatuses.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        feedingStatusAdapter.submitList(uiState.feedingStatuses)
        android.util.Log.d("DashboardFragment", "Feeding statuses updated: ${uiState.feedingStatuses.size} tanks")

        // Update cleaning status section
        updateCleaningStatusCard(uiState.cleaningStatus)

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

    private fun updateCleaningStatusCard(status: FarmCleaningStatus?) {
        if (status == null) {
            binding.cleaningStatusContainer.visibility = View.GONE
            return
        }

        binding.cleaningStatusContainer.visibility = View.VISIBLE
        val cardBinding = cleaningCardBinding ?: return

        // Update status badge
        when (status.overallStatus) {
            CleaningCardStatus.ALL_CLEAN -> {
                cardBinding.textStatusBadge.text = getString(R.string.cleaning_all_clean)
                cardBinding.textStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.normal_text))
                cardBinding.textStatusBadge.background.setTint(ContextCompat.getColor(requireContext(), R.color.normal_background))
            }
            CleaningCardStatus.PENDING -> {
                cardBinding.textStatusBadge.text = getString(R.string.cleaning_pending)
                cardBinding.textStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning_text))
                cardBinding.textStatusBadge.background.setTint(ContextCompat.getColor(requireContext(), R.color.warning_background))
            }
            CleaningCardStatus.OVERDUE -> {
                cardBinding.textStatusBadge.text = getString(R.string.cleaning_overdue)
                cardBinding.textStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.critical_text))
                cardBinding.textStatusBadge.background.setTint(ContextCompat.getColor(requireContext(), R.color.critical_background))
            }
        }

        // Update scheduled time
        if (status.nextScheduledTime != null) {
            cardBinding.layoutScheduledTime.visibility = View.VISIBLE
            cardBinding.textNoSchedule.visibility = View.GONE
            val formattedTime = formatTime(status.nextScheduledTime)
            cardBinding.textScheduledTime.text = getString(R.string.cleaning_daily_at, formattedTime)
        } else {
            cardBinding.layoutScheduledTime.visibility = View.GONE
            cardBinding.textNoSchedule.visibility = View.VISIBLE
        }

        // Update overdue warning
        if (status.overallStatus == CleaningCardStatus.OVERDUE && status.overdueByMinutes > 0) {
            cardBinding.layoutOverdueWarning.visibility = View.VISIBLE
            val overdueText = if (status.overdueByMinutes >= 60) {
                val hours = status.overdueByMinutes / 60
                val mins = status.overdueByMinutes % 60
                if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
            } else {
                "${status.overdueByMinutes} min"
            }
            cardBinding.textOverdueMessage.text = getString(R.string.cleaning_overdue_by, overdueText)
        } else {
            cardBinding.layoutOverdueWarning.visibility = View.GONE
        }

        // Update Mark Clean button visibility and click handler
        val nextPendingLog = status.getNextPendingLog()
        val nextOverdueLog = status.getOverdueLogs().firstOrNull()
        val actionableLog = nextPendingLog ?: nextOverdueLog

        if (actionableLog != null && (actionableLog.status == CleaningStatus.PENDING || actionableLog.status == CleaningStatus.OVERDUE)) {
            cardBinding.btnConfirmCleaning.visibility = View.VISIBLE
            cardBinding.btnConfirmCleaning.setOnClickListener {
                viewModel.confirmCleaning(actionableLog.id)
            }
        } else {
            cardBinding.btnConfirmCleaning.visibility = View.GONE
        }

        android.util.Log.d("DashboardFragment", "Cleaning status updated: ${status.overallStatus}")
    }

    private fun formatTime(time24: String): String {
        return try {
            val date = timeFormat24.parse(time24) ?: return time24
            timeFormat12.format(date)
        } catch (e: Exception) {
            time24
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleaningCardBinding = null
        _binding = null
    }
}