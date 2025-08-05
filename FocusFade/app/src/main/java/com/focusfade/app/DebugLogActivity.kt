package com.focusfade.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Button
import android.widget.TextView
import android.widget.ScrollView
import com.focusfade.app.utils.CrashLogger

class DebugLogActivity : AppCompatActivity() {
    
    private lateinit var buttonBack: Button
    private lateinit var buttonRefreshLogs: Button
    private lateinit var buttonClearLogs: Button
    private lateinit var buttonFilterAll: Button
    private lateinit var buttonFilterService: Button
    private lateinit var buttonFilterSettings: Button
    private lateinit var buttonFilterErrors: Button
    private lateinit var textLogs: TextView
    private lateinit var scrollViewLogs: ScrollView
    private val logTags = listOf(
        "BlurOverlayService",
        "SettingsManager", 
        "FocusStateManager",
        "WhitelistManager",
        "AndroidRuntime"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)
        
        initViews()
        setupUI()
        loadLogs()
    }
    
    private fun initViews() {
        buttonBack = findViewById(R.id.buttonBack)
        buttonRefreshLogs = findViewById(R.id.buttonRefreshLogs)
        buttonClearLogs = findViewById(R.id.buttonClearLogs)
        buttonFilterAll = findViewById(R.id.buttonFilterAll)
        buttonFilterService = findViewById(R.id.buttonFilterService)
        buttonFilterSettings = findViewById(R.id.buttonFilterSettings)
        buttonFilterErrors = findViewById(R.id.buttonFilterErrors)
        textLogs = findViewById(R.id.textLogs)
        scrollViewLogs = findViewById(R.id.scrollViewLogs)
    }
    
    private fun setupUI() {
        // Back button
        buttonBack.setOnClickListener {
            finish()
        }
        
        // Refresh logs button
        buttonRefreshLogs.setOnClickListener {
            loadLogs()
        }
        
        // Clear logs button
        buttonClearLogs.setOnClickListener {
            clearLogs()
        }
        
        // Filter buttons
        buttonFilterAll.setOnClickListener {
            loadLogs()
        }
        
        buttonFilterService.setOnClickListener {
            loadLogs("BlurOverlayService")
        }
        
        buttonFilterSettings.setOnClickListener {
            loadLogs("SettingsManager")
        }
        
        buttonFilterErrors.setOnClickListener {
            loadLogs(null, "E")
        }
    }
    
    private fun loadLogs(tagFilter: String? = null, levelFilter: String? = null) {
        lifecycleScope.launch {
            textLogs.text = "Loading logs..."
            
            try {
                val logs = withContext(Dispatchers.IO) {
                    // First try to read from persistent log files
                    val persistentLogs = CrashLogger.getLogContent()
                    val crashLogs = CrashLogger.getCrashContent()
                    
                    val combinedLogs = buildString {
                        if (crashLogs != "No crash file found") {
                            appendLine("=== CRASH LOGS ===")
                            appendLine(crashLogs)
                            appendLine("=== END CRASH LOGS ===")
                            appendLine()
                        }
                        
                        if (persistentLogs != "No log file found") {
                            appendLine("=== PERSISTENT LOGS ===")
                            appendLine(persistentLogs)
                            appendLine("=== END PERSISTENT LOGS ===")
                            appendLine()
                        }
                        
                        // Also try to get logcat logs
                        appendLine("=== LOGCAT LOGS ===")
                        appendLine(readLogcat(tagFilter, levelFilter))
                        appendLine("=== END LOGCAT LOGS ===")
                    }
                    
                    // Apply filters if specified
                    if (tagFilter != null || levelFilter != null) {
                        filterLogs(combinedLogs, tagFilter, levelFilter)
                    } else {
                        combinedLogs
                    }
                }
                
                textLogs.text = if (logs.isNotEmpty()) {
                    logs
                } else {
                    "No logs found. Make sure the app is running and generating logs."
                }
                
                // Scroll to bottom to show latest logs
                scrollViewLogs.post {
                    scrollViewLogs.fullScroll(android.view.View.FOCUS_DOWN)
                }
                
            } catch (e: Exception) {
                textLogs.text = "Error reading logs: ${e.message}\n\n" +
                        "This might happen if:\n" +
                        "1. The app doesn't have permission to read logs\n" +
                        "2. The device doesn't allow log access\n" +
                        "3. No logs have been generated yet\n\n" +
                        "Try running the app for a minute to generate logs, then refresh."
                
                Toast.makeText(this@DebugLogActivity, "Error reading logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun filterLogs(logs: String, tagFilter: String?, levelFilter: String?): String {
        return logs.lines().filter { line ->
            var matches = true
            
            if (tagFilter != null) {
                matches = matches && line.contains(tagFilter)
            }
            
            if (levelFilter != null) {
                matches = matches && (line.contains("[$levelFilter]") || line.contains("/$levelFilter "))
            }
            
            matches
        }.joinToString("\n")
    }
    
    private fun readLogcat(tagFilter: String? = null, levelFilter: String? = null): String {
        val command = buildList {
            add("logcat")
            add("-d") // dump logs and exit
            add("-v")
            add("time") // include timestamps
            
            // Add tag filter if specified
            if (tagFilter != null) {
                add("-s")
                add(tagFilter)
            } else {
                // Filter for our app's tags
                add("-s")
                add(logTags.joinToString(","))
            }
        }
        
        val process = Runtime.getRuntime().exec(command.toTypedArray())
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val logs = StringBuilder()
        
        var line: String?
        var lineCount = 0
        val maxLines = 1000 // Limit to prevent memory issues
        
        while (reader.readLine().also { line = it } != null && lineCount < maxLines) {
            val logLine = line!!
            
            // Apply level filter if specified
            if (levelFilter != null && !logLine.contains("/$levelFilter ")) {
                continue
            }
            
            // Format the log line for better readability
            val formattedLine = formatLogLine(logLine)
            logs.appendLine(formattedLine)
            lineCount++
        }
        
        reader.close()
        process.waitFor()
        
        if (lineCount >= maxLines) {
            logs.appendLine("\n... (showing last $maxLines lines)")
        }
        
        return logs.toString()
    }
    
    private fun formatLogLine(logLine: String): String {
        // Parse logcat format: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: MESSAGE
        return try {
            val parts = logLine.split(" ", limit = 6)
            if (parts.size >= 6) {
                val timestamp = "${parts[0]} ${parts[1]}"
                val level = parts[4]
                val tagAndMessage = parts[5]
                
                // Color code by level (using text indicators since we can't use colors in TextView)
                val levelIndicator = when {
                    level.contains("/E ") -> "[ERROR]"
                    level.contains("/W ") -> "[WARN]"
                    level.contains("/I ") -> "[INFO]"
                    level.contains("/D ") -> "[DEBUG]"
                    level.contains("/V ") -> "[VERBOSE]"
                    else -> "[LOG]"
                }
                
                "$timestamp $levelIndicator $tagAndMessage"
            } else {
                logLine
            }
        } catch (e: Exception) {
            logLine
        }
    }
    
    private fun clearLogs() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear the logcat buffer
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
                    process.waitFor()
                    
                    // Clear persistent log files
                    CrashLogger.clearLogs()
                }
                
                textLogs.text = "All logs cleared. New logs will appear as the app runs."
                Toast.makeText(this@DebugLogActivity, "All logs cleared", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@DebugLogActivity, "Error clearing logs: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
