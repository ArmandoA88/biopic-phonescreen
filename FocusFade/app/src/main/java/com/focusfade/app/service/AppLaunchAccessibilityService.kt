package com.focusfade.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.focusfade.app.DelayedLaunchActivity
import com.focusfade.app.manager.SettingsManager

/**
 * AccessibilityService to detect app launches and trigger delayed launch for blacklisted apps
 */
class AppLaunchAccessibilityService : AccessibilityService() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var bypassPrefs: SharedPreferences
    private lateinit var executionPrefs: SharedPreferences
    
    companion object {
        private const val BYPASS_PREFS = "delayed_launch_bypass"
        private const val BYPASS_DURATION_MS = 300000L // 5 minutes bypass window (much longer)
        private const val EXECUTION_PREFS = "delayed_launch_executions"
        private const val DAILY_RESET_HOUR = 4 // Reset at 4 AM
        private const val EMERGENCY_STOP_PREFS = "emergency_stop"
        private const val MAX_ATTEMPTS_PER_MINUTE = 2 // Maximum 2 attempts per minute before emergency stop
        private const val IMMEDIATE_BLOCK_MS = 5000L // Block for 5 seconds after any execution
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsManager = SettingsManager(this)
        bypassPrefs = getSharedPreferences(BYPASS_PREFS, Context.MODE_PRIVATE)
        executionPrefs = getSharedPreferences(EXECUTION_PREFS, Context.MODE_PRIVATE)
        
        // Clean up old executions on service start
        cleanupOldExecutions()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            
            if (packageName != null && packageName != this.packageName) {
                // EMERGENCY STOP CHECK: If emergency stop is active, disable all delayed launch
                if (isEmergencyStopActive()) {
                    return
                }
                
                // Check if delayed launch is enabled
                if (!settingsManager.isDelayedLaunchEnabled()) {
                    return
                }
                
                // Check if app is in the delayed launch apps list (blacklist)
                val delayedLaunchApps = settingsManager.getDelayedLaunchApps()
                if (delayedLaunchApps.contains(packageName)) {
                    val currentTime = System.currentTimeMillis()
                    
                    // IMMEDIATE BLOCK CHECK: Has this app been accessed in the last 5 seconds?
                    val lastAccessTime = bypassPrefs.getLong("${packageName}_last_access", 0L)
                    if (currentTime - lastAccessTime < IMMEDIATE_BLOCK_MS) {
                        // Too recent, block completely and activate emergency stop
                        activateEmergencyStop()
                        return
                    }
                    
                    // EMERGENCY STOP CHECK: Detect rapid attempts
                    if (detectLoopAndActivateEmergencyStop(packageName, currentTime)) {
                        return
                    }
                    
                    // EXECUTION CHECK: Has this app already been executed today?
                    if (hasBeenExecutedToday(packageName)) {
                        // Update last access time but allow launch
                        bypassPrefs.edit().putLong("${packageName}_last_access", currentTime).apply()
                        return
                    }
                    
                    // BYPASS CHECK: Is this app currently bypassed?
                    val lastBypassTime = bypassPrefs.getLong(packageName, 0L)
                    if (currentTime - lastBypassTime < BYPASS_DURATION_MS) {
                        // Update last access time but allow launch
                        bypassPrefs.edit().putLong("${packageName}_last_access", currentTime).apply()
                        return
                    }
                    
                    // RECORD ALL ACCESS: Track every access attempt
                    bypassPrefs.edit().putLong("${packageName}_last_access", currentTime).apply()
                    recordAttempt(packageName, currentTime)
                    
                    // EXECUTE DELAY: Mark as executed and set bypass
                    markAsExecutedToday(packageName)
                    bypassPrefs.edit().putLong(packageName, currentTime).apply()
                    
                    // Clean up old entries periodically
                    cleanupOldBypassEntries()
                    cleanupOldExecutions()
                    
                    // Launch delayed launch activity
                    val launchIntent = Intent(this, DelayedLaunchActivity::class.java)
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    launchIntent.putExtra("TARGET_PACKAGE", packageName)
                    
                    // Check if there's a specific delay for this app
                    val specificDelay = settingsManager.getAppSpecificDelay(packageName)
                    if (specificDelay != null) {
                        launchIntent.putExtra("DELAY_SECONDS", specificDelay)
                    }
                    
                    startActivity(launchIntent)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }
    
    /**
     * Check if an app has already been delayed today
     */
    private fun hasBeenExecutedToday(packageName: String): Boolean {
        val today = getTodayKey()
        val executionKey = "${packageName}_$today"
        return executionPrefs.getBoolean(executionKey, false)
    }
    
    /**
     * Mark an app as having been delayed today
     */
    private fun markAsExecutedToday(packageName: String) {
        val today = getTodayKey()
        val executionKey = "${packageName}_$today"
        executionPrefs.edit().putBoolean(executionKey, true).apply()
    }
    
    /**
     * Get today's key for tracking executions (format: YYYY-MM-DD)
     */
    private fun getTodayKey(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", year, month, day)
    }
    
    /**
     * Clean up old execution entries (older than 7 days)
     */
    private fun cleanupOldExecutions() {
        val currentTime = System.currentTimeMillis()
        val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000L) // 7 days in milliseconds
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = sevenDaysAgo
        
        val editor = executionPrefs.edit()
        var hasChanges = false
        
        // Get all execution entries
        for (entry in executionPrefs.all) {
            val key = entry.key
            if (key.contains("_")) {
                val datePart = key.substringAfterLast("_")
                try {
                    val parts = datePart.split("-")
                    if (parts.size == 3) {
                        val entryYear = parts[0].toInt()
                        val entryMonth = parts[1].toInt() - 1 // Calendar.MONTH is 0-based
                        val entryDay = parts[2].toInt()
                        
                        val entryCalendar = java.util.Calendar.getInstance()
                        entryCalendar.set(entryYear, entryMonth, entryDay)
                        
                        if (entryCalendar.timeInMillis < sevenDaysAgo) {
                            editor.remove(key)
                            hasChanges = true
                        }
                    }
                } catch (e: Exception) {
                    // Invalid date format, remove it
                    editor.remove(key)
                    hasChanges = true
                }
            }
        }
        
        if (hasChanges) {
            editor.apply()
        }
    }
    
    /**
     * Check if emergency stop is currently active
     */
    private fun isEmergencyStopActive(): Boolean {
        val emergencyPrefs = getSharedPreferences(EMERGENCY_STOP_PREFS, Context.MODE_PRIVATE)
        val stopTime = emergencyPrefs.getLong("stop_time", 0L)
        val currentTime = System.currentTimeMillis()
        
        // Emergency stop lasts for 1 hour
        return (currentTime - stopTime) < (60 * 60 * 1000L)
    }
    
    /**
     * Record an attempt for loop detection
     */
    private fun recordAttempt(packageName: String, currentTime: Long) {
        val emergencyPrefs = getSharedPreferences(EMERGENCY_STOP_PREFS, Context.MODE_PRIVATE)
        val attemptsKey = "attempts_$packageName"
        val attempts = emergencyPrefs.getString(attemptsKey, "") ?: ""
        
        // Add current timestamp to attempts list
        val newAttempts = if (attempts.isEmpty()) {
            currentTime.toString()
        } else {
            "$attempts,$currentTime"
        }
        
        emergencyPrefs.edit().putString(attemptsKey, newAttempts).apply()
    }
    
    /**
     * Detect if there's a loop and activate emergency stop if needed
     */
    private fun detectLoopAndActivateEmergencyStop(packageName: String, currentTime: Long): Boolean {
        val emergencyPrefs = getSharedPreferences(EMERGENCY_STOP_PREFS, Context.MODE_PRIVATE)
        val attemptsKey = "attempts_$packageName"
        val attempts = emergencyPrefs.getString(attemptsKey, "") ?: ""
        
        if (attempts.isNotEmpty()) {
            val attemptsList = attempts.split(",").mapNotNull { it.toLongOrNull() }
            val oneMinuteAgo = currentTime - (60 * 1000L) // 1 minute ago
            
            // Count attempts in the last minute
            val recentAttempts = attemptsList.filter { it > oneMinuteAgo }
            
            if (recentAttempts.size >= MAX_ATTEMPTS_PER_MINUTE) {
                // Too many attempts, activate emergency stop
                activateEmergencyStop()
                return true
            }
        }
        
        return false
    }
    
    /**
     * Activate emergency stop - completely disables delayed launch for 1 hour
     */
    private fun activateEmergencyStop() {
        val emergencyPrefs = getSharedPreferences(EMERGENCY_STOP_PREFS, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        
        emergencyPrefs.edit()
            .putLong("stop_time", currentTime)
            .putBoolean("is_active", true)
            .apply()
        
        // Also disable delayed launch in settings as a backup
        try {
            kotlinx.coroutines.runBlocking {
                settingsManager.setDelayedLaunchEnabled(false)
            }
        } catch (e: Exception) {
            // Ignore if settings update fails
        }
        
        // Clear all attempts
        val editor = emergencyPrefs.edit()
        for (key in emergencyPrefs.all.keys) {
            if (key.startsWith("attempts_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
    
    /**
     * Clean up old bypass entries to prevent SharedPreferences from growing indefinitely
     */
    private fun cleanupOldBypassEntries() {
        val currentTime = System.currentTimeMillis()
        val editor = bypassPrefs.edit()
        var hasChanges = false
        
        for (entry in bypassPrefs.all) {
            val timestamp = entry.value as? Long ?: 0L
            if (currentTime - timestamp > BYPASS_DURATION_MS * 2) { // Clean entries older than 2x bypass duration
                editor.remove(entry.key)
                hasChanges = true
            }
        }
        
        if (hasChanges) {
            editor.apply()
        }
    }
}
