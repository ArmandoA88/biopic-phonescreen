package com.focusfade.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.focusfade.app.adapter.AppListAdapter
import com.focusfade.app.databinding.ActivityWhitelistBinding
import com.focusfade.app.manager.SettingsManager
import com.focusfade.app.manager.WhitelistManager
import kotlinx.coroutines.launch

class WhitelistActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWhitelistBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var whitelistManager: WhitelistManager
    
    private lateinit var whitelistedAppsAdapter: AppListAdapter
    private lateinit var suggestedAppsAdapter: AppListAdapter
    private lateinit var allAppsAdapter: AppListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsManager = SettingsManager(this)
        whitelistManager = WhitelistManager(this, settingsManager)
        
        setupToolbar()
        setupRecyclerViews()
        setupUI()
        loadApps()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Whitelist Apps"
    }
    
    private fun setupRecyclerViews() {
        // Whitelisted apps
        whitelistedAppsAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                removeFromWhitelist(appInfo.packageName)
            },
            showRemoveButton = true
        )
        binding.recyclerWhitelistedApps.apply {
            layoutManager = LinearLayoutManager(this@WhitelistActivity)
            adapter = whitelistedAppsAdapter
        }
        
        // Suggested apps
        suggestedAppsAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                addToWhitelist(appInfo.packageName)
            },
            showRemoveButton = false
        )
        binding.recyclerSuggestedApps.apply {
            layoutManager = LinearLayoutManager(this@WhitelistActivity)
            adapter = suggestedAppsAdapter
        }
        
        // All apps
        allAppsAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                val whitelistedApps = settingsManager.getWhitelistedApps()
                if (whitelistedApps.contains(appInfo.packageName)) {
                    removeFromWhitelist(appInfo.packageName)
                } else {
                    addToWhitelist(appInfo.packageName)
                }
            },
            showRemoveButton = false
        )
        binding.recyclerAllApps.apply {
            layoutManager = LinearLayoutManager(this@WhitelistActivity)
            adapter = allAppsAdapter
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // Permission check
            if (!whitelistManager.hasUsageStatsPermission()) {
                cardPermissionRequired.visibility = android.view.View.VISIBLE
                buttonGrantPermission.setOnClickListener {
                    requestUsageStatsPermission()
                }
            } else {
                cardPermissionRequired.visibility = android.view.View.GONE
            }
            
            // Expand/collapse sections
            headerWhitelistedApps.setOnClickListener {
                toggleSection(sectionWhitelistedApps)
            }
            
            headerSuggestedApps.setOnClickListener {
                toggleSection(sectionSuggestedApps)
            }
            
            headerAllApps.setOnClickListener {
                toggleSection(sectionAllApps)
            }
        }
    }
    
    private fun toggleSection(section: android.view.View) {
        section.visibility = if (section.visibility == android.view.View.VISIBLE) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            try {
                // Load whitelisted apps
                val whitelistedApps = whitelistManager.getWhitelistedAppsInfo()
                whitelistedAppsAdapter.submitList(whitelistedApps)
                
                if (whitelistedApps.isEmpty()) {
                    binding.textNoWhitelistedApps.visibility = android.view.View.VISIBLE
                    binding.recyclerWhitelistedApps.visibility = android.view.View.GONE
                } else {
                    binding.textNoWhitelistedApps.visibility = android.view.View.GONE
                    binding.recyclerWhitelistedApps.visibility = android.view.View.VISIBLE
                }
                
                // Load suggested apps
                val suggestedApps = whitelistManager.getSuggestedApps()
                val whitelistedPackages = settingsManager.getWhitelistedApps()
                val filteredSuggested = suggestedApps.filter { !whitelistedPackages.contains(it.packageName) }
                suggestedAppsAdapter.submitList(filteredSuggested)
                
                // Load all apps
                val allApps = whitelistManager.getAllInstalledApps()
                allAppsAdapter.submitList(allApps)
                allAppsAdapter.updateWhitelistedApps(whitelistedPackages)
                
            } catch (e: Exception) {
                Toast.makeText(this@WhitelistActivity, "Error loading apps: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun addToWhitelist(packageName: String) {
        lifecycleScope.launch {
            settingsManager.addWhitelistedApp(packageName)
            val appName = whitelistManager.getAppName(packageName)
            Toast.makeText(this@WhitelistActivity, "$appName added to whitelist", Toast.LENGTH_SHORT).show()
            loadApps() // Refresh the lists
        }
    }
    
    private fun removeFromWhitelist(packageName: String) {
        lifecycleScope.launch {
            settingsManager.removeWhitelistedApp(packageName)
            val appName = whitelistManager.getAppName(packageName)
            Toast.makeText(this@WhitelistActivity, "$appName removed from whitelist", Toast.LENGTH_SHORT).show()
            loadApps() // Refresh the lists
        }
    }
    
    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Check permission status when returning from settings
        if (whitelistManager.hasUsageStatsPermission()) {
            binding.cardPermissionRequired.visibility = android.view.View.GONE
            loadApps()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
