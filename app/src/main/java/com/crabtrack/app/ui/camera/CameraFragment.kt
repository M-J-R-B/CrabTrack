package com.crabtrack.app.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.crabtrack.app.databinding.FragmentCameraBinding
import com.crabtrack.app.ui.camera.adapter.CameraStreamAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment displaying live camera streams for tank monitoring
 */
@AndroidEntryPoint
class CameraFragment : Fragment() {
    
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CameraViewModel by viewModels()
    private lateinit var streamAdapter: CameraStreamAdapter
    
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
        
        setupRecyclerView()
        setupTabs()
        observeViewModel()
        observeLifecycleEvents()
    }
    
    private fun setupRecyclerView() {
        streamAdapter = CameraStreamAdapter(
            onPlayPause = { stream ->
                viewModel.toggleStream(stream)
            },
            onQualityChange = { stream, quality ->
                viewModel.changeStreamQuality(stream.id, quality)
            },
            onSnapshot = { stream ->
                viewModel.takeSnapshot(stream.id)
            }
        )
        
        binding.streamsRecyclerView.apply {
            adapter = streamAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun setupTabs() {
        binding.tankTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.tag?.let { tankId ->
                    viewModel.selectTank(tankId as String)
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe UI state
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUI(uiState)
                    }
                }
                
                // Observe UI events
                launch {
                    viewModel.uiEvents.collect { event ->
                        event?.let { handleUiEvent(it) }
                    }
                }
                
                // Observe stream metrics
                launch {
                    viewModel.streamMetrics.collect { metricsMap ->
                        metricsMap.forEach { (streamId, metrics) ->
                            streamAdapter.updateStreamMetrics(streamId, metrics)
                        }
                    }
                }
                
                // Observe available tanks
                launch {
                    viewModel.availableTanks.collect { tanks ->
                        updateTabs(tanks)
                    }
                }
            }
        }
    }
    
    private fun observeLifecycleEvents() {
        // Auto-refresh when fragment becomes visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshStreams()
            }
        }
    }
    
    private fun updateUI(uiState: CameraUiState) {
        binding.apply {
            // Loading state
            loadingIndicator.isVisible = uiState.isLoading
            
            // Streams
            streamAdapter.submitList(uiState.streams)
            
            // Empty state
            val hasStreams = uiState.streams.isNotEmpty()
            streamsRecyclerView.isVisible = hasStreams && !uiState.isLoading
            emptyStateLayout.isVisible = !hasStreams && !uiState.isLoading
            
            // Error handling
            uiState.errorMessage?.let { message ->
                if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    showError(message)
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun updateTabs(tanks: List<String>) {
        binding.tankTabLayout.removeAllTabs()
        
        tanks.forEach { tankId ->
            val tab = binding.tankTabLayout.newTab()
            tab.text = "Tank ${tankId.takeLast(3)}" // Show last 3 characters of tank ID
            tab.tag = tankId
            binding.tankTabLayout.addTab(tab)
        }
        
        // Select first tab by default if none selected
        if (tanks.isNotEmpty() && binding.tankTabLayout.selectedTabPosition == -1) {
            binding.tankTabLayout.selectTab(binding.tankTabLayout.getTabAt(0))
        }
    }
    
    private fun handleUiEvent(event: CameraUiEvent) {
        when (event) {
            is CameraUiEvent.ShowMessage -> {
                showMessage(event.message)
            }
            is CameraUiEvent.StreamStarted -> {
                showMessage("Stream started")
            }
            is CameraUiEvent.StreamStopped -> {
                showMessage("Stream stopped")
            }
            is CameraUiEvent.SnapshotTaken -> {
                showMessage("Snapshot saved")
            }
        }
        viewModel.clearUiEvent()
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                viewModel.refreshStreams()
            }
            .show()
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .show()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop all streams when fragment goes to background to save bandwidth
        viewModel.stopAllStreams()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}