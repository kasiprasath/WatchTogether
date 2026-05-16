package com.watchtogether.ui.discovery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchtogether.data.model.DeviceInfo
import com.watchtogether.network.wifidirect.WifiDirectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {

    val wifiDirectManager = WifiDirectManager(application)

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        wifiDirectManager.initialize()

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
                _uiState.value = _uiState.value.copy(connectionState = state)
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
        wifiDirectManager.registerReceiver()
        wifiDirectManager.discoverPeers()
    }

    fun stopDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    fun connectToDevice(device: DeviceInfo) {
        wifiDirectManager.connectToDevice(device)
    }

    fun disconnect() {
        wifiDirectManager.disconnect()
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
        val thisDevice: DeviceInfo? = null
    )
}
