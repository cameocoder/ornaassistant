package com.lloir.ornaassistant.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.lloir.ornaassistant.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogCollector @Inject constructor() {

    companion object {
        private const val TAG = "LogCollector"
        private const val MAX_LOG_LINES = 1000
        private const val MAX_LOG_FILES = 5
        private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    }

    /**
     * Collect application logs for debugging
     */
    fun collectLogs(): String {
        return try {
            val logs = StringBuilder()

            // Add device info
            logs.appendLine("=== DEVICE INFORMATION ===")
            logs.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            logs.appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            logs.appendLine("App Version: ${getAppVersion()}")
            logs.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            logs.appendLine()

            // Collect logcat output for our app
            logs.appendLine("=== APPLICATION LOGS ===")
            logs.append(getLogcatOutput())

            logs.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting logs", e)
            "Error collecting logs: ${e.message}"
        }
    }

    /**
     * Get recent logcat output filtered for our app
     */
    private fun getLogcatOutput(): String {
        return try {
            val logLevel = if (BuildConfig.DEBUG) "V" else "W" // Only warnings and errors in production
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", 
                "-d", // dump logs
                "-v", "time", // include timestamps
                "--pid=${android.os.Process.myPid()}", // only our process
                "*:$logLevel" // Filtered log level
            ))

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = StringBuilder()
            val lines = mutableListOf<String>()

            // Read all lines
            reader.useLines { sequence ->
                sequence.forEach { line ->
                    if (line.contains("OrnaAssistant") || 
                        line.contains("com.lloir.ornaassistant") ||
                        line.contains("OrnaAccessibility") ||
                        line.contains("OverlayManager") ||
                        line.contains("DungeonScreenParser")) {
                        lines.add(line)
                    }
                }
            }

            // Keep only the most recent lines
            val recentLines = if (lines.size > MAX_LOG_LINES) {
                lines.takeLast(MAX_LOG_LINES)
            } else {
                lines
            }

            recentLines.forEach { line ->
                logs.appendLine(line)
            }

            // Destroy the process to release resources
            process.destroy()

            logs.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting logcat output", e)
            "Error retrieving logs: ${e.message}"
        }
    }

    private fun getAppVersion(): String = "2.0.10" // Could be made dynamic

    /**
     * Set up log rotation for debug builds
     */
    fun setupLogRotation(context: Context) {
        if (BuildConfig.DEBUG) {
            try {
                // Clear old log files if there are too many
                val logDir = context.getExternalFilesDir("logs")
                logDir?.let { dir ->
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }

                    // Get all log files
                    val logFiles = dir.listFiles { file -> 
                        file.isFile && file.name.endsWith(".log") 
                    }

                    // Sort by last modified time (oldest first)
                    logFiles?.sortBy { it.lastModified() }

                    // Delete oldest files if we have too many
                    if (logFiles != null && logFiles.size > MAX_LOG_FILES) {
                        for (i in 0 until logFiles.size - MAX_LOG_FILES) {
                            logFiles[i].delete()
                        }
                    }
                }

                Log.i(TAG, "Log rotation setup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up log rotation", e)
            }
        }
    }
}
