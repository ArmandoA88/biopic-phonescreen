package com.focusfade.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Persistent crash and debug logger that saves logs to files
 */
object CrashLogger {
    
    private const val TAG = "CrashLogger"
    private const val LOG_FILE_NAME = "focusfade_debug.log"
    private const val CRASH_FILE_NAME = "focusfade_crash.log"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    
    private lateinit var logFile: File
    private lateinit var crashFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            logFile = File(logDir, LOG_FILE_NAME)
            crashFile = File(logDir, CRASH_FILE_NAME)
            
            // Set up uncaught exception handler
            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                logCrash(thread, exception)
                // Call the original handler to maintain normal crash behavior
                System.exit(1)
            }
            
            log("INFO", "CrashLogger", "CrashLogger initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CrashLogger", e)
        }
    }
    
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = buildString {
                append("$timestamp [$level] $tag: $message")
                if (throwable != null) {
                    append("\n")
                    append(getStackTraceString(throwable))
                }
                append("\n")
            }
            
            // Log to Android logcat as well
            when (level) {
                "ERROR" -> Log.e(tag, message, throwable)
                "WARN" -> Log.w(tag, message, throwable)
                "INFO" -> Log.i(tag, message, throwable)
                "DEBUG" -> Log.d(tag, message, throwable)
                "VERBOSE" -> Log.v(tag, message, throwable)
                else -> Log.d(tag, message, throwable)
            }
            
            // Write to file
            writeToFile(logFile, logEntry)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log entry", e)
        }
    }
    
    private fun logCrash(thread: Thread, exception: Throwable) {
        try {
            val timestamp = dateFormat.format(Date())
            val crashEntry = buildString {
                append("=== CRASH REPORT ===\n")
                append("Timestamp: $timestamp\n")
                append("Thread: ${thread.name}\n")
                append("Exception: ${exception.javaClass.simpleName}\n")
                append("Message: ${exception.message}\n")
                append("Stack Trace:\n")
                append(getStackTraceString(exception))
                append("\n=== END CRASH REPORT ===\n\n")
            }
            
            // Write to crash file
            writeToFile(crashFile, crashEntry)
            
            // Also write to regular log file
            writeToFile(logFile, crashEntry)
            
            Log.e(TAG, "CRASH DETECTED", exception)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log crash", e)
        }
    }
    
    private fun writeToFile(file: File, content: String) {
        try {
            // Check file size and rotate if necessary
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                rotateLogFile(file)
            }
            
            FileWriter(file, true).use { writer ->
                writer.write(content)
                writer.flush()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to file: ${file.name}", e)
        }
    }
    
    private fun rotateLogFile(file: File) {
        try {
            val backupFile = File(file.parent, "${file.nameWithoutExtension}_backup.${file.extension}")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            file.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log file", e)
        }
    }
    
    private fun getStackTraceString(throwable: Throwable): String {
        return try {
            val sw = java.io.StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }
    
    fun getLogContent(): String {
        return try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No log file found"
            }
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }
    
    fun getCrashContent(): String {
        return try {
            if (crashFile.exists()) {
                crashFile.readText()
            } else {
                "No crash file found"
            }
        } catch (e: Exception) {
            "Error reading crash file: ${e.message}"
        }
    }
    
    fun clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
            if (crashFile.exists()) {
                crashFile.delete()
            }
            log("INFO", TAG, "Log files cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log files", e)
        }
    }
    
    fun getLogFiles(): List<File> {
        return try {
            val logDir = logFile.parentFile
            logDir?.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get log files", e)
            emptyList()
        }
    }
}
