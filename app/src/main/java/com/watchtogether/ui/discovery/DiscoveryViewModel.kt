package com.watchtogether.ui.discovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchtogether.data.model.DeviceInfo
import com.watchtogether.data.repository.ConnectionHistoryEntry
import com.watchtogether.data.repository.ConnectionHistoryManager
import com.watchtogether.network.wifidirect.WifiDirectManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {

    val wifiDirectManager = WifiDirectManager(application)
    val historyManager = ConnectionHistoryManager(application)

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()
    private var pendingConnectionDevice: DeviceInfo? = null
    private var discoveryTimeoutJob: Job? = null

    init {
        wifiDirectManager.initialize()
        loadHistory()

        viewModelScope.launch {
            wifiDirectManager.peers.collect { peers ->
                _uiState.value = _uiState.value.copy(
                    devices = peers,
                    isEmpty = peers.isEmpty()
                )
            }
        }

        viewModelScope.launch {
            wifiDirectManager.connectionState.collect { state ->
                when (state) {
                    is WifiDirectManager.ConnectionState.ConnectedAsHost,
                    is WifiDirectManager.ConnectionState.ConnectedAsClient -> {
                        val name = resolveConnectedDeviceName()
                        _uiState.value = _uiState.value.copy(
                            connectionState = state,
                            connectedDeviceName = name
                        )
                        saveCurrentConnection(state, name)
                    }
                    is WifiDirectManager.ConnectionState.Disconnected -> {
                        pendingConnectionDevice = null
                        _uiState.value = _uiState.value.copy(
                            connectionState = state,
                            connectedDeviceName = null
                        )
                    }
                    is WifiDirectManager.ConnectionState.Error -> {
                        pendingConnectionDevice = null
                        _uiState.value = _uiState.value.copy(
                            connectionState = state,
                            connectedDeviceName = null
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(connectionState = state)
                    }
                }
            }
        }

        viewModelScope.launch {
            wifiDirectManager.isDiscovering.collect { discovering ->
                _uiState.value = _uiState.value.copy(isDiscovering = discovering)
            }
        }

        viewModelScope.launch {
            wifiDirectManager.thisDevice.collect { device ->
                _uiState.value = _uiState.value.copy(thisDevice = device)
            }
        }
    }

    fun startDiscovery() {
        discoveryTimeoutJob?.cancel()
        wifiDirectManager.discoverPeers()
        discoveryTimeoutJob = viewModelScope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            wifiDirectManager.stopDiscovery()
        }
    }

    fun stopDiscovery() {
        discoveryTimeoutJob?.cancel()
        wifiDirectManager.stopDiscovery()
    }

    fun connectToDevice(device: DeviceInfo) {
        pendingConnectionDevice = device
        wifiDirectManager.connectToDevice(device)
    }

    fun disconnect() {
        wifiDirectManager.disconnect()
    }

    fun removeHistoryEntry(deviceAddress: String) {
        historyManager.removeEntry(deviceAddress)
        loadHistory()
    }

    fun loadHistory() {
        _uiState.value = _uiState.value.copy(
            connectionHistory = historyManager.getEntries()
        )
    }

    private fun resolveConnectedDeviceName(): String? {
        pendingConnectionDevice?.name?.let { return it }
        val currentName = _uiState.value.connectedDeviceName
        if (currentName != null) return currentName
        val peers = wifiDirectManager.peers.value
        val connectedPeer = peers.firstOrNull { it.isConnected || it.isInvited }
        return connectedPeer?.name
    }

    private fun saveCurrentConnection(state: WifiDirectManager.ConnectionState, deviceName: String?) {
        val device = pendingConnectionDevice
        if (device != null) {
            historyManager.addEntry(device.name, device.address)
            loadHistory()
            pendingConnectionDevice = null
        } else if (deviceName != null) {
            val peers = wifiDirectManager.peers.value
            val connectedPeer = peers.firstOrNull { it.isConnected || it.isInvited }
            if (connectedPeer != null) {
                historyManager.addEntry(connectedPeer.name, connectedPeer.address)
                loadHistory()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.cleanup()
    }

    data class DiscoveryUiState(
        val devices: List<DeviceInfo> = emptyList(),
        val connectionState: WifiDirectManager.ConnectionState = WifiDirectManager.ConnectionState.Disconnected,
        val isDiscovering: Boolean = false,
        val isEmpty: Boolean = true,
        val thisDevice: DeviceInfo? = null,
        val connectionHistory: List<ConnectionHistoryEntry> = emptyList(),
        val connectedDeviceName: String? = null
    )

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 15_000L
    }
}
