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

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val permissionIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                permissionIntent.putExtra("REQUEST_NOTIFICATION_PERMISSION", true)
                startActivity(permissionIntent)
            }
        }
        
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
            "SET_OVERLAY_MODE" -> {
                val mode = intent.getIntExtra("mode", 0)
                setOverlayMode(mode)
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

        // For notifications, RemoteViews can't contain custom views, so we replace with simple icon and text
        val remoteViews = android.widget.RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "Blur: ${(if (isManualBlurMode) manualBlurLevel else focusStateManager.currentBlurLevel.value).toInt()}%")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFade Active")
            .setContentText("Blur level: ${(if (isManualBlurMode) manualBlurLevel else focusStateManager.currentBlurLevel.value).toInt()}%")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_refresh, "Reset", resetPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    // Adds support for blur overlay, color shift/dim overlay, and overlay patterns / timers
    private var colorShiftOverlayView: View? = null
    private var patternOverlayView: View? = null
    private var timerWarningOverlayView: View? = null

    private fun setupBlurOverlay() {
        blurOverlayView = BlurOverlayView(this)

        // Color shift overlay setup
        colorShiftOverlayView = object : View(this) {
            private val paint = android.graphics.Paint()
            private val matrix = android.graphics.ColorMatrix()
            private val filter = android.graphics.ColorMatrixColorFilter(matrix)

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                paint.color = Color.argb(100, 0, 0, 0) // dim
                paint.colorFilter = filter
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            fun setIntensity(percent: Float, grayscale: Boolean = true) {
                val g = percent.coerceIn(0f, 1f)
                if (grayscale) {
                    matrix.setSaturation(1f - g)
                }
                paint.color = Color.argb((g * 150).toInt(), 0, 0, 0)
                invalidate()
            }
        }

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
    
// Updated to support showing either blur or color shift overlay
    private var useColorShift = false

    private fun showOverlay() {
        if (!isOverlayShown && overlayParams != null) {
            try {
                if (useColorShift && colorShiftOverlayView != null) {
                    if (colorShiftOverlayView?.parent == null) {
                        windowManager.addView(colorShiftOverlayView, overlayParams)
                    }
                } else {
                    if (blurOverlayView.parent == null) {
                        windowManager.addView(blurOverlayView, overlayParams)
                    }
                }
                isOverlayShown = true
            } catch (e: Exception) {
                // Handle overlay permission issues
            }
        }
    }
    
    // Updated to support removing correct overlay type
    // Overlay mode selector: 0 - Blur, 1 - Color Shift/Dim, 2 - Pattern, 3 - Timer Warning
    private var overlayMode: Int = 0

    private fun hideOverlay() {
        if (isOverlayShown) {
            try {
                when (overlayMode) {
                    1 -> if (colorShiftOverlayView?.parent != null) {
                        windowManager.removeView(colorShiftOverlayView)
                    }
                    2 -> if (patternOverlayView?.parent != null) {
                        windowManager.removeView(patternOverlayView)
                    }
                    else -> if (blurOverlayView.parent != null) {
                        windowManager.removeView(blurOverlayView)
                    }
                }
                isOverlayShown = false
            } catch (e: Exception) {
                // View might already be removed
            }
        }
    }

    fun setOverlayMode(mode: Int) {
        overlayMode = mode.coerceIn(0, 3)
        hideOverlay()
        showOverlay()
    }

    fun triggerTimerWarning(seconds: Int) {
        overlayMode = 3
        if (timerWarningOverlayView != null) {
            val timerView = timerWarningOverlayView
            if (timerView?.parent == null) {
                windowManager.addView(timerView, overlayParams)
            }
            // Try to call setCountdown directly if matches our anonymous View
            try {
                val method = timerView?.javaClass?.getMethod("setCountdown", Int::class.javaPrimitiveType)
                method?.invoke(timerView, seconds)
            } catch (e: Exception) {
                // fallback to invalidate if no method
                timerView?.invalidate()
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
                        
                        val currentApp = whitelistManager.getCurrentForegroundApp()
                        val whitelistedApps = settingsManager.getWhitelistedApps()
                        val isWhitelisted = currentApp != null && whitelistedApps.contains(currentApp)
                        
                        CrashLogger.log("DEBUG", TAG, "Current app: $currentApp, Is whitelisted: $isWhitelisted")
                        
                        if (isWhitelisted) {
                            focusStateManager.pauseBlurAccumulation()
                            // Hide overlay when whitelisted app is active
                            if (isOverlayShown) {
                                hideOverlay()
                            }
                        } else {
                            focusStateManager.resumeBlurAccumulation()
                            // Show overlay when non-whitelisted app is active
                            if (!isOverlayShown) {
                                showOverlay()
                            }
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
        // Generate dynamic icon bitmap with text
        val size = 64
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = Color.RED
            textSize = 28f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        // Draw circle background
        val bgPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.1f, bgPaint)

        // Draw outer white stroke for visibility
        val strokePaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.1f - 1.5f, strokePaint)

        // Draw percentage text large & centered
        val percentText = "${blurLevel.toInt()}"
        paint.textSize = 42f
        paint.color = Color.RED
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(percentText, 0, percentText.length, textBounds)
        val textY = size / 2f + textBounds.height() / 2f - 2
        canvas.drawText(percentText, size / 2f, textY, paint)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFade Active")
            .setContentText("Blur level: ${blurLevel.toInt()}%")
            .setSmallIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap))
            .setOngoing(true)
            .setSilent(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun setupBlurControlBar() {
        // Hourglass bar removed - no longer needed
    }
    
    private fun createBlurControlBar(): View {
        // Replace the old bar design with our HourglassProgressView
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
        }

        // Create a layout with Hourglass on top and TextView below
        val verticalLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val hourglassView = com.focusfade.app.view.HourglassProgressView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(70, 70)
        }

        val percentText = android.widget.TextView(this).apply {
            text = "0%"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        verticalLayout.addView(hourglassView)
        verticalLayout.addView(percentText)

        // Allow dragging anywhere on screen and adjust manual blur level by dragging vertically
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = controlBarParams?.x ?: 0
                        initialY = controlBarParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        controlBarParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        controlBarParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(blurControlBar, controlBarParams)

                        // Adjust blur based on vertical drag position
                        val screenHeight = resources.displayMetrics.heightPixels
                        val percent = 100f - ((controlBarParams?.y?.toFloat()?.coerceIn(0f, screenHeight.toFloat()) ?: 0f) / screenHeight * 100f)
                        isManualBlurMode = true
                        setManualBlurLevel(percent)
                        return true
                    }
                }
                return false
            }
        })

        container.addView(verticalLayout)

        container.tag = mapOf(
            "hourglassView" to hourglassView,
            "percentText" to percentText
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

        val hourglassView = components["hourglassView"] as? com.focusfade.app.view.HourglassProgressView

        val currentBlur = if (isManualBlurMode) manualBlurLevel else focusStateManager.currentBlurLevel.value

        // Update Hourglass animation
        hourglassView?.setProgress(currentBlur)

        // Update percentage text if present
        val percentText = components["percentText"] as? android.widget.TextView
        percentText?.text = "${currentBlur.toInt()}%"
    }
}
