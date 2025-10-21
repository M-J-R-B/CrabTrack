package com.crabtrack.app.presentation

import android.Manifest
import com.google.firebase.database.FirebaseDatabase
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.crabtrack.app.R
import com.crabtrack.app.databinding.ActivityMainBinding
import com.crabtrack.app.notification.AlertsNotifier
import com.crabtrack.app.notification.MoltingNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    @Inject
    lateinit var alertsNotifier: AlertsNotifier
    
    @Inject
    lateinit var moltingNotifier: MoltingNotifier
    
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
        checkNotificationPermissionAndStart()
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
        alertsNotifier.stopCollection()
        moltingNotifier.stopCollection()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
    }
    
    private fun checkNotificationPermissionAndStart() {
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
        lifecycleScope.launch {
            alertsNotifier.startCollection(lifecycleScope)
        }
        lifecycleScope.launch {
            moltingNotifier.startCollection(lifecycleScope)
        }
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
        
        if (navController.currentDestination?.id != R.id.alertsFragment) {
            navController.navigate(R.id.alertsFragment)
        }
    }
    
    private fun navigateToMolting() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        if (navController.currentDestination?.id != R.id.moltingFragment) {
            navController.navigate(R.id.moltingFragment)
        }
    }
}