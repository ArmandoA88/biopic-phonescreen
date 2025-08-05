package com.focusfade.app.manager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Manages the current focus state and blur level calculations
 */
class FocusStateManager private constructor(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    
    companion object {
        @Volatile
        private var INSTANCE: FocusStateManager? = null
        
        fun getInstance(context: Context, settingsManager: SettingsManager): FocusStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusStateManager(context.applicationContext, settingsManager).also { INSTANCE = it }
            }
        }
    }
    
    private val _currentBlurLevel = MutableStateFlow(0f)
    val currentBlurLevel: StateFlow<Float> = _currentBlurLevel.asStateFlow()
    
    private val _isScreenOn = MutableStateFlow(false)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()
    
    private var lastScreenOnTime = 0L
    private var lastScreenOffTime = 0L
    private var accumulatedScreenOnTime = 0L
    private var blurStartTime = 0L
    private var isBlurAccumulationPaused = false
    
    /**
     * Called when screen turns on
     */
    fun onScreenOn() {
        if (!_isScreenOn.value) {
            _isScreenOn.value = true
            lastScreenOnTime = System.currentTimeMillis()
            blurStartTime = System.currentTimeMillis()
            
            // Stop blur recovery if it was happening
            stopBlurRecovery()
        }
    }
    
    /**
     * Called when screen turns off
     */
    fun onScreenOff() {
        if (_isScreenOn.value) {
            _isScreenOn.value = false
            lastScreenOffTime = System.currentTimeMillis()
            
            // Add to accumulated screen time
            if (lastScreenOnTime > 0) {
                accumulatedScreenOnTime += (lastScreenOffTime - lastScreenOnTime)
            }
            
            // Start blur recovery
            startBlurRecovery()
        }
    }
    
    /**
     * Updates blur level based on current screen time
     * New behavior: Start at 10% blur, increase by 10% every 10 seconds
     */
    fun updateBlurLevel() {
        if (!_isScreenOn.value || isBlurAccumulationPaused) return
        
        val currentTime = System.currentTimeMillis()
        val timeOnScreen = if (blurStartTime > 0) {
            currentTime - blurStartTime
        } else 0L
        
        val maxBlurLevel = settingsManager.getMaxBlurLevel()
        
        // New logic: Start at 10%, increase by 10% every 10 seconds
        val secondsOnScreen = timeOnScreen / 1000f
        val intervalsCompleted = (secondsOnScreen / 10f).toInt() // Every 10 seconds
        val newBlurLevel = min(maxBlurLevel, 10f + (intervalsCompleted * 10f))
        
        _currentBlurLevel.value = newBlurLevel
    }
    
    /**
     * Starts the blur recovery process when screen is off
     */
    private fun startBlurRecovery() {
        // This will be handled by a coroutine in the service
        // For now, we just mark that recovery should start
    }
    
    /**
     * Stops the blur recovery process
     */
    private fun stopBlurRecovery() {
        // Stop any ongoing recovery
    }
    
    /**
     * Recovers blur level when screen is off
     */
    fun recoverBlur() {
        if (_isScreenOn.value) return
        
        val currentTime = System.currentTimeMillis()
        val timeOffScreen = if (lastScreenOffTime > 0) {
            currentTime - lastScreenOffTime
        } else 0L
        
        val blurRecoveryRate = settingsManager.getBlurRecoveryRate()
        val minBlurLevel = settingsManager.getMinBlurLevel()
        
        // Calculate blur recovery based on time off screen
        val blurDecrease = (timeOffScreen / (blurRecoveryRate * 60 * 1000f)) * 10f // 10% per interval
        val newBlurLevel = max(minBlurLevel, _currentBlurLevel.value - blurDecrease)
        
        _currentBlurLevel.value = newBlurLevel
        
        // Also reduce accumulated screen time proportionally
        val recoveryRatio = blurDecrease / _currentBlurLevel.value
        accumulatedScreenOnTime = max(0L, (accumulatedScreenOnTime * (1f - recoveryRatio)).toLong())
    }
    
    /**
     * Manually resets blur to minimum level
     */
    fun resetBlur() {
        _currentBlurLevel.value = settingsManager.getMinBlurLevel()
        accumulatedScreenOnTime = 0L
        lastScreenOnTime = 0L
        lastScreenOffTime = 0L
        blurStartTime = System.currentTimeMillis() // Reset blur start time
        isBlurAccumulationPaused = false
    }
    
    /**
     * Daily reset of blur level
     */
    fun dailyReset() {
        resetBlur()
    }
    
    /**
     * Boot reset of blur level
     */
    fun bootReset() {
        resetBlur()
    }
    
    /**
     * Pauses blur accumulation (for whitelisted apps)
     */
    fun pauseBlurAccumulation() {
        isBlurAccumulationPaused = true
    }
    
    /**
     * Resumes blur accumulation
     */
    fun resumeBlurAccumulation() {
        isBlurAccumulationPaused = false
        if (_isScreenOn.value) {
            // Reset blur start time to current time when resuming
            blurStartTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Gets the transition duration based on blur level change
     */
    fun getTransitionDuration(oldBlur: Float, newBlur: Float): Long {
        val blurChange = kotlin.math.abs(newBlur - oldBlur)
        return when {
            blurChange <= 10f -> 300L // Quick transition for small changes
            blurChange >= 90f -> 1500L // Slow transition for large changes
            else -> (300L + (blurChange / 10f) * 150L).toLong() // Proportional
        }
    }
}
