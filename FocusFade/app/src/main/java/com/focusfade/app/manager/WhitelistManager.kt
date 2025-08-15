package com.focusfade.app.manager

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages app whitelisting and foreground app detection
 */
class WhitelistManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    
    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: Flow<String?> = _currentForegroundApp.asStateFlow()
    
    private val _isWhitelistedAppActive = MutableStateFlow(false)
    val isWhitelistedAppActive: Flow<Boolean> = _isWhitelistedAppActive.asStateFlow()
    
    /**
     * Data class for app information
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable?
    )
    
    /**
     * Gets the currently active foreground app
     */
    fun getCurrentForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60, // Last minute
            currentTime
        )
        
        if (stats.isNullOrEmpty()) {
            return null
        }
        
        // Find the most recently used app
        val mostRecentApp = stats.maxByOrNull { it.lastTimeUsed }
        return mostRecentApp?.packageName
    }
    
    /**
     * Updates the current foreground app and checks if it's whitelisted
     */
    fun updateForegroundAppStatus() {
        val foregroundApp = getCurrentForegroundApp()
        _currentForegroundApp.value = foregroundApp
        
        val whitelistedApps = settingsManager.getWhitelistedApps()
        val isWhitelisted = foregroundApp != null && whitelistedApps.contains(foregroundApp)
        _isWhitelistedAppActive.value = isWhitelisted
    }
    
    /**
     * Checks if the current foreground app is whitelisted
     */
    fun isCurrentAppWhitelisted(): Boolean {
        val foregroundApp = getCurrentForegroundApp()
        val whitelistedApps = settingsManager.getWhitelistedApps()
        return foregroundApp != null && whitelistedApps.contains(foregroundApp)
    }
    
    /**
     * Gets all installed apps that can be whitelisted
     */
    fun getAllInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        
        try {
            // Method 1: Get apps from launcher
            val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val launcherApps = packageManager.queryIntentActivities(mainIntent, 0)
            
            for (resolveInfo in launcherApps) {
                val packageName = resolveInfo.activityInfo.packageName
                
                if (packageName != context.packageName) {
                    val appName = try {
                        resolveInfo.loadLabel(packageManager).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    
                    val icon = try {
                        resolveInfo.loadIcon(packageManager)
                    } catch (e: Exception) {
                        null
                    }
                    
                    // Avoid duplicates
                    if (!apps.any { it.packageName == packageName }) {
                        apps.add(AppInfo(packageName, appName, icon))
                    }
                }
            }
            
            // Method 2: Get all installed packages as backup
            if (apps.size < 20) {
                val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                
                for (packageInfo in installedPackages) {
                    val appInfo = packageInfo.applicationInfo
                    
                    if (appInfo.packageName != context.packageName) {
                        val appName = try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            appInfo.packageName
                        }
                        
                        val icon = try {
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }
                        
                        // Check if it has a launcher intent or is enabled
                        val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                        val isEnabled = appInfo.enabled
                        
                        if ((launchIntent != null || isEnabled) && !apps.any { it.packageName == appInfo.packageName }) {
                            apps.add(AppInfo(appInfo.packageName, appName, icon))
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            // Fallback method - get all applications
            try {
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                
                for (appInfo in installedApps) {
                    if (appInfo.packageName != context.packageName) {
                        val appName = try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            appInfo.packageName
                        }
                        
                        val icon = try {
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }
                        
                        if (!apps.any { it.packageName == appInfo.packageName }) {
                            apps.add(AppInfo(appInfo.packageName, appName, icon))
                        }
                    }
                }
            } catch (e2: Exception) {
                // Handle permission issues or other errors
            }
        }
        
        return apps.sortedBy { it.appName }
    }
    
    /**
     * Gets commonly whitelisted apps (productivity, education, etc.)
     */
    fun getSuggestedApps(): List<AppInfo> {
        val suggestedPackages = setOf(
            "com.amazon.kindle",
            "com.duolingo",
            "com.google.android.apps.books",
            "com.microsoft.office.word",
            "com.microsoft.office.excel",
            "com.microsoft.office.powerpoint",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
            "com.evernote",
            "com.notion.id",
            "com.todoist",
            "com.any.do",
            "com.google.android.apps.maps",
            "com.waze",
            "com.spotify.music",
            "com.google.android.music",
            "com.audible.application",
            "com.headspace.android",
            "com.calm.android",
            "com.fitbit.FitbitMobile",
            "com.myfitnesspal.android",
            "com.google.android.apps.fitness"
        )
        
        val allApps = getAllInstalledApps()
        return allApps.filter { suggestedPackages.contains(it.packageName) }
    }
    
    /**
     * Gets whitelisted apps with their info
     */
    fun getWhitelistedAppsInfo(): List<AppInfo> {
        val whitelistedPackages = settingsManager.getWhitelistedApps()
        val allApps = getAllInstalledApps()
        
        return allApps.filter { whitelistedPackages.contains(it.packageName) }
    }
    
    /**
     * Checks if usage stats permission is granted
     */
    fun hasUsageStatsPermission(): Boolean {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60,
            currentTime
        )
        return stats != null && stats.isNotEmpty()
    }
    
    /**
     * Gets app name from package name
     */
    fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    /**
     * Gets app icon from package name
     */
    fun getAppIcon(packageName: String): android.graphics.drawable.Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Checks if an app is installed
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Removes uninstalled apps from whitelist
     */
    suspend fun cleanupWhitelist() {
        val whitelistedApps = settingsManager.getWhitelistedApps()
        val installedApps = whitelistedApps.filter { isAppInstalled(it) }.toSet()
        
        if (installedApps.size != whitelistedApps.size) {
            settingsManager.setWhitelistedApps(installedApps)
        }
    }
}
