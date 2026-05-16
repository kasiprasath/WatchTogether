package com.watchtogether.network.wifidirect

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.watchtogether.data.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiDirectBroadcastReceiver? = null

    private val _peers = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val peers: StateFlow<List<DeviceInfo>> = _peers.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _thisDevice = MutableStateFlow<DeviceInfo?>(null)
    val thisDevice: StateFlow<DeviceInfo?> = _thisDevice.asStateFlow()

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val deviceList = peerList.deviceList.map { DeviceInfo.fromWifiP2pDevice(it) }
        _peers.value = deviceList
        Log.d(TAG, "Peers updated: ${deviceList.size} devices found")
    }

    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        _connectionInfo.value = info
        if (info.groupFormed) {
            _connectionState.value = if (info.isGroupOwner) {
                ConnectionState.ConnectedAsHost(info.groupOwnerAddress?.hostAddress ?: "")
            } else {
                ConnectionState.ConnectedAsClient(info.groupOwnerAddress?.hostAddress ?: "")
            }
            Log.d(TAG, "Connected: isGroupOwner=${info.isGroupOwner}, address=${info.groupOwnerAddress?.hostAddress}")
        }
    }

    fun initialize() {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        receiver = WifiDirectBroadcastReceiver(this, manager, channel)
    }

    fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver?.let { context.registerReceiver(it, intentFilter) }
    }

    fun unregisterReceiver() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver already unregistered")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        _isDiscovering.value = true
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                Log.e(TAG, "Discovery failed: ${getFailureReason(reason)}")
            }
        })
    }

    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
                Log.d(TAG, "Discovery stopped")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                Log.w(TAG, "Stop discovery failed: ${getFailureReason(reason)}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DeviceInfo) {
        _connectionState.value = ConnectionState.Connecting(device.name)
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to ${device.name}")
            }

            override fun onFailure(reason: Int) {
                _connectionState.value = ConnectionState.Error("Connection failed: ${getFailureReason(reason)}")
                Log.e(TAG, "Connection failed: ${getFailureReason(reason)}")
            }
        })
    }

    fun disconnect() {
        // Cancel any pending invitation first
        manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Pending connect cancelled")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Cancel connect failed (may not have been connecting): ${getFailureReason(reason)}")
            }
        })

        // Remove the Wi-Fi Direct group
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = ConnectionState.Disconnected
                _connectionInfo.value = null
                Log.d(TAG, "Disconnected")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Remove group failed: ${getFailureReason(reason)}")
                // Still reset state so UI isn't stuck
                _connectionState.value = ConnectionState.Disconnected
                _connectionInfo.value = null
            }
        })
    }

    fun updateThisDevice(device: WifiP2pDevice) {
        _thisDevice.value = DeviceInfo.fromWifiP2pDevice(device)
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun cleanup() {
        stopDiscovery()
        disconnect()
        unregisterReceiver()
        channel = null
    }

    private fun getFailureReason(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P not supported"
        WifiP2pManager.BUSY -> "System busy"
        WifiP2pManager.ERROR -> "Internal error"
        else -> "Unknown error ($reason)"
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data class Connecting(val deviceName: String) : ConnectionState()
        data class ConnectedAsHost(val address: String) : ConnectionState()
        data class ConnectedAsClient(val hostAddress: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    companion object {
        private const val TAG = "WifiDirectManager"
    }
}
