package com.focusfade.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
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

class DelayedLaunchSettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWhitelistBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var whitelistManager: WhitelistManager
    
    private lateinit var delayedAppsAdapter: AppListAdapter
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
            Toast.makeText(this, "Error initializing delayed launch settings: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Delayed Launch Apps"
        } catch (e: Exception) {
            // Ignore toolbar setup errors
        }
    }
    
    private fun setupRecyclerViews() {
        try {
            // Delayed launch apps
            delayedAppsAdapter = AppListAdapter(
                onAppClick = { appInfo ->
                    try {
                        removeFromDelayedLaunch(appInfo.packageName)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error removing app", Toast.LENGTH_SHORT).show()
                    }
                },
                showRemoveButton = true
            )
            binding.recyclerWhitelistedApps.apply {
                layoutManager = LinearLayoutManager(this@DelayedLaunchSettingsActivity)
                adapter = delayedAppsAdapter
            }
            
            // Suggested apps
            suggestedAppsAdapter = AppListAdapter(
                onAppClick = { appInfo ->
                    try {
                        addToDelayedLaunch(appInfo.packageName)
                    } catch (e: Exception) {
                        Toast.makeText(this@DelayedLaunchSettingsActivity, "Error adding app", Toast.LENGTH_SHORT).show()
                    }
                },
                showRemoveButton = false
            )
            binding.recyclerSuggestedApps.apply {
                layoutManager = LinearLayoutManager(this@DelayedLaunchSettingsActivity)
                adapter = suggestedAppsAdapter
            }
            
            // All apps
            allAppsAdapter = AppListAdapter(
                onAppClick = { appInfo ->
                    try {
                        lifecycleScope.launch {
                            val delayedApps = settingsManager.getDelayedLaunchApps()
                            if (delayedApps.contains(appInfo.packageName)) {
                                removeFromDelayedLaunch(appInfo.packageName)
                            } else {
                                addToDelayedLaunch(appInfo.packageName)
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@DelayedLaunchSettingsActivity, "Error updating app", Toast.LENGTH_SHORT).show()
                    }
                },
                showRemoveButton = false
            )
            binding.recyclerAllApps.apply {
                layoutManager = LinearLayoutManager(this@DelayedLaunchSettingsActivity)
                adapter = allAppsAdapter
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up lists", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupUI() {
        try {
            binding.apply {
                // Hide permission card since we don't need usage stats for delayed launch
                cardPermissionRequired.visibility = android.view.View.GONE
                
                // Update section headers
                headerWhitelistedApps.text = "Apps Affected by Delayed Launch"
                headerSuggestedApps.text = "Suggested Apps to Delay"
                headerAllApps.text = "All Apps"
                
                // Update empty state text
                textNoWhitelistedApps.text = "No apps selected for delayed launch (all apps are whitelisted by default)"
                
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
                // Load delayed launch packages
                val delayedPackages = try {
                    settingsManager.getDelayedLaunchApps()
                } catch (e: Exception) {
                    emptySet<String>()
                }

                // Load delayed launch apps info
                val delayedApps = try {
                    whitelistManager.getAllInstalledApps().filter { delayedPackages.contains(it.packageName) }
                } catch (e: Exception) {
                    emptyList()
                }
                
                runOnUiThread {
                    try {
                        delayedAppsAdapter.submitList(delayedApps)
                        if (delayedApps.isEmpty()) {
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

                // Load suggested apps (popular user apps)
                val suggestedApps = try {
                    whitelistManager.getSuggestedApps()
                } catch (e: Exception) {
                    emptyList()
                }
                val filteredSuggested = suggestedApps.filter { !delayedPackages.contains(it.packageName) }
                
                runOnUiThread {
                    try {
                        suggestedAppsAdapter.submitList(filteredSuggested)
                        suggestedAppsAdapter.updateWhitelistedApps(delayedPackages)
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
                        allAppsAdapter.updateWhitelistedApps(delayedPackages)
                    } catch (e: Exception) {
                        // Ignore adapter errors
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@DelayedLaunchSettingsActivity,
                        "Error loading apps: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun addToDelayedLaunch(packageName: String) {
        lifecycleScope.launch {
            try {
                settingsManager.addDelayedLaunchApp(packageName)
                runOnUiThread {
                    Toast.makeText(this@DelayedLaunchSettingsActivity, "App added to delayed launch", Toast.LENGTH_SHORT).show()
                    loadApps() // Refresh the lists
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DelayedLaunchSettingsActivity, "Error adding app to delayed launch", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun removeFromDelayedLaunch(packageName: String) {
        lifecycleScope.launch {
            try {
                settingsManager.removeDelayedLaunchApp(packageName)
                runOnUiThread {
                    Toast.makeText(this@DelayedLaunchSettingsActivity, "App removed from delayed launch", Toast.LENGTH_SHORT).show()
                    loadApps() // Refresh the lists
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DelayedLaunchSettingsActivity, "Error removing app from delayed launch", Toast.LENGTH_SHORT).show()
                }
            }
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
