package com.focusfade.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusfade.app.DelayedLaunchActivity
import com.focusfade.app.manager.SettingsManager

/**
 * AccessibilityService to detect app launches and trigger delayed launch for non-whitelisted apps
 */
class AppLaunchAccessibilityService : AccessibilityService() {

    private lateinit var settingsManager: SettingsManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsManager = SettingsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            
            if (packageName != null && packageName != this.packageName) {
                // Check if delayed launch is enabled
                if (!settingsManager.isDelayedLaunchEnabled()) {
                    return
                }
                
                // Check if app is whitelisted
                val whitelistedApps = settingsManager.getWhitelistedApps()
                if (!whitelistedApps.contains(packageName)) {
                    // Launch delayed launch activity
                    val launchIntent = Intent(this, DelayedLaunchActivity::class.java)
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    launchIntent.putExtra("TARGET_PACKAGE", packageName)
                    startActivity(launchIntent)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }
}
