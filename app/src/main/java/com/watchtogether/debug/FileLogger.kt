package com.watchtogether.debug

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class FileLogger(context: Context) {

    private val logDir = File(context.filesDir, LOG_DIR_NAME)
    private val crashDir = File(context.filesDir, CRASH_DIR_NAME)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLogger").apply { isDaemon = true }
    }

    private val _recentLogs = CopyOnWriteArrayList<LogEntry>()
    val recentLogs: List<LogEntry> get() = _recentLogs.toList()

    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    init {
        logDir.mkdirs()
        crashDir.mkdirs()
        rotateLogsIfNeeded()
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun log(entry: LogEntry) {
        _recentLogs.add(entry)
        while (_recentLogs.size > MAX_MEMORY_ENTRIES) {
            _recentLogs.removeAt(0)
        }
        listeners.forEach { it(entry) }
        executor.execute {
            writeToFile(entry)
        }
    }

    fun logCrash(thread: Thread, throwable: Throwable) {
        val entry = LogEntry(
            level = LogEntry.Level.CRASH,
            tag = LogTag.CRASH,
            message = "CRASH in thread ${thread.name}: ${throwable.message}",
            threadName = thread.name,
            throwable = throwable.stackTraceToString()
        )
        _recentLogs.add(entry)
        listeners.forEach { it(entry) }
        writeCrashFile(thread, throwable)
        writeToFile(entry)
    }

    fun getCrashHistory(): List<String> {
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_CRASH_FILES)
            ?.map { it.readText() }
            ?: emptyList()
    }

    fun getLogFiles(): List<File> {
        if (!logDir.exists()) return emptyList()
        return logDir.listFiles()
            ?.filter { it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getCrashFiles(): List<File> {
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearLogs() {
        _recentLogs.clear()
        executor.execute {
            logDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun clearCrashes() {
        executor.execute {
            crashDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun exportLogsAsText(): File {
        val exportFile = File(logDir.parentFile, "watchtogether_logs.txt")
        PrintWriter(FileWriter(exportFile)).use { writer ->
            writer.println("=== WatchTogether Debug Logs ===")
            writer.println("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            writer.println()

            // Recent in-memory logs
            writer.println("--- Recent Logs (${_recentLogs.size} entries) ---")
            _recentLogs.forEach { writer.println(it.format()) }
            writer.println()

            // File logs
            getLogFiles().forEach { file ->
                writer.println("--- File: ${file.name} ---")
                writer.println(file.readText())
                writer.println()
            }

            // Crash history
            val crashes = getCrashFiles()
            if (crashes.isNotEmpty()) {
                writer.println("--- Crash History (${crashes.size} crashes) ---")
                crashes.forEach { file ->
                    writer.println("--- ${file.name} ---")
                    writer.println(file.readText())
                    writer.println()
                }
            }
        }
        return exportFile
    }

    fun exportLogsAsZip(context: Context): File {
        val zipFile = File(context.cacheDir, "watchtogether_logs.zip")
        val textFile = exportLogsAsText()
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            // Add text log
            zos.putNextEntry(java.util.zip.ZipEntry("logs.txt"))
            textFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()

            // Add individual log files
            getLogFiles().forEach { file ->
                zos.putNextEntry(java.util.zip.ZipEntry("logs/${file.name}"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            // Add crash files
            getCrashFiles().forEach { file ->
                zos.putNextEntry(java.util.zip.ZipEntry("crashes/${file.name}"))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        textFile.delete()
        return zipFile
    }

    private fun writeToFile(entry: LogEntry) {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val logFile = File(logDir, "watchtogether_$dateStr.log")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry.format())
            }
        } catch (e: Exception) {
            // Avoid recursive logging
        }
    }

    private fun writeCrashFile(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            PrintWriter(FileWriter(crashFile)).use { writer ->
                writer.println("Crash Report")
                writer.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                writer.println("Thread: ${thread.name}")
                writer.println("Exception: ${throwable.javaClass.name}")
                writer.println("Message: ${throwable.message}")
                writer.println()
                writer.println("Stack Trace:")
                throwable.printStackTrace(writer)
                writer.println()
                var cause = throwable.cause
                while (cause != null) {
                    writer.println("Caused by: ${cause.javaClass.name}: ${cause.message}")
                    cause.printStackTrace(writer)
                    writer.println()
                    cause = cause.cause
                }
            }
        } catch (e: Exception) {
            // Cannot log crash file write failure
        }
    }

    private fun rotateLogsIfNeeded() {
        executor.execute {
            // Remove log files older than 7 days
            val cutoff = System.currentTimeMillis() - MAX_LOG_AGE_MS
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
            // Keep only last N crash files
            crashDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CRASH_FILES)
                ?.forEach { it.delete() }
        }
    }

    companion object {
        private const val LOG_DIR_NAME = "debug_logs"
        private const val CRASH_DIR_NAME = "crash_reports"
        private const val MAX_MEMORY_ENTRIES = 500
        private const val MAX_CRASH_FILES = 20
        private const val MAX_LOG_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
}
