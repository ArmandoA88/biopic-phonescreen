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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_fade_settings")

/**
 * Manages app settings using DataStore
 */
class SettingsManager(private val context: Context) {
    
    companion object {
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
        
        // Default values
        const val DEFAULT_BLUR_GAIN_RATE = 10 // 10% every 10 minutes
        const val DEFAULT_BLUR_RECOVERY_RATE = 10 // 10% recovery every 10 minutes
        const val DEFAULT_MIN_BLUR_LEVEL = 0f
        const val DEFAULT_MAX_BLUR_LEVEL = 100f
        const val DEFAULT_DAILY_RESET_HOUR = 0 // midnight
        const val DEFAULT_DAILY_RESET_MINUTE = 0
    }
    
    // Blur gain rate (minutes per 10% blur increase)
    fun getBlurGainRate(): Int = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[BLUR_GAIN_RATE] ?: DEFAULT_BLUR_GAIN_RATE
        }.first()
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
    fun getBlurRecoveryRate(): Int = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[BLUR_RECOVERY_RATE] ?: DEFAULT_BLUR_RECOVERY_RATE
        }.first()
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
    fun getMinBlurLevel(): Float = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[MIN_BLUR_LEVEL] ?: DEFAULT_MIN_BLUR_LEVEL
        }.first()
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
    fun getMaxBlurLevel(): Float = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[MAX_BLUR_LEVEL] ?: DEFAULT_MAX_BLUR_LEVEL
        }.first()
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
    fun getDailyResetHour(): Int = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[DAILY_RESET_HOUR] ?: DEFAULT_DAILY_RESET_HOUR
        }.first()
    }
    
    fun getDailyResetMinute(): Int = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[DAILY_RESET_MINUTE] ?: DEFAULT_DAILY_RESET_MINUTE
        }.first()
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
    fun getWhitelistedApps(): Set<String> = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[WHITELISTED_APPS] ?: emptySet()
        }.first()
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
    fun isServiceEnabled(): Boolean = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[SERVICE_ENABLED] ?: true
        }.first()
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
    fun isFirstLaunch(): Boolean = runBlocking {
        context.dataStore.data.map { preferences ->
            preferences[FIRST_LAUNCH] ?: true
        }.first()
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
}
