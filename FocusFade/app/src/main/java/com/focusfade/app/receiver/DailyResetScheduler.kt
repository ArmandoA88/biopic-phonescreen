package com.focusfade.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusfade.app.manager.SettingsManager
import java.util.Calendar

/**
 * Schedules daily reset alarms using AlarmManager
 */
object DailyResetScheduler {
    
    private const val DAILY_RESET_REQUEST_CODE = 1000
    
    /**
     * Schedules the next daily reset alarm
     */
    fun scheduleDailyReset(context: Context, settingsManager: SettingsManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val resetHour = settingsManager.getDailyResetHour()
        val resetMinute = settingsManager.getDailyResetMinute()
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, resetHour)
            set(Calendar.MINUTE, resetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val intent = Intent(context, DailyResetReceiver::class.java).apply {
            action = DailyResetReceiver.ACTION_DAILY_RESET
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Cancel any existing alarm
            alarmManager.cancel(pendingIntent)
            
            // Schedule new alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            // Handle scheduling errors
        }
    }
    
    /**
     * Cancels the daily reset alarm
     */
    fun cancelDailyReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, DailyResetReceiver::class.java).apply {
            action = DailyResetReceiver.ACTION_DAILY_RESET
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // Handle cancellation errors
        }
    }
    
    /**
     * Reschedules daily reset when settings change
     */
    fun rescheduleDailyReset(context: Context, settingsManager: SettingsManager) {
        cancelDailyReset(context)
        scheduleDailyReset(context, settingsManager)
    }
    
    /**
     * Gets the next scheduled reset time
     */
    fun getNextResetTime(settingsManager: SettingsManager): Long {
        val resetHour = settingsManager.getDailyResetHour()
        val resetMinute = settingsManager.getDailyResetMinute()
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, resetHour)
            set(Calendar.MINUTE, resetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        return calendar.timeInMillis
    }
    
    /**
     * Formats the next reset time for display
     */
    fun getNextResetTimeFormatted(settingsManager: SettingsManager): String {
        val nextResetTime = getNextResetTime(settingsManager)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nextResetTime
        }
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val isToday = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
        
        val timeString = String.format("%02d:%02d", hour, minute)
        val dayString = if (isToday) "Today" else "Tomorrow"
        
        return "$dayString at $timeString"
    }
}
