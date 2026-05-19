package com.watchtogether.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.watchtogether.R
import com.watchtogether.app.WatchTogetherApp
import com.watchtogether.databinding.ActivityDebugBinding
import com.watchtogether.debug.LogEntry
import com.watchtogether.debug.LogTag

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding
    private val logAdapter = LogAdapter()
    private val crashAdapter = CrashAdapter()
    private var currentFilter: LogTag? = null
    private var currentTab = TAB_LOGS

    private val logListener: (LogEntry) -> Unit = { _ ->
        runOnUiThread {
            if (currentTab == TAB_LOGS) {
                refreshLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        setupLogList()
        setupCrashList()
        setupFilters()
        setupActions()
        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        getFileLogger()?.addListener(logListener)
        refreshCurrentTab()
    }

    override fun onPause() {
        super.onPause()
        getFileLogger()?.removeListener(logListener)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_logs))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_crashes))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_status))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showTab(position: Int) {
        binding.logsContainer.visibility = if (position == TAB_LOGS) View.VISIBLE else View.GONE
        binding.crashesContainer.visibility = if (position == TAB_CRASHES) View.VISIBLE else View.GONE
        binding.statusContainer.visibility = if (position == TAB_STATUS) View.VISIBLE else View.GONE
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            TAB_LOGS -> refreshLogs()
            TAB_CRASHES -> refreshCrashes()
            TAB_STATUS -> refreshStatus()
        }
    }

    private fun setupLogList() {
        binding.recyclerLogs.apply {
            layoutManager = LinearLayoutManager(this@DebugActivity).apply {
                stackFromEnd = true
            }
            adapter = logAdapter
        }
    }

    private fun setupCrashList() {
        binding.recyclerCrashes.apply {
            layoutManager = LinearLayoutManager(this@DebugActivity)
            adapter = crashAdapter
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            currentFilter = null
            refreshLogs()
        }
        binding.chipWifi.setOnClickListener {
            currentFilter = LogTag.WIFI_DIRECT
            refreshLogs()
        }
        binding.chipStream.setOnClickListener {
            currentFilter = LogTag.STREAM_SERVER
            refreshLogs()
        }
        binding.chipPlayer.setOnClickListener {
            currentFilter = LogTag.EXOPLAYER
            refreshLogs()
        }
        binding.chipSocket.setOnClickListener {
            currentFilter = LogTag.SOCKET
            refreshLogs()
        }
        binding.chipCrash.setOnClickListener {
            currentFilter = LogTag.CRASH
            refreshLogs()
        }
    }

    private fun setupActions() {
        binding.btnCopyLogs.setOnClickListener { copyLogsToClipboard() }
        binding.btnExportTxt.setOnClickListener { exportAsTxt() }
        binding.btnExportZip.setOnClickListener { exportAsZip() }
        binding.btnClearLogs.setOnClickListener { confirmClearLogs() }
    }

    private fun refreshLogs() {
        val fileLogger = getFileLogger() ?: return
        val logs = if (currentFilter != null) {
            fileLogger.recentLogs.filter { it.tag == currentFilter }
        } else {
            fileLogger.recentLogs
        }
        logAdapter.submitList(logs.toList()) {
            if (logs.isNotEmpty()) {
                binding.recyclerLogs.scrollToPosition(logs.size - 1)
            }
        }
    }

    private fun refreshCrashes() {
        val fileLogger = getFileLogger() ?: return
        val crashes = fileLogger.getCrashHistory()
        if (crashes.isEmpty()) {
            binding.crashEmptyState.visibility = View.VISIBLE
            binding.recyclerCrashes.visibility = View.GONE
        } else {
            binding.crashEmptyState.visibility = View.GONE
            binding.recyclerCrashes.visibility = View.VISIBLE
            crashAdapter.submitList(crashes)
        }
    }

    private fun refreshStatus() {
        if (application !is WatchTogetherApp) return

        binding.txtConnectionStatus.text = getString(
            R.string.status_connection_detail,
            "Check Discovery screen for live connection state"
        )

        val logCount = getFileLogger()?.recentLogs?.size ?: 0
        val logFileCount = getFileLogger()?.getLogFiles()?.size ?: 0
        binding.txtStreamStatus.text = getString(
            R.string.status_stream_detail,
            logCount.toString(),
            logFileCount.toString()
        )

        binding.txtPlayerStatus.text = getString(
            R.string.status_player_detail,
            getFileLogger()?.recentLogs?.count { it.tag == LogTag.EXOPLAYER }.toString()
        )

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "unknown" }
        val versionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toString()
            }
        } catch (e: Exception) { "unknown" }

        binding.txtAppInfo.text = getString(
            R.string.status_app_detail,
            versionName,
            versionCode,
            Build.MODEL,
            Build.VERSION.SDK_INT.toString(),
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) "Debug" else "Release"
        )
    }

    private fun copyLogsToClipboard() {
        val fileLogger = getFileLogger() ?: return
        val text = fileLogger.recentLogs.joinToString("\n") { it.format() }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WatchTogether Logs", text))
        Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
    }

    private fun exportAsTxt() {
        val fileLogger = getFileLogger() ?: return
        try {
            val file = fileLogger.exportLogsAsText()
            shareFile(file, "text/plain")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAsZip() {
        val fileLogger = getFileLogger() ?: return
        try {
            val file = fileLogger.exportLogsAsZip(this)
            shareFile(file, "application/zip")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: java.io.File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_logs)))
    }

    private fun confirmClearLogs() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_logs_title)
            .setMessage(R.string.clear_logs_message)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                getFileLogger()?.clearLogs()
                getFileLogger()?.clearCrashes()
                refreshCurrentTab()
                Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getFileLogger() = (application as? WatchTogetherApp)?.fileLogger

    companion object {
        private const val TAB_LOGS = 0
        private const val TAB_CRASHES = 1
        private const val TAB_STATUS = 2
    }
}
