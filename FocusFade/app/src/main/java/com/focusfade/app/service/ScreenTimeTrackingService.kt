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
