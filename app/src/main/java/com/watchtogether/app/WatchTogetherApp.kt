package com.watchtogether.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.watchtogether.debug.AnrWatchdog
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.CrashHandler
import com.watchtogether.debug.DebugConfig
import com.watchtogether.debug.FileLogger
import timber.log.Timber

class WatchTogetherApp : Application() {

    lateinit var fileLogger: FileLogger
        private set
    private var anrWatchdog: AnrWatchdog? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeLogging()
        createNotificationChannels()
    }

    private fun initializeLogging() {
        DebugConfig.init(this)
        fileLogger = FileLogger(this)
        AppLogger.init(fileLogger)

        if (isDebugBuild()) {
            Timber.plant(Timber.DebugTree())
        }

        val crashHandler = CrashHandler(fileLogger)
        crashHandler.install()

        if (isDebugBuild()) {
            anrWatchdog = AnrWatchdog(fileLogger).apply { start() }
        }

        Timber.d("WatchTogether logging initialized (mode=${DebugConfig.getMode().label})")
    }

    private fun isDebugBuild(): Boolean {
        return applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    override fun onTerminate() {
        anrWatchdog?.stop()
        super.onTerminate()
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

        @Volatile
        private var instance: WatchTogetherApp? = null

        fun getInstance(): WatchTogetherApp? = instance
    }
}
