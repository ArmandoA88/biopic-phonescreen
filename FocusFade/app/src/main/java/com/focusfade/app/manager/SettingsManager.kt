package com.focusfade.app.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import android.util.Log

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_fade_settings")

/**
 * Manages app settings using DataStore
 */
class SettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "SettingsManager"
        
        // Settings keys
        private val BLUR_GAIN_RATE = intPreferencesKey("blur_gain_rate") // minutes per 10% blur
        private val BLUR_RECOVERY_RATE = intPreferencesKey("blur_recovery_rate") // minutes per 10% recovery
        private val MIN_BLUR_LEVEL = floatPreferencesKey("min_blur_level") // 0-100%
        private val MAX_BLUR_LEVEL = floatPreferencesKey("max_blur_level") // 0-100%
        private val DAILY_RESET_HOUR = intPreferencesKey("daily_reset_hour") // 0-23
        private val DAILY_RESET_MINUTE = intPreferencesKey("daily_reset_minute") // 0-59
        private val WHITELISTED_APPS = stringSetPreferencesKey("whitelisted_apps")
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val LAUNCH_DELAY_SECONDS = intPreferencesKey("launch_delay_seconds")
        private val DELAYED_LAUNCH_ENABLED = booleanPreferencesKey("delayed_launch_enabled")
        private val APP_SPECIFIC_DELAYS = stringSetPreferencesKey("app_specific_delays") // Stores "packageName:delaySeconds"
        private val DELAYED_LAUNCH_APPS = stringSetPreferencesKey("delayed_launch_apps") // Apps that have delayed launch enabled
        
        // Default values
        const val DEFAULT_BLUR_GAIN_RATE = 10 // 10% every 10 minutes
        const val DEFAULT_BLUR_RECOVERY_RATE = 10 // 10% recovery every 10 minutes
        const val DEFAULT_MIN_BLUR_LEVEL = 0f
        const val DEFAULT_MAX_BLUR_LEVEL = 100f
        const val DEFAULT_DAILY_RESET_HOUR = 0 // midnight
        const val DEFAULT_DAILY_RESET_MINUTE = 0
        const val DEFAULT_LAUNCH_DELAY_SECONDS = 5 // Default delay of 5 seconds
        const val DEFAULT_DELAYED_LAUNCH_ENABLED = false
    }
    
    // Launch delay (in seconds)
    fun getLaunchDelaySeconds(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[LAUNCH_DELAY_SECONDS] ?: DEFAULT_LAUNCH_DELAY_SECONDS
                }.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLaunchDelaySeconds()", e)
            DEFAULT_LAUNCH_DELAY_SECONDS
        }
    }
    
    suspend fun setLaunchDelaySeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[LAUNCH_DELAY_SECONDS] = seconds
        }
    }
    
    fun getLaunchDelaySecondsFlow(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LAUNCH_DELAY_SECONDS] ?: DEFAULT_LAUNCH_DELAY_SECONDS
    }
    
    // Delayed launch enabled state
    fun isDelayedLaunchEnabled(): Boolean {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[DELAYED_LAUNCH_ENABLED] ?: DEFAULT_DELAYED_LAUNCH_ENABLED
                }.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in isDelayedLaunchEnabled()", e)
            DEFAULT_DELAYED_LAUNCH_ENABLED
        }
    }
    
    suspend fun setDelayedLaunchEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DELAYED_LAUNCH_ENABLED] = enabled
        }
    }
    
    fun getDelayedLaunchEnabledFlow(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DELAYED_LAUNCH_ENABLED] ?: DEFAULT_DELAYED_LAUNCH_ENABLED
    }
    
    // App-specific delays
    fun getAppSpecificDelay(packageName: String): Int? {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    val delays = preferences[APP_SPECIFIC_DELAYS] ?: emptySet()
                    delays.firstOrNull { it.startsWith("$packageName:") }?.split(":")?.get(1)?.toIntOrNull()
                }.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAppSpecificDelay() for $packageName", e)
            null
        }
    }
    
    suspend fun setAppSpecificDelay(packageName: String, delaySeconds: Int) {
        context.dataStore.edit { preferences ->
            val currentDelays = preferences[APP_SPECIFIC_DELAYS] ?: emptySet()
            val newDelays = currentDelays.filter { !it.startsWith("$packageName:") }.toMutableSet()
            newDelays.add("$packageName:$delaySeconds")
            preferences[APP_SPECIFIC_DELAYS] = newDelays
        }
    }
    
    suspend fun removeAppSpecificDelay(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentDelays = preferences[APP_SPECIFIC_DELAYS] ?: emptySet()
            preferences[APP_SPECIFIC_DELAYS] = currentDelays.filter { !it.startsWith("$packageName:") }.toSet()
        }
    }
    
    fun getAllAppSpecificDelays(): Map<String, Int> {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    val delays = preferences[APP_SPECIFIC_DELAYS] ?: emptySet()
                    delays.mapNotNull {
                        val parts = it.split(":")
                        if (parts.size == 2) {
                            val delay = parts[1].toIntOrNull()
                            if (delay != null) parts[0] to delay
                            else null
                        } else null
                    }.toMap()
                }.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAllAppSpecificDelays()", e)
            emptyMap()
        }
    }
    
    fun getAllAppSpecificDelaysFlow(): Flow<Map<String, Int>> = context.dataStore.data.map { preferences ->
        val delays = preferences[APP_SPECIFIC_DELAYS] ?: emptySet()
        delays.mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                val delay = parts[1].toIntOrNull()
                if (delay != null) parts[0] to delay
                else null
            } else null
        }.toMap()
    }
    
    // Removed the filterNotNullValues helper function as it's no longer needed.
    
    // Blur gain rate (minutes per 10% blur increase)
    fun getBlurGainRate(): Int {
        return try {
            Log.v(TAG, "getBlurGainRate() called")
            val result = runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[BLUR_GAIN_RATE] ?: DEFAULT_BLUR_GAIN_RATE
                }.first()
            }
            Log.v(TAG, "getBlurGainRate() returning: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in getBlurGainRate()", e)
            DEFAULT_BLUR_GAIN_RATE
        }
    }
    
    suspend fun setBlurGainRate(rate: Int) {
        context.dataStore.edit { preferences ->
            preferences[BLUR_GAIN_RATE] = rate
        }
    }
    
    fun getBlurGainRateFlow(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[BLUR_GAIN_RATE] ?: DEFAULT_BLUR_GAIN_RATE
    }
    
    // Blur recovery rate (minutes per 10% blur decrease)
    fun getBlurRecoveryRate(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[BLUR_RECOVERY_RATE] ?: DEFAULT_BLUR_RECOVERY_RATE
                }.first()
            }
        } catch (e: Exception) {
            DEFAULT_BLUR_RECOVERY_RATE
        }
    }
    
    suspend fun setBlurRecoveryRate(rate: Int) {
        context.dataStore.edit { preferences ->
            preferences[BLUR_RECOVERY_RATE] = rate
        }
    }
    
    fun getBlurRecoveryRateFlow(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[BLUR_RECOVERY_RATE] ?: DEFAULT_BLUR_RECOVERY_RATE
    }
    
    // Minimum blur level
    fun getMinBlurLevel(): Float {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[MIN_BLUR_LEVEL] ?: DEFAULT_MIN_BLUR_LEVEL
                }.first()
            }
        } catch (e: Exception) {
            DEFAULT_MIN_BLUR_LEVEL
        }
    }
    
    suspend fun setMinBlurLevel(level: Float) {
        context.dataStore.edit { preferences ->
            preferences[MIN_BLUR_LEVEL] = level.coerceIn(0f, 100f)
        }
    }
    
    fun getMinBlurLevelFlow(): Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MIN_BLUR_LEVEL] ?: DEFAULT_MIN_BLUR_LEVEL
    }
    
    // Maximum blur level
    fun getMaxBlurLevel(): Float {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[MAX_BLUR_LEVEL] ?: DEFAULT_MAX_BLUR_LEVEL
                }.first()
            }
        } catch (e: Exception) {
            DEFAULT_MAX_BLUR_LEVEL
        }
    }
    
    suspend fun setMaxBlurLevel(level: Float) {
        context.dataStore.edit { preferences ->
            preferences[MAX_BLUR_LEVEL] = level.coerceIn(0f, 100f)
        }
    }
    
    fun getMaxBlurLevelFlow(): Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[MAX_BLUR_LEVEL] ?: DEFAULT_MAX_BLUR_LEVEL
    }
    
    // Daily reset time
    fun getDailyResetHour(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[DAILY_RESET_HOUR] ?: DEFAULT_DAILY_RESET_HOUR
                }.first()
            }
        } catch (e: Exception) {
            DEFAULT_DAILY_RESET_HOUR
        }
    }
    
    fun getDailyResetMinute(): Int {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[DAILY_RESET_MINUTE] ?: DEFAULT_DAILY_RESET_MINUTE
                }.first()
            }
        } catch (e: Exception) {
            DEFAULT_DAILY_RESET_MINUTE
        }
    }
    
    suspend fun setDailyResetTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_RESET_HOUR] = hour.coerceIn(0, 23)
            preferences[DAILY_RESET_MINUTE] = minute.coerceIn(0, 59)
        }
    }
    
    fun getDailyResetTimeFlow(): Flow<Pair<Int, Int>> = context.dataStore.data.map { preferences ->
        val hour = preferences[DAILY_RESET_HOUR] ?: DEFAULT_DAILY_RESET_HOUR
        val minute = preferences[DAILY_RESET_MINUTE] ?: DEFAULT_DAILY_RESET_MINUTE
        Pair(hour, minute)
    }
    
    // Whitelisted apps
    fun getWhitelistedApps(): Set<String> {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[WHITELISTED_APPS] ?: emptySet()
                }.first()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    suspend fun setWhitelistedApps(apps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[WHITELISTED_APPS] = apps
        }
    }
    
    suspend fun addWhitelistedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[WHITELISTED_APPS] ?: emptySet()
            preferences[WHITELISTED_APPS] = currentApps + packageName
        }
    }
    
    suspend fun removeWhitelistedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[WHITELISTED_APPS] ?: emptySet()
            preferences[WHITELISTED_APPS] = currentApps - packageName
        }
    }
    
    fun getWhitelistedAppsFlow(): Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[WHITELISTED_APPS] ?: emptySet()
    }
    
    // Service enabled state
    fun isServiceEnabled(): Boolean {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[SERVICE_ENABLED] ?: true
                }.first()
            }
        } catch (e: Exception) {
            true
        }
    }
    
    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }
    
    fun getServiceEnabledFlow(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SERVICE_ENABLED] ?: true
    }
    
    // First launch flag
    fun isFirstLaunch(): Boolean {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[FIRST_LAUNCH] ?: true
                }.first()
            }
        } catch (e: Exception) {
            true
        }
    }
    
    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH] = false
        }
    }
    
    /**
     * Reset all settings to defaults
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    /**
     * Get all settings as a flow for UI observation
     */
    data class AppSettings(
        val blurGainRate: Int,
        val blurRecoveryRate: Int,
        val minBlurLevel: Float,
        val maxBlurLevel: Float,
        val dailyResetHour: Int,
        val dailyResetMinute: Int,
        val whitelistedApps: Set<String>,
        val serviceEnabled: Boolean
    )
    
    fun getAllSettingsFlow(): Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            blurGainRate = preferences[BLUR_GAIN_RATE] ?: DEFAULT_BLUR_GAIN_RATE,
            blurRecoveryRate = preferences[BLUR_RECOVERY_RATE] ?: DEFAULT_BLUR_RECOVERY_RATE,
            minBlurLevel = preferences[MIN_BLUR_LEVEL] ?: DEFAULT_MIN_BLUR_LEVEL,
            maxBlurLevel = preferences[MAX_BLUR_LEVEL] ?: DEFAULT_MAX_BLUR_LEVEL,
            dailyResetHour = preferences[DAILY_RESET_HOUR] ?: DEFAULT_DAILY_RESET_HOUR,
            dailyResetMinute = preferences[DAILY_RESET_MINUTE] ?: DEFAULT_DAILY_RESET_MINUTE,
            whitelistedApps = preferences[WHITELISTED_APPS] ?: emptySet(),
            serviceEnabled = preferences[SERVICE_ENABLED] ?: true
        )
    }
    
    // Delayed launch apps management
    fun getDelayedLaunchApps(): Set<String> {
        return try {
            runBlocking {
                context.dataStore.data.map { preferences ->
                    preferences[DELAYED_LAUNCH_APPS] ?: emptySet()
                }.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getDelayedLaunchApps()", e)
            emptySet()
        }
    }
    
    suspend fun addDelayedLaunchApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[DELAYED_LAUNCH_APPS] ?: emptySet()
            preferences[DELAYED_LAUNCH_APPS] = currentApps + packageName
        }
    }
    
    suspend fun removeDelayedLaunchApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[DELAYED_LAUNCH_APPS] ?: emptySet()
            preferences[DELAYED_LAUNCH_APPS] = currentApps - packageName
        }
    }
    
    fun getDelayedLaunchAppsFlow(): Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[DELAYED_LAUNCH_APPS] ?: emptySet()
    }
}
