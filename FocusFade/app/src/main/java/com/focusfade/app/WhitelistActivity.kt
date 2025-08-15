package com.focusfade.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
    
    private var allAppsList = listOf<WhitelistManager.AppInfo>()
    private var filteredAppsList = listOf<WhitelistManager.AppInfo>()
    private var currentFilter = AppFilter.ALL
    
    enum class AppFilter {
        ALL, USER, SYSTEM
    }
    
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
                
                // Setup search functionality
                editTextSearch.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        filterApps(s.toString())
                    }
                })
                
                // Setup filter toggle buttons
                toggleGroupAppFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (isChecked) {
                        currentFilter = when (checkedId) {
                            R.id.buttonShowAll -> AppFilter.ALL
                            R.id.buttonShowUser -> AppFilter.USER
                            R.id.buttonShowSystem -> AppFilter.SYSTEM
                            else -> AppFilter.ALL
                        }
                        filterApps(editTextSearch.text.toString())
                    }
                }
                
                // Set default selection
                buttonShowAll.isChecked = true
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

                // Load whitelisted packages
                val whitelistedPackages = try {
                    settingsManager.getWhitelistedApps()
                } catch (e: Exception) {
                    emptySet<String>()
                }

                // Load whitelisted apps
                val whitelistedApps = try {
                    whitelistManager.getWhitelistedAppsInfo()
                } catch (e: Exception) {
                    emptyList()
                }
                
                runOnUiThread {
                    try {
                        whitelistedAppsAdapter.submitList(whitelistedApps)
                        if (whitelistedApps.isEmpty()) {
                            binding.textNoWhitelistedApps.visibility = android.view.View.VISIBLE
                            binding.recyclerWhitelistedApps.visibility = android.view.View.GONE
                        } else {
                            binding.textNoWhitelistedApps.visibility = android.view.View.GONE
                            binding.recyclerWhitelistedApps.visibility = android.view.View.VISIBLE
                        }
                    } catch (e: Exception) {
                        // Ignore adapter errors
                    }
                }

                // Load suggested apps
                val suggestedApps = try {
                    whitelistManager.getSuggestedApps()
                } catch (e: Exception) {
                    emptyList()
                }
                val filteredSuggested = suggestedApps.filter { !whitelistedPackages.contains(it.packageName) }
                
                runOnUiThread {
                    try {
                        suggestedAppsAdapter.submitList(filteredSuggested)
                        suggestedAppsAdapter.updateWhitelistedApps(whitelistedPackages)
                    } catch (e: Exception) {
                        // Ignore adapter errors
                    }
                }

                // Load all apps
                allAppsList = try {
                    whitelistManager.getAllInstalledApps()
                } catch (e: Exception) {
                    emptyList()
                }
                
                // Apply initial filter
                filterApps("")
                
                runOnUiThread {
                    try {
                        allAppsAdapter.updateWhitelistedApps(whitelistedPackages)
                    } catch (e: Exception) {
                        // Ignore adapter errors
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@WhitelistActivity,
                        "Error loading apps: ${e.message}",
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
                    loadApps() // Refresh the lists
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
                    loadApps() // Refresh the lists
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
    
    private fun filterApps(searchQuery: String) {
        try {
            val packageManager = packageManager
            
            filteredAppsList = allAppsList.filter { appInfo ->
                // Apply search filter
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    appInfo.appName.contains(searchQuery, ignoreCase = true) ||
                    appInfo.packageName.contains(searchQuery, ignoreCase = true)
                }
                
                // Apply app type filter
                val matchesFilter = when (currentFilter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> {
                        try {
                            val applicationInfo = packageManager.getApplicationInfo(appInfo.packageName, 0)
                            (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        } catch (e: Exception) {
                            false
                        }
                    }
                    AppFilter.SYSTEM -> {
                        try {
                            val applicationInfo = packageManager.getApplicationInfo(appInfo.packageName, 0)
                            (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                
                matchesSearch && matchesFilter
            }
            
            runOnUiThread {
                try {
                    allAppsAdapter.submitList(filteredAppsList)
                } catch (e: Exception) {
                    // Ignore adapter errors
                }
            }
        } catch (e: Exception) {
            // Ignore filter errors
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
