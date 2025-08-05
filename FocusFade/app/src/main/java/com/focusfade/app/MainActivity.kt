package com.focusfade.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focusfade.app.databinding.ActivityMainBinding
import com.focusfade.app.manager.FocusStateManager
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.manager.WhitelistManager
import com.focusfade.app.receiver.DailyResetScheduler
import com.focusfade.app.service.BlurOverlayService
import com.focusfade.app.service.ScreenTimeTrackingService
import com.focusfade.app.utils.CrashLogger
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var focusStateManager: FocusStateManager
    private lateinit var whitelistManager: WhitelistManager
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Settings.canDrawOverlays(this)) {
            startServices()
        } else {
            Toast.makeText(this, "Overlay permission is required for FocusFade to work", Toast.LENGTH_LONG).show()
        }
    }
    
    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (whitelistManager.hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage stats permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Usage stats permission is needed for app whitelisting", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeManagers()
        setupUI()
        observeState()
        
        if (settingsManager.isFirstLaunch()) {
            handleFirstLaunch()
        }
    }
    
    private fun initializeManagers() {
        // Initialize crash logger first
        CrashLogger.init(this)
        CrashLogger.log("INFO", "MainActivity", "App started - initializing managers")
        
        settingsManager = SettingsManager(this)
        focusStateManager = FocusStateManager.getInstance(this, settingsManager)
        whitelistManager = WhitelistManager(this, settingsManager)
        
        CrashLogger.log("INFO", "MainActivity", "All managers initialized successfully")
    }
    
    private fun setupUI() {
        binding.apply {
            // Service control
            switchServiceEnabled.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    settingsManager.setServiceEnabled(isChecked)
                    if (isChecked) {
                        checkPermissionsAndStartServices()
                    } else {
                        stopServices()
                    }
                }
            }
            
            // Reset blur button
            buttonResetBlur.setOnClickListener {
                focusStateManager.resetBlur()
                Toast.makeText(this@MainActivity, "Blur reset", Toast.LENGTH_SHORT).show()
            }
            
            // Manual blur toggle button
            buttonToggleManualBlur.setOnClickListener {
                val intent = Intent(this@MainActivity, BlurOverlayService::class.java).apply {
                    action = BlurOverlayService.ACTION_TOGGLE_MANUAL_BLUR
                }
                startService(intent)
                Toast.makeText(this@MainActivity, "Manual blur toggled", Toast.LENGTH_SHORT).show()
            }
            
            // Settings button
            buttonSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            
            // Whitelist button
            buttonWhitelist.setOnClickListener {
                startActivity(Intent(this@MainActivity, WhitelistActivity::class.java))
            }
            
            // Debug logs button
            buttonDebugLogs.setOnClickListener {
                startActivity(Intent(this@MainActivity, DebugLogActivity::class.java))
            }
            
            // Blur gain rate slider
            sliderBlurGainRate.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    lifecycleScope.launch {
                        settingsManager.setBlurGainRate(value.toInt())
                    }
                    updateBlurGainRateText(value.toInt())
                }
            }
            
            // Blur recovery rate slider
            sliderBlurRecoveryRate.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    lifecycleScope.launch {
                        settingsManager.setBlurRecoveryRate(value.toInt())
                    }
                    updateBlurRecoveryRateText(value.toInt())
                }
            }
            
            // Max blur level slider
            sliderMaxBlurLevel.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    lifecycleScope.launch {
                        settingsManager.setMaxBlurLevel(value)
                    }
                    updateMaxBlurLevelText(value.toInt())
                }
            }
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            // Observe service enabled state
            settingsManager.getServiceEnabledFlow().collect { enabled ->
                binding.switchServiceEnabled.isChecked = enabled
                updateServiceStatus(enabled)
            }
        }
        
        lifecycleScope.launch {
            // Observe current blur level
            focusStateManager.currentBlurLevel.collect { blurLevel ->
                binding.textCurrentBlurLevel.text = "Current Blur: ${blurLevel.toInt()}%"
                binding.progressBlurLevel.progress = blurLevel.toInt()
            }
        }
        
        lifecycleScope.launch {
            // Observe settings changes
            combine(
                settingsManager.getBlurGainRateFlow(),
                settingsManager.getBlurRecoveryRateFlow(),
                settingsManager.getMaxBlurLevelFlow()
            ) { gainRate, recoveryRate, maxBlur ->
                binding.sliderBlurGainRate.value = gainRate.toFloat()
                binding.sliderBlurRecoveryRate.value = recoveryRate.toFloat()
                binding.sliderMaxBlurLevel.value = maxBlur
                
                updateBlurGainRateText(gainRate)
                updateBlurRecoveryRateText(recoveryRate)
                updateMaxBlurLevelText(maxBlur.toInt())
            }.collect { }
        }
        
        lifecycleScope.launch {
            // Observe daily reset time
            settingsManager.getDailyResetTimeFlow().collect { (hour, minute) ->
                binding.textNextReset.text = "Next reset: ${DailyResetScheduler.getNextResetTimeFormatted(settingsManager)}"
            }
        }
    }
    
    private fun updateBlurGainRateText(rate: Int) {
        binding.textBlurGainRate.text = "Blur gain: +10% every $rate minutes"
    }
    
    private fun updateBlurRecoveryRateText(rate: Int) {
        binding.textBlurRecoveryRate.text = "Blur recovery: -10% every $rate minutes"
    }
    
    private fun updateMaxBlurLevelText(level: Int) {
        binding.textMaxBlurLevel.text = "Max blur level: $level%"
    }
    
    private fun updateServiceStatus(enabled: Boolean) {
        binding.textServiceStatus.text = if (enabled) {
            "FocusFade is active"
        } else {
            "FocusFade is disabled"
        }
    }
    
    private fun handleFirstLaunch() {
        lifecycleScope.launch {
            // Show welcome message or tutorial
            Toast.makeText(this@MainActivity, "Welcome to FocusFade! Grant permissions to get started.", Toast.LENGTH_LONG).show()
            settingsManager.setFirstLaunchComplete()
        }
    }
    
    private fun checkPermissionsAndStartServices() {
        when {
            !Settings.canDrawOverlays(this) -> {
                requestOverlayPermission()
            }
            !whitelistManager.hasUsageStatsPermission() -> {
                requestUsageStatsPermission()
            }
            else -> {
                startServices()
            }
        }
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
    
    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsPermissionLauncher.launch(intent)
    }
    
    private fun startServices() {
        try {
            // Start screen time tracking service
            val screenTrackingIntent = Intent(this, ScreenTimeTrackingService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(screenTrackingIntent)
            } else {
                startService(screenTrackingIntent)
            }
            
            // Start blur overlay service
            val blurServiceIntent = Intent(this, BlurOverlayService::class.java).apply {
                action = BlurOverlayService.ACTION_START_SERVICE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(blurServiceIntent)
            } else {
                startService(blurServiceIntent)
            }
            
            // Schedule daily reset
            DailyResetScheduler.scheduleDailyReset(this, settingsManager)
            
            Toast.makeText(this, "FocusFade services started", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start services: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopServices() {
        try {
            // Stop services
            stopService(Intent(this, ScreenTimeTrackingService::class.java))
            
            val blurServiceIntent = Intent(this, BlurOverlayService::class.java).apply {
                action = BlurOverlayService.ACTION_STOP_SERVICE
            }
            startService(blurServiceIntent)
            
            // Cancel daily reset
            DailyResetScheduler.cancelDailyReset(this)
            
            Toast.makeText(this, "FocusFade services stopped", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping services: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update UI when returning from settings
        lifecycleScope.launch {
            binding.textNextReset.text = "Next reset: ${DailyResetScheduler.getNextResetTimeFormatted(settingsManager)}"
        }
    }
}
