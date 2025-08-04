package com.focusfade.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import com.focusfade.app.manager.FocusStateManager
import com.focusfade.app.manager.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service that tracks screen on/off events and updates focus state
 */
class ScreenTimeTrackingService : Service() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var focusStateManager: FocusStateManager
    private lateinit var powerManager: PowerManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    handleScreenOn()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    handleScreenOff()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // User unlocked the device
                    handleUserPresent()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        settingsManager = SettingsManager(this)
        focusStateManager = FocusStateManager.getInstance(this, settingsManager)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        registerScreenReceiver()
        
        // Initialize screen state
        initializeScreenState()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        serviceScope.cancel()
    }
    
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }
    
    private fun unregisterScreenReceiver() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }
    
    private fun initializeScreenState() {
        serviceScope.launch {
            val isScreenOn = powerManager.isInteractive
            if (isScreenOn) {
                focusStateManager.onScreenOn()
            } else {
                focusStateManager.onScreenOff()
            }
        }
    }
    
    private fun handleScreenOn() {
        serviceScope.launch {
            focusStateManager.onScreenOn()
        }
    }
    
    private fun handleScreenOff() {
        serviceScope.launch {
            focusStateManager.onScreenOff()
        }
    }
    
    private fun handleUserPresent() {
        serviceScope.launch {
            // User has unlocked the device, ensure screen on state is set
            focusStateManager.onScreenOn()
        }
    }
    
    /**
     * Gets the current screen state
     */
    fun isScreenOn(): Boolean {
        return powerManager.isInteractive
    }
}
