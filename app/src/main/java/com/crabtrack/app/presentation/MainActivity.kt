package com.crabtrack.app.presentation

import android.Manifest
import com.google.firebase.database.FirebaseDatabase
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.crabtrack.app.R
import com.crabtrack.app.data.model.AuthState
import com.crabtrack.app.databinding.ActivityMainBinding
import com.crabtrack.app.notification.AlertsNotifier
import com.crabtrack.app.notification.MoltingNotifier
import com.crabtrack.app.notification.NotificationCleanupManager
import com.crabtrack.app.presentation.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Auth guard - observe auth state for automatic navigation
    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var alertsNotifier: AlertsNotifier

    @Inject
    lateinit var moltingNotifier: MoltingNotifier

    @Inject
    lateinit var notificationCleanupManager: NotificationCleanupManager

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startNotifiers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupAuthGuard()  // ✅ Critical: Auth state observer will handle notifier startup
        // REMOVED: checkNotificationPermissionAndStart() - now triggered by auth state
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    override fun onStart() {
        super.onStart()
        // Notifiers are started via permission check
    }
    
    override fun onStop() {
        super.onStop()
        // Stop notifiers when activity goes to background
        // They'll restart in onStart if user is still authenticated
        notificationCleanupManager.stopAllNotifiers()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
    }

    /**
     * Auth Guard - Observes authentication state and navigates accordingly
     * This ensures users are on the correct screen based on their auth status
     */
    private fun setupAuthGuard() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    handleAuthState(state)
                }
            }
        }
    }

    private fun handleAuthState(state: AuthState) {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController
        val currentDestination = navController.currentDestination?.id

        when (state) {
            is AuthState.Loading -> {
                // Don't start notifiers - waiting for auth resolution
            }

            is AuthState.Authenticated -> {
                // User is logged in - start notifiers if permission granted
                checkNotificationPermissionAndStartNotifiers()

                when (currentDestination) {
                    // Only auto-redirect from landing screen or completely unknown
                    R.id.landingScreenFragment, null -> {
                        navController.navigate(R.id.action_global_mainFragment)
                    }

                    // If already on login/register/forgot, let those fragments
                    // handle navigation (e.g., after showing success dialog)
                    R.id.loginFragment,
                    R.id.registerFragment,
                    R.id.forgotPasswordFragment -> {
                        // do nothing – fragment decides what to do
                    }

                    // Already inside the app (main, dashboard, settings, etc.)
                    else -> {
                        // no-op
                    }
                }
            }

            is AuthState.Unauthenticated -> {
                // User is logged out - stop notifiers immediately
                notificationCleanupManager.stopAllNotifiers()

                // Navigate to login if on protected screens
                if (isOnProtectedScreen(currentDestination)) {
                    navController.navigate(R.id.action_global_loginFragment)
                }
            }

            is AuthState.Error -> {
                // On error, stop notifiers to be safe
                notificationCleanupManager.stopAllNotifiers()
                android.util.Log.e("MainActivity", "Auth error: ${state.message}")
            }
        }
    }


    private fun isOnAuthScreen(destinationId: Int?): Boolean {
        return destinationId in listOf(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment,
            R.id.landingScreenFragment
        )
    }

    private fun isOnProtectedScreen(destinationId: Int?): Boolean {
        // All screens except auth screens are protected
        return !isOnAuthScreen(destinationId)
    }
    
    private fun checkNotificationPermissionAndStartNotifiers() {
        // Only called when user is authenticated (from handleAuthState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> {
                    startNotifiers()
                }
                else -> {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startNotifiers()
        }
    }
    
    private fun startNotifiers() {
        // Start alert notifications for water parameter monitoring
        // Only called when user is authenticated
        notificationCleanupManager.startAllNotifiers(lifecycleScope)
    }
    
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val navigateTo = it.getStringExtra("navigate_to")
            when (navigateTo) {
                "alerts" -> navigateToAlerts()
                "molting" -> navigateToMolting()
            }
        }
    }
    
    private fun navigateToAlerts() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // First ensure we're on the main fragment
        if (navController.currentDestination?.id != R.id.mainFragment) {
            navController.navigate(R.id.action_global_mainFragment)
        }

        // The MainFragment's nested nav graph will handle navigation to alertsFragment
        // via its bottom navigation, so we don't need to navigate here
    }

    private fun navigateToMolting() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // First ensure we're on the main fragment
        if (navController.currentDestination?.id != R.id.mainFragment) {
            navController.navigate(R.id.action_global_mainFragment)
        }

        // The MainFragment's nested nav graph will handle navigation to cameraFragment
        // via its bottom navigation, so we don't need to navigate here
    }
}