package com.watchtogether.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class WatchTogetherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val streamingChannel = NotificationChannel(
                CHANNEL_STREAMING,
                "Video Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active video streaming notification"
                setShowBadge(false)
            }

            val connectionChannel = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Device connection notifications"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(streamingChannel)
            manager.createNotificationChannel(connectionChannel)
        }
    }

    companion object {
        const val CHANNEL_STREAMING = "streaming_channel"
        const val CHANNEL_CONNECTION = "connection_channel"
    }
}
