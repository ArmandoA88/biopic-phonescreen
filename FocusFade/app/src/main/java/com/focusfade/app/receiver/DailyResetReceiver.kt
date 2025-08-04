package com.focusfade.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusfade.app.manager.FocusStateManager
import com.focusfade.app.manager.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles daily reset alarms
 */
class DailyResetReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_DAILY_RESET = "com.focusfade.app.DAILY_RESET"
    }
    
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DAILY_RESET -> {
                handleDailyReset(context)
            }
        }
    }
    
    private fun handleDailyReset(context: Context) {
        receiverScope.launch {
            try {
                val settingsManager = SettingsManager(context)
                val focusStateManager = FocusStateManager.getInstance(context, settingsManager)
                
                // Perform daily reset
                focusStateManager.dailyReset()
                
                // Schedule next daily reset
                DailyResetScheduler.scheduleDailyReset(context, settingsManager)
                
            } catch (e: Exception) {
                // Handle any errors during daily reset
            }
        }
    }
}
