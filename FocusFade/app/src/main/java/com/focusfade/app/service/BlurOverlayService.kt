package com.focusfade.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.focusfade.app.MainActivity
import com.focusfade.app.R
import com.focusfade.app.manager.FocusStateManager
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.manager.WhitelistManager
import com.focusfade.app.view.BlurOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that manages the blur overlay
 */
class BlurOverlayService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "focus_fade_service"
        const val ACTION_START_SERVICE = "START_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_RESET_BLUR = "RESET_BLUR"
        
        private const val UPDATE_INTERVAL = 1000L // 1 second
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var blurOverlayView: BlurOverlayView
    private lateinit var settingsManager: SettingsManager
    private lateinit var focusStateManager: FocusStateManager
    private lateinit var whitelistManager: WhitelistManager
    
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isOverlayShown = false
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    private var whitelistCheckJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsManager = SettingsManager(this)
        focusStateManager = FocusStateManager.getInstance(this, settingsManager)
        whitelistManager = WhitelistManager(this, settingsManager)
        
        createNotificationChannel()
        setupBlurOverlay()
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(NOTIFICATION_ID, createNotification())
                showOverlay()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_RESET_BLUR -> {
                focusStateManager.resetBlur()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusFade Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manages screen blur overlay"
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
        
        val resetIntent = Intent(this, BlurOverlayService::class.java).apply {
            action = ACTION_RESET_BLUR
        }
        val resetPendingIntent = PendingIntent.getService(
            this, 1, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, BlurOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFade Active")
            .setContentText("Monitoring screen time")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_refresh, "Reset", resetPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun setupBlurOverlay() {
        blurOverlayView = BlurOverlayView(this)
        
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
    
    private fun showOverlay() {
        if (!isOverlayShown && overlayParams != null) {
            try {
                windowManager.addView(blurOverlayView, overlayParams)
                isOverlayShown = true
            } catch (e: Exception) {
                // Handle overlay permission issues
            }
        }
    }
    
    private fun hideOverlay() {
        if (isOverlayShown) {
            try {
                windowManager.removeView(blurOverlayView)
                isOverlayShown = false
            } catch (e: Exception) {
                // View might already be removed
            }
        }
    }
    
    private fun startMonitoring() {
        // Monitor blur level changes and update overlay
        updateJob = serviceScope.launch {
            focusStateManager.currentBlurLevel.collect { blurLevel ->
                blurOverlayView.updateBlurLevel(blurLevel)
                updateNotification(blurLevel)
            }
        }
        
        // Monitor whitelisted app status
        whitelistCheckJob = serviceScope.launch {
            while (true) {
                whitelistManager.updateForegroundAppStatus()
                
                if (whitelistManager.isCurrentAppWhitelisted()) {
                    focusStateManager.pauseBlurAccumulation()
                } else {
                    focusStateManager.resumeBlurAccumulation()
                    focusStateManager.updateBlurLevel()
                }
                
                delay(UPDATE_INTERVAL)
            }
        }
        
        // Monitor screen state and blur recovery
        serviceScope.launch {
            combine(
                focusStateManager.isScreenOn,
                whitelistManager.isWhitelistedAppActive
            ) { isScreenOn, isWhitelistedActive ->
                if (!isScreenOn) {
                    // Start blur recovery when screen is off
                    launch {
                        while (!focusStateManager.isScreenOn.value) {
                            focusStateManager.recoverBlur()
                            delay(UPDATE_INTERVAL)
                        }
                    }
                }
            }.collect { }
        }
    }
    
    private fun updateNotification(blurLevel: Float) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFade Active")
            .setContentText("Blur level: ${blurLevel.toInt()}%")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
