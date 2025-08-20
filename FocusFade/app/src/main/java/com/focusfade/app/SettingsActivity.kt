package com.focusfade.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focusfade.app.databinding.ActivitySettingsBinding
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.receiver.DailyResetScheduler
import com.focusfade.app.service.AppLaunchAccessibilityService
import com.focusfade.app.utils.CrashLogger
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d(TAG, "onCreate() called")
            
            // Initialize CrashLogger if not already initialized
            try {
                CrashLogger.init(this)
                CrashLogger.log("DEBUG", TAG, "SettingsActivity onCreate started")
            } catch (e: Exception) {
                Log.w(TAG, "CrashLogger initialization failed, continuing without it", e)
            }
            
            binding = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            settingsManager = SettingsManager(this)
            
            setupToolbar()
            setupUI()
            observeSettings()
            
            try {
                CrashLogger.log("DEBUG", TAG, "SettingsActivity onCreate completed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "CrashLogger logging failed", e)
            }
            
        } catch (e: Exception) {
            try {
                CrashLogger.log("ERROR", TAG, "Error in onCreate", e)
            } catch (logError: Exception) {
                Log.e(TAG, "CrashLogger failed, original error:", e)
                Log.e(TAG, "CrashLogger error:", logError)
            }
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing settings: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupToolbar() {
        try {
            Log.d(TAG, "Setting up toolbar")
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Settings"
            Log.d(TAG, "Toolbar setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up toolbar", e)
            try {
                CrashLogger.log("ERROR", TAG, "Error setting up toolbar", e)
            } catch (logError: Exception) {
                Log.e(TAG, "CrashLogger failed while logging toolbar error", logError)
            }
        }
    }
    
    private fun setupUI() {
        try {
            Log.d(TAG, "Setting up UI")
            binding.apply {
                // Daily reset time picker
                buttonDailyResetTime.setOnClickListener {
                    showTimePickerDialog()
                }
                
                // Reset all settings
                buttonResetSettings.setOnClickListener {
                    showResetConfirmationDialog()
                }
                
                // Delayed launch switch
                includeLaunchDelay.switchDelayedLaunch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        // Check if accessibility service is enabled
                        if (!isAccessibilityServiceEnabled()) {
                            // Show dialog to enable accessibility service
                            showAccessibilityPermissionDialog()
                            // Revert switch state
                            includeLaunchDelay.switchDelayedLaunch.isChecked = false
                        } else {
                            settingsManager.setDelayedLaunchEnabled(true)
                        }
                    } else {
                        settingsManager.setDelayedLaunchEnabled(false)
                    }
                }

                // Launch delay seekbar
                includeLaunchDelay.delaySeekBar.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        settingsManager.setLaunchDelaySeconds(value.toInt())
                        updateDelayText(value.toInt())
                    }
                }

                // Min blur level slider
                sliderMinBlurLevel.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        lifecycleScope.launch {
                            settingsManager.setMinBlurLevel(value)
                        }
                        updateMinBlurLevelText(value.toInt())
                    }
                }

                // Overlay mode selector buttons (0=Blur,1=Color Shift,2=Pattern)
                buttonSelectBlur.setOnClickListener {
                    try {
                        val serviceIntent = Intent(this@SettingsActivity, com.focusfade.app.service.BlurOverlayService::class.java)
                        serviceIntent.action = "SET_OVERLAY_MODE"
                        serviceIntent.putExtra("mode", 0)
                        startService(serviceIntent)
                        Toast.makeText(this@SettingsActivity, "Blur overlay selected", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error selecting blur mode", e)
                        Toast.makeText(this@SettingsActivity, "Error selecting blur mode", Toast.LENGTH_SHORT).show()
                    }
                }
                buttonSelectColorShift.setOnClickListener {
                    try {
                        val serviceIntent = Intent(this@SettingsActivity, com.focusfade.app.service.BlurOverlayService::class.java)
                        serviceIntent.action = "SET_OVERLAY_MODE"
                        serviceIntent.putExtra("mode", 1)
                        startService(serviceIntent)
                        Toast.makeText(this@SettingsActivity, "Color shift overlay selected", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error selecting color shift mode", e)
                        Toast.makeText(this@SettingsActivity, "Error selecting color shift mode", Toast.LENGTH_SHORT).show()
                    }
                }
                buttonSelectPattern.setOnClickListener {
                    try {
                        val serviceIntent = Intent(this@SettingsActivity, com.focusfade.app.service.BlurOverlayService::class.java)
                        serviceIntent.action = "SET_OVERLAY_MODE"
                        serviceIntent.putExtra("mode", 2)
                        startService(serviceIntent)
                        Toast.makeText(this@SettingsActivity, "Pattern overlay selected", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error selecting pattern mode", e)
                        Toast.makeText(this@SettingsActivity, "Error selecting pattern mode", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Log.d(TAG, "UI setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI", e)
            try {
                CrashLogger.log("ERROR", TAG, "Error setting up UI", e)
            } catch (logError: Exception) {
                Log.e(TAG, "CrashLogger failed while logging UI setup error", logError)
            }
            throw e // Re-throw to be caught by onCreate
        }
    }
    
    private fun observeSettings() {
        lifecycleScope.launch {
            try {
                CrashLogger.log("DEBUG", TAG, "Starting settings observation")
                settingsManager.getAllSettingsFlow().collect { settings ->
                    try {
                        if (!this@SettingsActivity.isFinishing && !this@SettingsActivity.isDestroyed) {
                            runOnUiThread {
                                try {
                                    binding.apply {
                                        // Update daily reset time display
                                        val timeString = String.format("%02d:%02d", settings.dailyResetHour, settings.dailyResetMinute)
                                        textDailyResetTime.text = "Daily reset at $timeString"
                                        
                                        // Update delayed launch settings
                                        includeLaunchDelay.switchDelayedLaunch.isChecked = settingsManager.isDelayedLaunchEnabled()
                                        includeLaunchDelay.delaySeekBar.value = settingsManager.getLaunchDelaySeconds().toFloat()
                                        updateDelayText(settingsManager.getLaunchDelaySeconds())

                                        // Update min blur level
                                        sliderMinBlurLevel.value = settings.minBlurLevel
                                        updateMinBlurLevelText(settings.minBlurLevel.toInt())
                                        
                                        // Update next reset time
                                        try {
                                            textNextReset.text = "Next reset: ${DailyResetScheduler.getNextResetTimeFormatted(settingsManager)}"
                                        } catch (e: Exception) {
                                            CrashLogger.log("ERROR", TAG, "Error formatting next reset time", e)
                                            textNextReset.text = "Next reset: Error calculating time"
                                        }
                                    }
                                } catch (bindErr: Exception) {
                                    CrashLogger.log("ERROR", TAG, "Error applying settings to UI", bindErr)
                                    Log.e(TAG, "Error applying settings to UI", bindErr)
                                }
                            }
                        }
                        CrashLogger.log("DEBUG", TAG, "Settings UI updated successfully")
                    } catch (e: Exception) {
                        CrashLogger.log("ERROR", TAG, "Error updating settings UI", e)
                        Log.e(TAG, "Error updating settings UI", e)
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("ERROR", TAG, "Error in observeSettings", e)
                Log.e(TAG, "Error in observeSettings", e)
                if (!this@SettingsActivity.isFinishing && !this@SettingsActivity.isDestroyed) {
                    Toast.makeText(this@SettingsActivity, "Error loading settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateMinBlurLevelText(level: Int) {
        binding.textMinBlurLevel.text = "Minimum blur level: $level%"
    }

    private fun updateDelayText(seconds: Int) {
        binding.includeLaunchDelay.textLaunchDelay.text = "App launch delay: $seconds sec"
    }
    
    private fun showTimePickerDialog() {
        val currentHour = settingsManager.getDailyResetHour()
        val currentMinute = settingsManager.getDailyResetMinute()
        
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                lifecycleScope.launch {
                    settingsManager.setDailyResetTime(hourOfDay, minute)
                    DailyResetScheduler.rescheduleDailyReset(this@SettingsActivity, settingsManager)
                    Toast.makeText(this@SettingsActivity, "Daily reset time updated", Toast.LENGTH_SHORT).show()
                }
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }
    
    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Settings")
            .setMessage("Are you sure you want to reset all settings to defaults? This cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    settingsManager.resetToDefaults()
                    DailyResetScheduler.rescheduleDailyReset(this@SettingsActivity, settingsManager)
                    Toast.makeText(this@SettingsActivity, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val serviceName = "${packageName}/${AppLaunchAccessibilityService::class.java.name}"
            return !TextUtils.isEmpty(services) && services.contains(serviceName)
        }
        return false
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("To use delayed launch, please enable the FocusFade accessibility service in Settings > Accessibility > FocusFade.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
