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
import android.util.Log
import com.focusfade.app.utils.CrashLogger

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
        const val ACTION_TOGGLE_MANUAL_BLUR = "TOGGLE_MANUAL_BLUR"
        const val ACTION_SET_MANUAL_BLUR = "SET_MANUAL_BLUR"
        const val EXTRA_BLUR_LEVEL = "blur_level"
        
        private const val UPDATE_INTERVAL = 1000L // 1 second
        private const val TAG = "BlurOverlayService"
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var blurOverlayView: BlurOverlayView
    private lateinit var blurControlBar: View
    private lateinit var settingsManager: SettingsManager
    private lateinit var focusStateManager: FocusStateManager
    private lateinit var whitelistManager: WhitelistManager
    
    private var overlayParams: WindowManager.LayoutParams? = null
    private var controlBarParams: WindowManager.LayoutParams? = null
    private var isOverlayShown = false
    private var isControlBarShown = false
    private var isManualBlurMode = false
    private var manualBlurLevel = 0f
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    private var whitelistCheckJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        CrashLogger.log("DEBUG", TAG, "Service onCreate() started")
        
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            CrashLogger.log("DEBUG", TAG, "WindowManager initialized")
            
            settingsManager = SettingsManager(this)
            CrashLogger.log("DEBUG", TAG, "SettingsManager initialized")
            
            focusStateManager = FocusStateManager.getInstance(this, settingsManager)
            CrashLogger.log("DEBUG", TAG, "FocusStateManager initialized")
            
            whitelistManager = WhitelistManager(this, settingsManager)
            CrashLogger.log("DEBUG", TAG, "WhitelistManager initialized")
            
            createNotificationChannel()
            CrashLogger.log("DEBUG", TAG, "Notification channel created")
            
            setupBlurOverlay()
            CrashLogger.log("DEBUG", TAG, "Blur overlay setup complete")
            
            // Initialize asynchronously to avoid blocking
            serviceScope.launch {
                try {
                    CrashLogger.log("DEBUG", TAG, "Starting monitoring...")
                    startMonitoring()
                    CrashLogger.log("DEBUG", TAG, "Monitoring started successfully")
                } catch (e: Exception) {
                    CrashLogger.log("ERROR", TAG, "Error starting monitoring", e)
                }
            }
            
            CrashLogger.log("DEBUG", TAG, "Service onCreate() completed successfully")
        } catch (e: Exception) {
            CrashLogger.log("ERROR", TAG, "Critical error in onCreate()", e)
            throw e
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(NOTIFICATION_ID, createNotification())
                showOverlay()
                setupBlurControlBar()
                showControlBar()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_RESET_BLUR -> {
                focusStateManager.resetBlur()
                isManualBlurMode = false
                manualBlurLevel = 0f
            }
            ACTION_TOGGLE_MANUAL_BLUR -> {
                toggleManualBlurMode()
            }
            ACTION_SET_MANUAL_BLUR -> {
                val blurLevel = intent.getFloatExtra(EXTRA_BLUR_LEVEL, 0f)
                setManualBlurLevel(blurLevel)
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel all jobs first
        updateJob?.cancel()
        whitelistCheckJob?.cancel()
        updateJob = null
        whitelistCheckJob = null
        
        // Hide overlays
        hideOverlay()
        hideControlBar()
        
        // Cancel the entire scope
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
        CrashLogger.log("DEBUG", TAG, "startMonitoring() called")
        
        // Monitor blur level changes and update overlay
        updateJob = serviceScope.launch {
            try {
                CrashLogger.log("DEBUG", TAG, "Starting blur level collection...")
                focusStateManager.currentBlurLevel.collect { blurLevel ->
                    try {
                        CrashLogger.log("VERBOSE", TAG, "Blur level update: $blurLevel")
                        if (!isManualBlurMode) {
                            blurOverlayView.updateBlurLevel(blurLevel)
                        }
                        updateNotification(if (isManualBlurMode) manualBlurLevel else blurLevel)
                        updateControlBarUI()
                    } catch (e: Exception) {
                        CrashLogger.log("ERROR", TAG, "Error in blur level update", e)
                    }
                }
            } catch (e: Exception) {
                CrashLogger.log("ERROR", TAG, "Error in blur level collection", e)
            }
        }
        
        // Monitor whitelisted app status and update blur level
        whitelistCheckJob = serviceScope.launch {
            try {
                CrashLogger.log("DEBUG", TAG, "Starting whitelist monitoring loop...")
                var loopCount = 0
                while (true) {
                    try {
                        loopCount++
                        CrashLogger.log("VERBOSE", TAG, "Whitelist check loop iteration: $loopCount")
                        
                        whitelistManager.updateForegroundAppStatus()
                        
                        if (whitelistManager.isCurrentAppWhitelisted()) {
                            focusStateManager.pauseBlurAccumulation()
                        } else {
                            focusStateManager.resumeBlurAccumulation()
                        }
                        
                        // Always update blur level to ensure it increases every 10 seconds
                        focusStateManager.updateBlurLevel()
                        
                        // Log every 60 iterations (1 minute)
                        if (loopCount % 60 == 0) {
                            CrashLogger.log("DEBUG", TAG, "Whitelist monitoring running - iteration $loopCount")
                        }
                    } catch (e: Exception) {
                        CrashLogger.log("ERROR", TAG, "Error in whitelist check iteration $loopCount", e)
                    }
                    
                    delay(UPDATE_INTERVAL)
                }
            } catch (e: Exception) {
                CrashLogger.log("ERROR", TAG, "Critical error in whitelist monitoring loop", e)
            }
        }
        
        // Monitor screen state and blur recovery
        serviceScope.launch {
            try {
                CrashLogger.log("DEBUG", TAG, "Starting screen state monitoring...")
                combine(
                    focusStateManager.isScreenOn,
                    whitelistManager.isWhitelistedAppActive
                ) { isScreenOn, isWhitelistedActive ->
                    CrashLogger.log("VERBOSE", TAG, "Screen state: $isScreenOn, Whitelisted active: $isWhitelistedActive")
                    if (!isScreenOn) {
                        // Start blur recovery when screen is off
                        launch {
                            try {
                                CrashLogger.log("DEBUG", TAG, "Starting blur recovery...")
                                while (!focusStateManager.isScreenOn.value) {
                                    focusStateManager.recoverBlur()
                                    delay(UPDATE_INTERVAL)
                                }
                                CrashLogger.log("DEBUG", TAG, "Blur recovery stopped - screen is on")
                            } catch (e: Exception) {
                                CrashLogger.log("ERROR", TAG, "Error in blur recovery", e)
                            }
                        }
                    }
                }.collect { }
            } catch (e: Exception) {
                CrashLogger.log("ERROR", TAG, "Error in screen state monitoring", e)
            }
        }
        
        CrashLogger.log("DEBUG", TAG, "All monitoring jobs started")
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
    
    private fun setupBlurControlBar() {
        blurControlBar = createBlurControlBar()
        
        controlBarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Position from top
        }
    }
    
    private fun createBlurControlBar(): View {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(32, 16, 32, 16)
        }
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Manual blur toggle button
        val toggleButton = android.widget.Button(this).apply {
            text = "Manual Blur: OFF"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF4444"))
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                toggleManualBlurMode()
            }
        }
        
        // Blur level slider
        val slider = android.widget.SeekBar(this).apply {
            max = 100
            progress = 0
            setPadding(32, 0, 32, 0)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isManualBlurMode) {
                        setManualBlurLevel(progress.toFloat())
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        
        // Blur level text
        val blurText = android.widget.TextView(this).apply {
            text = "0%"
            setTextColor(Color.WHITE)
            textSize = 16f
            minWidth = 100
            gravity = Gravity.CENTER
        }
        
        layout.addView(toggleButton)
        layout.addView(slider, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(blurText)
        
        container.addView(layout)
        
        // Store references for updates
        container.tag = mapOf(
            "toggleButton" to toggleButton,
            "slider" to slider,
            "blurText" to blurText
        )
        
        return container
    }
    
    private fun showControlBar() {
        if (!isControlBarShown && controlBarParams != null) {
            try {
                windowManager.addView(blurControlBar, controlBarParams)
                isControlBarShown = true
            } catch (e: Exception) {
                // Handle overlay permission issues
            }
        }
    }
    
    private fun hideControlBar() {
        if (isControlBarShown) {
            try {
                windowManager.removeView(blurControlBar)
                isControlBarShown = false
            } catch (e: Exception) {
                // View might already be removed
            }
        }
    }
    
    private fun toggleManualBlurMode() {
        isManualBlurMode = !isManualBlurMode
        updateControlBarUI()
        
        if (isManualBlurMode) {
            // Pause automatic blur updates
            focusStateManager.pauseBlurAccumulation()
        } else {
            // Resume automatic blur updates
            focusStateManager.resumeBlurAccumulation()
            manualBlurLevel = 0f
        }
    }
    
    private fun setManualBlurLevel(blurLevel: Float) {
        if (isManualBlurMode) {
            manualBlurLevel = blurLevel.coerceIn(0f, 100f)
            blurOverlayView.updateBlurLevel(manualBlurLevel, animate = false)
            updateControlBarUI()
        }
    }
    
    private fun updateControlBarUI() {
        // Check if blurControlBar is initialized
        if (!::blurControlBar.isInitialized) {
            return
        }
        
        val components = blurControlBar.tag as? Map<String, View> ?: return
        
        val toggleButton = components["toggleButton"] as? android.widget.Button
        val slider = components["slider"] as? android.widget.SeekBar
        val blurText = components["blurText"] as? android.widget.TextView
        
        toggleButton?.apply {
            text = if (isManualBlurMode) "Manual Blur: ON" else "Manual Blur: OFF"
            setBackgroundColor(if (isManualBlurMode) Color.parseColor("#44FF44") else Color.parseColor("#FF4444"))
        }
        
        slider?.apply {
            isEnabled = isManualBlurMode
            if (isManualBlurMode) {
                progress = manualBlurLevel.toInt()
            }
        }
        
        val currentBlur = if (isManualBlurMode) manualBlurLevel else focusStateManager.currentBlurLevel.value
        blurText?.text = "${currentBlur.toInt()}%"
    }
}
