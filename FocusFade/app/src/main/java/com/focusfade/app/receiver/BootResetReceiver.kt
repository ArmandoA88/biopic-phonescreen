package com.focusfade.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusfade.app.manager.FocusStateManager
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.service.BlurOverlayService
import com.focusfade.app.service.ScreenTimeTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles device boot events and resets blur state
 */
class BootResetReceiver : BroadcastReceiver() {
    
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                handleBootCompleted(context)
            }
        }
    }
    
    private fun handleBootCompleted(context: Context) {
        receiverScope.launch {
            try {
                val settingsManager = SettingsManager(context)
                val focusStateManager = FocusStateManager.getInstance(context, settingsManager)
                
                // Reset blur state on boot
                focusStateManager.bootReset()
                
                // Start services if enabled
                if (settingsManager.isServiceEnabled()) {
                    startServices(context)
                }
                
            } catch (e: Exception) {
                // Handle any errors during boot initialization
            }
        }
    }
    
    private fun startServices(context: Context) {
        try {
            // Start screen time tracking service
            val screenTrackingIntent = Intent(context, ScreenTimeTrackingService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(screenTrackingIntent)
            } else {
                context.startService(screenTrackingIntent)
            }
            
            // Start blur overlay service
            val blurServiceIntent = Intent(context, BlurOverlayService::class.java).apply {
                action = BlurOverlayService.ACTION_START_SERVICE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(blurServiceIntent)
            } else {
                context.startService(blurServiceIntent)
            }
            
        } catch (e: Exception) {
            // Handle service start errors
        }
    }
}
