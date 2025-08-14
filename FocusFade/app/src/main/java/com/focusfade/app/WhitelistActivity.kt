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
        
        try {
            binding = ActivityWhitelistBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            settingsManager = SettingsManager(this)
            whitelistManager = WhitelistManager(this, settingsManager)
            
            setupToolbar()
            setupRecyclerViews()
            setupUI()
            loadApps()
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing whitelist: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Whitelist Apps"
        } catch (e: Exception) {
            // Ignore toolbar setup errors
        }
    }
    
    private fun setupRecyclerViews() {
        try {
            // Whitelisted apps
            whitelistedAppsAdapter = AppListAdapter(
                onAppClick = { appInfo ->
                    try {
                        removeFromWhitelist(appInfo.packageName)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error removing app", Toast.LENGTH_SHORT).show()
                    }
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
                    try {
                        addToWhitelist(appInfo.packageName)
                    } catch (e: Exception) {
                        Toast.makeText(this@WhitelistActivity, "Error adding app", Toast.LENGTH_SHORT).show()
                    }
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
                    try {
                        val whitelistedApps = settingsManager.getWhitelistedApps()
                        if (whitelistedApps.contains(appInfo.packageName)) {
                            removeFromWhitelist(appInfo.packageName)
                        } else {
                            addToWhitelist(appInfo.packageName)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@WhitelistActivity, "Error updating app", Toast.LENGTH_SHORT).show()
                    }
                },
                showRemoveButton = false
            )
            binding.recyclerAllApps.apply {
                layoutManager = LinearLayoutManager(this@WhitelistActivity)
                adapter = allAppsAdapter
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up lists", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupUI() {
        try {
            binding.apply {
                // Permission check
                try {
                    if (!whitelistManager.hasUsageStatsPermission()) {
                        cardPermissionRequired.visibility = android.view.View.VISIBLE
                        buttonGrantPermission.setOnClickListener {
                            requestUsageStatsPermission()
                        }
                    } else {
                        cardPermissionRequired.visibility = android.view.View.GONE
                    }
                } catch (e: Exception) {
                    cardPermissionRequired.visibility = android.view.View.VISIBLE
                    buttonGrantPermission.setOnClickListener {
                        requestUsageStatsPermission()
                    }
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
        } catch (e: Exception) {
            // Ignore UI setup errors
        }
    }
    
    private fun toggleSection(section: android.view.View) {
        try {
            section.visibility = if (section.visibility == android.view.View.VISIBLE) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            // Ignore toggle errors
        }
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            try {
                // Check permission first
                val hasPermission = try {
                    whitelistManager.hasUsageStatsPermission()
                } catch (e: Exception) {
                    false
                }
                
                if (!hasPermission) {
                    runOnUiThread {
                        binding.cardPermissionRequired.visibility = android.view.View.VISIBLE
                    }
                    return@launch
                } else {
                    runOnUiThread {
                        binding.cardPermissionRequired.visibility = android.view.View.GONE
                    }
                }

                // Show basic UI without loading apps if there are issues
                runOnUiThread {
                    try {
                        whitelistedAppsAdapter.submitList(emptyList())
                        suggestedAppsAdapter.submitList(emptyList())
                        allAppsAdapter.submitList(emptyList())
                        
                        binding.textNoWhitelistedApps.visibility = android.view.View.VISIBLE
                        binding.recyclerWhitelistedApps.visibility = android.view.View.GONE
                    } catch (e: Exception) {
                        // Ignore adapter errors
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@WhitelistActivity,
                        "Whitelist feature temporarily unavailable",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun addToWhitelist(packageName: String) {
        lifecycleScope.launch {
            try {
                settingsManager.addWhitelistedApp(packageName)
                runOnUiThread {
                    Toast.makeText(this@WhitelistActivity, "App added to whitelist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@WhitelistActivity, "Error adding app to whitelist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun removeFromWhitelist(packageName: String) {
        lifecycleScope.launch {
            try {
                settingsManager.removeWhitelistedApp(packageName)
                runOnUiThread {
                    Toast.makeText(this@WhitelistActivity, "App removed from whitelist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@WhitelistActivity, "Error removing app from whitelist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun requestUsageStatsPermission() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Check permission status when returning from settings
            if (whitelistManager.hasUsageStatsPermission()) {
                binding.cardPermissionRequired.visibility = android.view.View.GONE
            }
        } catch (e: Exception) {
            // Ignore resume errors
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        try {
            onBackPressed()
            return true
        } catch (e: Exception) {
            finish()
            return true
        }
    }
}
