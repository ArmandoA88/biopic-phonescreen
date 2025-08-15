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
                setManualModeVisuals(false) // Reset visual indicators
                Toast.makeText(this@MainActivity, "Blur reset", Toast.LENGTH_SHORT).show()
            }
            
            // Manual blur toggle button
            buttonToggleManualBlur.setOnClickListener {
                val intent = Intent(this@MainActivity, BlurOverlayService::class.java).apply {
                    action = BlurOverlayService.ACTION_TOGGLE_MANUAL_BLUR
                }
                startService(intent)
                
                // Check current button text to determine if we're entering or exiting manual mode
                if (buttonToggleManualBlur.text == "Exit Manual") {
                    // Exiting manual mode
                    setManualModeVisuals(false)
                    Toast.makeText(this@MainActivity, "Manual mode disabled", Toast.LENGTH_SHORT).show()
                } else {
                    // Entering manual mode
                    setManualModeVisuals(true)
                    Toast.makeText(this@MainActivity, "Manual mode enabled - drag orange bar to adjust", Toast.LENGTH_SHORT).show()
                }
            }

            // Dragging Current Status bar to adjust blur in manual mode with 1% increments
            progressBlurLevel.setOnTouchListener(object : android.view.View.OnTouchListener {
                private var initialX = 0f
                private var startPercent = 0f
                private var isDragging = false
                private var hasShownToast = false

                override fun onTouch(v: android.view.View?, event: android.view.MotionEvent?): Boolean {
                    if (event == null) return false
                    
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = event.rawX
                            startPercent = progressBlurLevel.progress.toFloat()
                            isDragging = true
                            hasShownToast = false
                            
                            // Activate manual mode when user starts dragging
                            if (buttonToggleManualBlur.text != "Exit Manual") {
                                val intent = Intent(this@MainActivity, BlurOverlayService::class.java).apply {
                                    action = BlurOverlayService.ACTION_TOGGLE_MANUAL_BLUR
                                }
                                startService(intent)
                                setManualModeVisuals(true)
                                
                                // Show guidance toast only once per drag session
                                if (!hasShownToast) {
                                    Toast.makeText(this@MainActivity, "Drag left/right to adjust blur level", Toast.LENGTH_SHORT).show()
                                    hasShownToast = true
                                }
                            }
                            
                            // Consume the touch event
                            v?.parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                        
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (!isDragging) return false
                            
                            val dx = event.rawX - initialX
                            // Horizontal control - 30 pixels = 1% change
                            val deltaPercent = dx / 30f
                            val newPercent = (startPercent + deltaPercent).coerceIn(0f, 100f)
                            // Round to nearest 1% for precise control
                            val roundedPercent = kotlin.math.round(newPercent).toInt()
                            
                            // Update progress bar immediately for visual feedback
                            progressBlurLevel.progress = roundedPercent
                            
                            // Update text with manual mode indicator
                            if (buttonToggleManualBlur.text == "Exit Manual") {
                                textCurrentBlurLevel.text = "Current Blur: ${roundedPercent}% (Manual - Drag left/right ↔)"
                            } else {
                                textCurrentBlurLevel.text = "Current Blur: ${roundedPercent}% (Manual)"
                            }
                            
                            // Send to service
                            val serviceIntent = Intent(this@MainActivity, BlurOverlayService::class.java).apply {
                                action = BlurOverlayService.ACTION_SET_MANUAL_BLUR
                                putExtra(BlurOverlayService.EXTRA_BLUR_LEVEL, roundedPercent.toFloat())
                            }
                            startService(serviceIntent)
                            return true
                        }
                        
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            v?.parent?.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                    }
                    return false
                }
            })
            
            // Settings button
            buttonSettings.setOnClickListener {
                try {
                    CrashLogger.log("DEBUG", "MainActivity", "Settings button clicked")
                    val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    CrashLogger.log("ERROR", "MainActivity", "Error launching SettingsActivity", e)
                    Toast.makeText(this@MainActivity, "Error opening settings: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
                // Only update if not in manual mode (to avoid conflicts with manual dragging)
                if (binding.buttonToggleManualBlur.text != "Exit Manual") {
                    binding.textCurrentBlurLevel.text = "Current Blur: ${blurLevel.toInt()}%"
                    binding.progressBlurLevel.progress = blurLevel.toInt()
                }
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
    
    private fun setManualModeVisuals(isManualMode: Boolean) {
        if (isManualMode) {
            // Change progress bar color to indicate manual mode
            binding.progressBlurLevel.progressTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            )
            // Use custom progress drawable with rounded corners
            binding.progressBlurLevel.progressDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.progress_bar_with_thumb)
            
            // Make progress bar taller for easier dragging
            val layoutParams = binding.progressBlurLevel.layoutParams
            layoutParams.height = (24 * resources.displayMetrics.density).toInt() // 24dp
            binding.progressBlurLevel.layoutParams = layoutParams
            
            // Update text to show manual mode with drag instructions
            val currentBlur = binding.progressBlurLevel.progress
            binding.textCurrentBlurLevel.text = "Current Blur: ${currentBlur}% (Manual - Drag orange bar ↔)"
            // Change button text to indicate active manual mode
            binding.buttonToggleManualBlur.text = "Exit Manual"
            binding.buttonToggleManualBlur.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_orange_light)
            )
        } else {
            // Reset to normal colors and size
            binding.progressBlurLevel.progressTintList = null
            binding.progressBlurLevel.progressDrawable = null
            
            // Reset height to default
            val layoutParams = binding.progressBlurLevel.layoutParams
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            binding.progressBlurLevel.layoutParams = layoutParams
            
            val currentBlur = binding.progressBlurLevel.progress
            binding.textCurrentBlurLevel.text = "Current Blur: ${currentBlur}%"
            binding.buttonToggleManualBlur.text = "Manual Blur"
            binding.buttonToggleManualBlur.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.transparent)
            )
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
