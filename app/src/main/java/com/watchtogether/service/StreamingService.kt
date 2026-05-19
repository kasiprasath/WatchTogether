package com.watchtogether.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.watchtogether.R
import com.watchtogether.app.WatchTogetherApp
import com.watchtogether.data.model.SyncMessage
import com.watchtogether.network.server.VideoStreamServer
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import com.watchtogether.network.sync.SyncServer
import com.watchtogether.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class StreamingService : Service() {

    private val binder = StreamingBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var videoServer: VideoStreamServer? = null
    private var syncServer: SyncServer? = null

    val incomingMessages: SharedFlow<SyncMessage>?
        get() = syncServer?.incomingMessages

    val clientConnected: SharedFlow<Boolean>?
        get() = syncServer?.clientConnected

    val isServerRunning: Boolean
        get() = videoServer != null

    val syncClientCount: Int
        get() = syncServer?.clientCount ?: 0

    inner class StreamingBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(LogTag.STREAM_SERVER, "StreamingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }
        return START_NOT_STICKY
    }

    fun startStreaming() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Failed to start foreground service", e)
            stopSelf()
            return
        }

        try {
            if (videoServer == null) {
                videoServer = VideoStreamServer().apply { startServer() }
            }
            AppLogger.i(LogTag.STREAM_SERVER, "Video server started on port ${VideoStreamServer.DEFAULT_PORT}")
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: Video server failed to start on port ${VideoStreamServer.DEFAULT_PORT}", e)
        }

        try {
            if (syncServer == null) {
                syncServer = SyncServer().apply { startServer() }
            }
            AppLogger.i(LogTag.SOCKET, "Sync server started on port ${SyncServer.DEFAULT_PORT}")
        } catch (e: Exception) {
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: Sync server failed to start on port ${SyncServer.DEFAULT_PORT}", e)
        }

        AppLogger.i(LogTag.STREAM_SERVER, "Streaming started (video=${videoServer != null}, sync=${syncServer != null})")
    }

    fun stopStreaming() {
        // Broadcast disconnect to all viewers before stopping servers
        try {
            syncServer?.broadcastMessage(SyncMessage.Disconnect)
        } catch (e: Exception) {
            AppLogger.w(LogTag.SOCKET, "Failed to broadcast disconnect before stop", e)
        }
        try {
            videoServer?.stopServer()
        } catch (e: Exception) {
            AppLogger.w(LogTag.STREAM_SERVER, "Error stopping video server", e)
        }
        videoServer = null
        try {
            syncServer?.stopServer()
        } catch (e: Exception) {
            AppLogger.w(LogTag.SOCKET, "Error stopping sync server", e)
        }
        syncServer = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            AppLogger.w(LogTag.STREAM_SERVER, "Error stopping foreground service", e)
        }
        AppLogger.d(LogTag.STREAM_SERVER, "Streaming stopped")
    }

    fun setVideoPath(path: String) {
        if (videoServer == null) {
            AppLogger.e(LogTag.STREAM_SERVER, "FLOW BREAK: setVideoPath called but video server is null")
            return
        }
        videoServer?.setVideoPath(path)
    }

    fun broadcastSyncMessage(message: SyncMessage) {
        if (syncServer == null) {
            AppLogger.e(LogTag.SOCKET, "FLOW BREAK: broadcastSyncMessage called but sync server is null")
            return
        }
        serviceScope.launch(syncDispatcher) {
            try {
                syncServer?.broadcastMessage(message)
            } catch (e: Exception) {
                AppLogger.e(LogTag.SOCKET, "FLOW BREAK: broadcastSyncMessage failed", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WatchTogetherApp.CHANNEL_STREAMING)
            .setContentTitle("WatchTogether")
            .setContentText("Streaming video to connected devices")
            .setSmallIcon(R.drawable.ic_stream)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopStreaming()
        serviceScope.cancel()
        super.onDestroy()
        AppLogger.d(LogTag.STREAM_SERVER, "StreamingService destroyed")
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.watchtogether.action.START_STREAMING"
        const val ACTION_STOP = "com.watchtogether.action.STOP_STREAMING"
    }
}
