package com.watchtogether.ui.discovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.watchtogether.R
import com.watchtogether.data.model.DeviceInfo
import com.watchtogether.databinding.ActivityDiscoveryBinding
import com.watchtogether.network.wifidirect.WifiDirectManager
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import com.watchtogether.ui.debug.DebugActivity
import com.watchtogether.ui.library.VideoLibraryActivity
import com.watchtogether.ui.player.PlayerActivity
import com.watchtogether.util.PermissionHelper
import kotlinx.coroutines.launch

class DiscoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscoveryBinding
    private val viewModel: DiscoveryViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private var hasNavigated = false
    private var exitDialog: AlertDialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.startDiscovery()
        } else {
            Toast.makeText(this, "Permissions required for Wi-Fi Direct", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        setupBackPressHandler()
        observeState()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun showExitConfirmationDialog() {
        if (exitDialog?.isShowing == true) return
        exitDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setNegativeButton(android.R.string.no, null)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                AppLogger.d(LogTag.UI, "User confirmed exit")
                finishAffinity()
            }
            .setCancelable(true)
            .show()
    }

    private fun setupUI() {
        deviceAdapter = DeviceAdapter { device ->
            onDeviceClicked(device)
        }

        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@DiscoveryActivity)
            adapter = deviceAdapter
        }

        historyAdapter = HistoryAdapter(
            onItemClick = { entry ->
                val device = DeviceInfo(
                    name = entry.deviceName,
                    address = entry.deviceAddress,
                    status = 3 // WifiP2pDevice.AVAILABLE
                )
                viewModel.connectToDevice(device)
            },
            onRemoveClick = { entry ->
                viewModel.removeHistoryEntry(entry.deviceAddress)
            }
        )

        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@DiscoveryActivity)
            adapter = historyAdapter
        }

        binding.btnScan.setOnClickListener {
            checkPermissionsAndDiscover()
        }

        binding.btnHost.setOnClickListener {
            launchAsHost()
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        binding.swipeRefresh.setOnRefreshListener {
            checkPermissionsAndDiscover()
        }

        // Hidden debug screen access: long-press on the app title in the connection card
        binding.connectionCardTitle.setOnLongClickListener {
            AppLogger.d(LogTag.UI, "Debug screen opened via long-press")
            startActivity(Intent(this, DebugActivity::class.java))
            true
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: DiscoveryViewModel.DiscoveryUiState) {
        deviceAdapter.submitList(state.devices)
        binding.swipeRefresh.isRefreshing = state.isDiscovering

        // Connection state UI
        when (state.connectionState) {
            is WifiDirectManager.ConnectionState.Disconnected -> {
                binding.connectionStatus.text = getString(R.string.status_disconnected)
                binding.connectedDeviceName.visibility = View.GONE
                binding.connectionCardTitle.text = getString(R.string.app_name)
                binding.btnDisconnect.visibility = View.GONE
                binding.btnHost.visibility = View.VISIBLE
                binding.btnScan.visibility = View.VISIBLE
                binding.progressConnecting.visibility = View.GONE
                binding.statusDot.visibility = View.VISIBLE
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_disconnected)
                hasNavigated = false
            }
            is WifiDirectManager.ConnectionState.Connecting -> {
                binding.connectionStatus.text = getString(R.string.status_connecting, state.connectionState.deviceName)
                binding.connectedDeviceName.visibility = View.GONE
                binding.connectionCardTitle.text = getString(R.string.app_name)
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnHost.visibility = View.GONE
                binding.btnScan.visibility = View.GONE
                binding.progressConnecting.visibility = View.VISIBLE
                binding.statusDot.visibility = View.GONE
            }
            is WifiDirectManager.ConnectionState.ConnectedAsHost -> {
                binding.connectionCardTitle.text = getString(R.string.status_hosting)
                binding.connectionStatus.text = getString(R.string.connected_device_info)
                binding.connectedDeviceName.visibility = View.VISIBLE
                binding.connectedDeviceName.text = state.connectedDeviceName ?: getString(R.string.unknown_device)
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnHost.visibility = View.VISIBLE
                binding.btnScan.visibility = View.GONE
                binding.progressConnecting.visibility = View.GONE
                binding.statusDot.visibility = View.VISIBLE
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_connected)
            }
            is WifiDirectManager.ConnectionState.ConnectedAsClient -> {
                binding.connectionCardTitle.text = getString(R.string.status_connected)
                binding.connectionStatus.text = getString(R.string.connected_device_info)
                binding.connectedDeviceName.visibility = View.VISIBLE
                binding.connectedDeviceName.text = state.connectedDeviceName ?: getString(R.string.unknown_device)
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnHost.visibility = View.GONE
                binding.btnScan.visibility = View.GONE
                binding.progressConnecting.visibility = View.GONE
                binding.statusDot.visibility = View.VISIBLE
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_connected)
                if (!hasNavigated) {
                    hasNavigated = true
                    navigateToPlayer(isHost = false, hostAddress = state.connectionState.hostAddress)
                }
            }
            is WifiDirectManager.ConnectionState.Error -> {
                binding.connectionStatus.text = state.connectionState.message
                binding.connectedDeviceName.visibility = View.GONE
                binding.connectionCardTitle.text = getString(R.string.app_name)
                binding.btnDisconnect.visibility = View.GONE
                binding.btnHost.visibility = View.VISIBLE
                binding.btnScan.visibility = View.VISIBLE
                binding.progressConnecting.visibility = View.GONE
                binding.statusDot.visibility = View.VISIBLE
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_error)
            }
        }

        // Empty state
        if (state.isEmpty && !state.isDiscovering) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerDevices.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerDevices.visibility = View.VISIBLE
        }

        // Connection history
        if (state.connectionHistory.isNotEmpty()) {
            binding.historySection.visibility = View.VISIBLE
            historyAdapter.submitList(state.connectionHistory)
        } else {
            binding.historySection.visibility = View.GONE
        }
    }

    private fun onDeviceClicked(device: DeviceInfo) {
        viewModel.connectToDevice(device)
    }

    private fun launchAsHost() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
            return
        }
        val state = viewModel.uiState.value.connectionState
        if (state is WifiDirectManager.ConnectionState.ConnectedAsHost ||
            state is WifiDirectManager.ConnectionState.ConnectedAsClient
        ) {
            val address = when (state) {
                is WifiDirectManager.ConnectionState.ConnectedAsHost -> state.address
                is WifiDirectManager.ConnectionState.ConnectedAsClient -> state.hostAddress
                else -> null
            }
            navigateToLibrary(isHost = true, hostAddress = address)
        } else {
            Toast.makeText(this, getString(R.string.no_device_connected), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndDiscover() {
        if (PermissionHelper.hasAllPermissions(this)) {
            viewModel.startDiscovery()
        } else {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        }
    }

    private fun navigateToLibrary(isHost: Boolean, hostAddress: String?) {
        val intent = Intent(this, VideoLibraryActivity::class.java).apply {
            putExtra(EXTRA_IS_HOST, isHost)
            putExtra(EXTRA_HOST_ADDRESS, hostAddress)
        }
        startActivity(intent)
    }

    private fun navigateToPlayer(isHost: Boolean, hostAddress: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(EXTRA_IS_HOST, isHost)
            putExtra(EXTRA_HOST_ADDRESS, hostAddress)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.wifiDirectManager.registerReceiver()
        viewModel.loadHistory()
    }

    override fun onPause() {
        super.onPause()
        viewModel.wifiDirectManager.unregisterReceiver()
    }

    companion object {
        const val EXTRA_IS_HOST = "extra_is_host"
        const val EXTRA_HOST_ADDRESS = "extra_host_address"
    }
}
