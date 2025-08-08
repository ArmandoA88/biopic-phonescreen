package com.focusfade.app

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focusfade.app.databinding.ActivitySettingsBinding
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.receiver.DailyResetScheduler
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsManager = SettingsManager(this)
        
        setupToolbar()
        setupUI()
        observeSettings()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }
    
    private fun setupUI() {
        binding.apply {
            // Daily reset time picker
            buttonDailyResetTime.setOnClickListener {
                showTimePickerDialog()
            }
            
            // Reset all settings
            buttonResetSettings.setOnClickListener {
                showResetConfirmationDialog()
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
            binding.buttonSelectBlur.setOnClickListener {
                try {
                    val serviceIntent = Intent(this@SettingsActivity, com.focusfade.app.service.BlurOverlayService::class.java)
                    serviceIntent.action = "SET_OVERLAY_MODE"
                    serviceIntent.putExtra("mode", 0)
                    startService(serviceIntent)
                    Toast.makeText(this@SettingsActivity, "Blur overlay selected", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
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
                    Toast.makeText(this@SettingsActivity, "Error selecting pattern mode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun observeSettings() {
        lifecycleScope.launch {
            settingsManager.getAllSettingsFlow().collect { settings ->
                binding.apply {
                    // Update daily reset time display
                    val timeString = String.format("%02d:%02d", settings.dailyResetHour, settings.dailyResetMinute)
                    textDailyResetTime.text = "Daily reset at $timeString"
                    
                    // Update min blur level
                    sliderMinBlurLevel.value = settings.minBlurLevel
                    updateMinBlurLevelText(settings.minBlurLevel.toInt())
                    
                    // Update next reset time
                    textNextReset.text = "Next reset: ${DailyResetScheduler.getNextResetTimeFormatted(settingsManager)}"
                }
            }
        }
    }
    
    private fun updateMinBlurLevelText(level: Int) {
        binding.textMinBlurLevel.text = "Minimum blur level: $level%"
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
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
