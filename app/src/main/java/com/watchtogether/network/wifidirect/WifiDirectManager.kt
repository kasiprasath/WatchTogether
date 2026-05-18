package com.watchtogether.network.wifidirect

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.watchtogether.data.model.DeviceInfo
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import java.util.concurrent.atomic.AtomicInteger
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
        AppLogger.d(LogTag.WIFI_DIRECT, "Peers updated: ${deviceList.size} devices found")
    }

    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        _connectionInfo.value = info
        if (info.groupFormed) {
            _connectionState.value = if (info.isGroupOwner) {
                ConnectionState.ConnectedAsHost(info.groupOwnerAddress?.hostAddress ?: "")
            } else {
                ConnectionState.ConnectedAsClient(info.groupOwnerAddress?.hostAddress ?: "")
            }
            AppLogger.d(LogTag.WIFI_DIRECT, "Connected: isGroupOwner=${info.isGroupOwner}, address=${info.groupOwnerAddress?.hostAddress}")
        }
    }

    fun initialize() {
        channel = manager?.initialize(context, Looper.getMainLooper(), null)
        receiver = WifiDirectBroadcastReceiver(this, manager, channel)
        clearPersistentGroups {
            AppLogger.d(LogTag.WIFI_DIRECT, "Persistent groups cleared on init")
        }
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
            AppLogger.w(LogTag.WIFI_DIRECT, "Receiver already unregistered")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        _isDiscovering.value = true
        clearPersistentGroups {
            AppLogger.d(LogTag.WIFI_DIRECT, "Persistent groups cleared before discovery")
        }
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLogger.d(LogTag.WIFI_DIRECT, "Discovery started")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                AppLogger.e(LogTag.WIFI_DIRECT, "Discovery failed: ${getFailureReason(reason)}")
            }
        })
    }

    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isDiscovering.value = false
                AppLogger.d(LogTag.WIFI_DIRECT, "Discovery stopped")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                AppLogger.w(LogTag.WIFI_DIRECT, "Stop discovery failed: ${getFailureReason(reason)}")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DeviceInfo) {
        _connectionState.value = ConnectionState.Connecting(device.name)
        AppLogger.d(LogTag.WIFI_DIRECT, "Preparing connection to ${device.name} (${device.address})")

        // Step 1: Cancel any pending invitation from a previous attempt
        manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLogger.d(LogTag.WIFI_DIRECT, "Cancelled pending connect")
                removeGroupBeforeConnect(device)
            }
            override fun onFailure(reason: Int) {
                removeGroupBeforeConnect(device)
            }
        }) ?: removeGroupBeforeConnect(device)
    }

    @SuppressLint("MissingPermission")
    private fun removeGroupBeforeConnect(device: DeviceInfo) {
        // Step 2: Remove any active Wi-Fi Direct group
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLogger.d(LogTag.WIFI_DIRECT, "Cleared active group before connect")
                clearPersistentGroupsBeforeConnect(device)
            }
            override fun onFailure(reason: Int) {
                clearPersistentGroupsBeforeConnect(device)
            }
        }) ?: clearPersistentGroupsBeforeConnect(device)
    }

    private fun clearPersistentGroupsBeforeConnect(device: DeviceInfo) {
        // Step 3: Delete all persistent/remembered groups so the remote device
        // always shows an invitation dialog instead of auto-connecting
        clearPersistentGroups {
            AppLogger.d(LogTag.WIFI_DIRECT, "Persistent groups cleared, initiating fresh connection to ${device.name}")
            initiateConnection(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initiateConnection(device: DeviceInfo) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
            groupOwnerIntent = 15 // Force inviter to be group owner (Host)
        }

        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLogger.d(LogTag.WIFI_DIRECT, "Connection initiated to ${device.name}")
            }

            override fun onFailure(reason: Int) {
                _connectionState.value = ConnectionState.Error("Connection failed: ${getFailureReason(reason)}")
                AppLogger.e(LogTag.WIFI_DIRECT, "Connection failed: ${getFailureReason(reason)}")
            }
        })
    }

    fun disconnect() {
        // Cancel any pending invitation first
        manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLogger.d(LogTag.WIFI_DIRECT, "Pending connect cancelled")
            }
            override fun onFailure(reason: Int) {
                AppLogger.w(LogTag.WIFI_DIRECT, "Cancel connect failed (may not have been connecting): ${getFailureReason(reason)}")
            }
        })

        // Remove the Wi-Fi Direct group
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionState.value = ConnectionState.Disconnected
                _connectionInfo.value = null
                AppLogger.d(LogTag.WIFI_DIRECT, "Disconnected")
            }

            override fun onFailure(reason: Int) {
                AppLogger.w(LogTag.WIFI_DIRECT, "Remove group failed: ${getFailureReason(reason)}")
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

    private fun clearPersistentGroups(onComplete: () -> Unit) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            onComplete()
            return
        }
        try {
            val deletePersistentGroup = WifiP2pManager::class.java.getMethod(
                "deletePersistentGroup",
                WifiP2pManager.Channel::class.java,
                Int::class.javaPrimitiveType,
                WifiP2pManager.ActionListener::class.java
            )

            val totalGroups = MAX_PERSISTENT_GROUPS
            val remaining = AtomicInteger(totalGroups)

            for (netId in 0 until totalGroups) {
                try {
                    deletePersistentGroup.invoke(mgr, ch, netId, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            AppLogger.d(LogTag.WIFI_DIRECT, "Deleted persistent group netId=$netId")
                            if (remaining.decrementAndGet() == 0) onComplete()
                        }
                        override fun onFailure(reason: Int) {
                            if (remaining.decrementAndGet() == 0) onComplete()
                        }
                    })
                } catch (e: Exception) {
                    if (remaining.decrementAndGet() == 0) onComplete()
                }
            }
        } catch (e: Exception) {
            AppLogger.w(LogTag.WIFI_DIRECT, "Cannot clear persistent groups: ${e.message}")
            onComplete()
        }
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
        private const val MAX_PERSISTENT_GROUPS = 32
    }
}
