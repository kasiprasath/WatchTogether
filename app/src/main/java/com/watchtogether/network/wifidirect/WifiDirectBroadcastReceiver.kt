package com.watchtogether.network.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag

class WifiDirectBroadcastReceiver(
    private val wifiDirectManager: WifiDirectManager,
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?
) : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    wifiDirectManager.updateConnectionState(
                        WifiDirectManager.ConnectionState.Error("Wi-Fi Direct is not enabled")
                    )
                    AppLogger.w(LogTag.WIFI_DIRECT, "Wi-Fi Direct is not enabled")
                } else {
                    AppLogger.d(LogTag.WIFI_DIRECT, "Wi-Fi Direct is enabled")
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager?.requestPeers(channel, wifiDirectManager.peerListListener)
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                }

                if (networkInfo?.isConnected == true) {
                    manager?.requestConnectionInfo(channel, wifiDirectManager.connectionInfoListener)
                } else {
                    wifiDirectManager.updateConnectionState(
                        WifiDirectManager.ConnectionState.Disconnected
                    )
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                        WifiP2pDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                }
                device?.let { wifiDirectManager.updateThisDevice(it) }
            }
        }
    }

    companion object
}
