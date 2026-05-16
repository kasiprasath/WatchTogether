package com.watchtogether.data.model

import android.net.wifi.p2p.WifiP2pDevice

data class DeviceInfo(
    val name: String,
    val address: String,
    val status: Int,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null
) {
    val statusText: String
        get() = when (status) {
            WifiP2pDevice.AVAILABLE -> "Available"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.FAILED -> "Failed"
            WifiP2pDevice.UNAVAILABLE -> "Unavailable"
            else -> "Unknown"
        }

    val isAvailable: Boolean
        get() = status == WifiP2pDevice.AVAILABLE

    val isConnected: Boolean
        get() = status == WifiP2pDevice.CONNECTED

    companion object {
        fun fromWifiP2pDevice(device: WifiP2pDevice): DeviceInfo {
            return DeviceInfo(
                name = device.deviceName.ifEmpty { device.deviceAddress },
                address = device.deviceAddress,
                status = device.status
            )
        }
    }
}
