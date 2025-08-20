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
        // Method 1: UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60,
            currentTime
        )
        
        if (!stats.isNullOrEmpty()) {
            val mostRecentApp = stats.maxByOrNull { it.lastTimeUsed }
            if (mostRecentApp != null) return mostRecentApp.packageName
        }

        // Method 2: ActivityManager running tasks (legacy but still works on some devices)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val taskInfo = am.getRunningTasks(1)
            if (!taskInfo.isNullOrEmpty()) {
                return taskInfo[0].topActivity?.packageName
            }
        } catch (e: Exception) {}

        // Method 3: Running app processes
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = am.runningAppProcesses
            if (!processes.isNullOrEmpty()) {
                val foregroundProc = processes.firstOrNull { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                if (foregroundProc != null) return foregroundProc.processName
            }
        } catch (e: Exception) {}

        return null
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
        val addedPackages = mutableSetOf<String>()
        
        try {
            // Method 1: Get all installed packages first (most comprehensive)
            val installedPackages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11+ requires QUERY_ALL_PACKAGES permission or specific package queries
                try {
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                } catch (e: Exception) {
                    // Fallback for permission issues
                    packageManager.getInstalledPackages(0)
                }
            } else {
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            }
            
            for (packageInfo in installedPackages) {
                val appInfo = packageInfo.applicationInfo
                
                if (appInfo.packageName != context.packageName && !addedPackages.contains(appInfo.packageName)) {
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
                    
                    // Include all apps that are enabled and not hidden
                    if (appInfo.enabled) {
                        apps.add(AppInfo(appInfo.packageName, appName, icon))
                        addedPackages.add(appInfo.packageName)
                    }
                }
            }
            
            // Method 2: Get apps from launcher as additional check
            try {
                val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                val launcherApps = packageManager.queryIntentActivities(mainIntent, 0)
                
                for (resolveInfo in launcherApps) {
                    val packageName = resolveInfo.activityInfo.packageName
                    
                    if (packageName != context.packageName && !addedPackages.contains(packageName)) {
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
                        
                        apps.add(AppInfo(packageName, appName, icon))
                        addedPackages.add(packageName)
                    }
                }
            } catch (e: Exception) {
                // Continue if launcher query fails
            }
            
        } catch (e: Exception) {
            // Fallback method - get all applications without any filtering
            try {
                val installedApps = packageManager.getInstalledApplications(0)
                
                for (appInfo in installedApps) {
                    if (appInfo.packageName != context.packageName && !addedPackages.contains(appInfo.packageName)) {
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
                        
                        apps.add(AppInfo(appInfo.packageName, appName, icon))
                        addedPackages.add(appInfo.packageName)
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
