package com.focusfade.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.focusfade.app.MainActivity
import com.focusfade.app.R
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
    
    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "screen_time_tracking_service"
    }
    
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
        
        createNotificationChannel()
        registerScreenReceiver()
        
        // Initialize screen state
        initializeScreenState()
        // Start monitoring foreground apps continuously
        startForegroundAppMonitor()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
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
        serviceScope.launch handleScreenOnLaunch@{ // Labeled launch block
            val whitelistManager = com.focusfade.app.manager.WhitelistManager(applicationContext, settingsManager)
            val currentApp = whitelistManager.getCurrentForegroundApp()
            
            // Exclude FocusFade's own activities, especially DelayedLaunchActivity
            if (currentApp == packageName || currentApp == "com.focusfade.app.DelayedLaunchActivity") {
                focusStateManager.pauseBlurAccumulation() // Ensure blur doesn't accumulate on self
                focusStateManager.onScreenOn()
                return@handleScreenOnLaunch // Return from this specific launch block
            }
            
            val isSystemApp = currentApp != null && whitelistManager.isSystemApp(currentApp)
            val isWhitelisted = currentApp != null && settingsManager.getWhitelistedApps().contains(currentApp)
            
            if (isWhitelisted || isSystemApp) { // No delay for whitelisted or system apps
                focusStateManager.pauseBlurAccumulation()
                focusStateManager.onScreenOn()
            } else {
                focusStateManager.resumeBlurAccumulation()
                // Check if delayed launch is enabled and if this app is in the delayed launch list
                // New logic: All apps are whitelisted by default, only selected apps get delayed
                currentApp?.let {
                    val delayedLaunchApps = settingsManager.getDelayedLaunchApps()
                    if (settingsManager.isDelayedLaunchEnabled() && delayedLaunchApps.contains(it)) {
                        val appSpecificDelay = settingsManager.getAppSpecificDelay(it)
                        val delaySeconds = appSpecificDelay ?: settingsManager.getLaunchDelaySeconds()
                        
                        val launchIntent = Intent(applicationContext, com.focusfade.app.DelayedLaunchActivity::class.java)
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        launchIntent.putExtra("TARGET_PACKAGE", it)
                        launchIntent.putExtra("DELAY_SECONDS", delaySeconds) // Pass the delay to DelayedLaunchActivity
                        applicationContext.startActivity(launchIntent)
                    }
                }
            }
        }
    }
    
    private fun handleScreenOff() {
        serviceScope.launch {
            focusStateManager.onScreenOff()
        }
    }
    
    private fun handleUserPresent() {
        serviceScope.launch {
            val whitelistManager = com.focusfade.app.manager.WhitelistManager(applicationContext, settingsManager)
            val currentApp = whitelistManager.getCurrentForegroundApp()
            val isWhitelisted = currentApp != null && settingsManager.getWhitelistedApps().contains(currentApp)
            if (isWhitelisted) {
                focusStateManager.pauseBlurAccumulation()
            } else {
                focusStateManager.resumeBlurAccumulation()
            }
            focusStateManager.onScreenOn()
        }
    }

    private fun startForegroundAppMonitor() {
        serviceScope.launch startForegroundAppMonitorLaunch@{ // Labeled launch block
            var lastApp: String? = null
            val whitelistManager = com.focusfade.app.manager.WhitelistManager(applicationContext, settingsManager)

            while (true) {
                val currentApp = whitelistManager.getCurrentForegroundApp()
                
                // Exclude FocusFade's own activities, especially DelayedLaunchActivity
                if (currentApp == packageName || currentApp == "com.focusfade.app.DelayedLaunchActivity") {
                    lastApp = currentApp // Update lastApp to prevent re-triggering on self
                    kotlinx.coroutines.delay(1000)
                    continue // Skip processing for self or DelayedLaunchActivity
                }

                if (currentApp != null && currentApp != lastApp) {
                    lastApp = currentApp
                    
                    // New logic: Check if delayed launch is enabled and if this app is in the delayed launch list
                    // All apps are whitelisted by default, only selected apps get delayed
                    val delayedLaunchApps = settingsManager.getDelayedLaunchApps()
                    if (settingsManager.isDelayedLaunchEnabled() && 
                        delayedLaunchApps.contains(currentApp)) { // Apply delay only if enabled and app is in the delayed launch list
                        val appSpecificDelay = settingsManager.getAppSpecificDelay(currentApp)
                        val delaySeconds = appSpecificDelay ?: settingsManager.getLaunchDelaySeconds()
                        
                        // Start delayed launch activity overlay
                        val launchIntent = Intent(applicationContext, com.focusfade.app.DelayedLaunchActivity::class.java)
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        launchIntent.putExtra("TARGET_PACKAGE", currentApp)
                        launchIntent.putExtra("DELAY_SECONDS", delaySeconds) // Pass the delay to DelayedLaunchActivity
                        applicationContext.startActivity(launchIntent)
                    }
                }
                kotlinx.coroutines.delay(1000) // check once per second
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Time Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks screen on/off events"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFade Screen Tracking")
            .setContentText("Monitoring screen events")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    /**
     * Gets the current screen state
     */
    fun isScreenOn(): Boolean {
        return powerManager.isInteractive
    }
}
