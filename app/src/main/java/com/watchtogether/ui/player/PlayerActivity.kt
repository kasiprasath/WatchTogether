package com.watchtogether.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.watchtogether.R
import com.watchtogether.data.model.SyncMessage
import com.watchtogether.databinding.ActivityPlayerBinding
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.DebugConfig
import com.watchtogether.debug.LogTag
import com.watchtogether.network.server.VideoStreamServer
import com.watchtogether.network.sync.SyncClient
import com.watchtogether.network.sync.SyncServer
import com.watchtogether.service.StreamingService
import com.watchtogether.ui.discovery.DiscoveryActivity
import com.watchtogether.util.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private var player: ExoPlayer? = null
    private var isHost: Boolean = false
    private var hostAddress: String? = null
    private var videoPath: String? = null
    private var videoTitle: String? = null

    private var streamingService: StreamingService? = null
    private var syncClient: SyncClient? = null
    private var isBound = false
    private var isSyncing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Flow breakage detection
    private var videoSelectedReceived = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as StreamingService.StreamingBinder
                streamingService = binder.getService()
                isBound = true
                AppLogger.i(LogTag.STREAM_SERVER, "Service bound successfully")
                debugOverlayInfo("Service connected")
                onServiceReady()
            } catch (e: Exception) {
                AppLogger.e(LogTag.STREAM_SERVER, "Service connection failed", e)
                showError("Service connection failed: ${e.message}")
                debugOverlayError("Service bind failed: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
            isBound = false
            AppLogger.w(LogTag.STREAM_SERVER, "Service disconnected unexpectedly")
            debugOverlayError("Service disconnected unexpectedly")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isHost = intent.getBooleanExtra(DiscoveryActivity.EXTRA_IS_HOST, false)
        hostAddress = intent.getStringExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS)
        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)

        AppLogger.i(LogTag.GENERAL, "PlayerActivity: isHost=$isHost, host=$hostAddress, video=$videoPath")

        setupUI()
        setupDebugOverlay()
        initializePlayer()

        if (isHost) {
            validateAndStartHost()
        } else {
            connectToHost()
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.videoTitle.text = videoTitle ?: "WatchTogether"

        binding.btnBack.setOnClickListener { finish() }

        val roleText = if (isHost) getString(R.string.role_host) else getString(R.string.role_viewer)
        binding.roleIndicator.text = roleText

        if (isHost) {
            binding.connectionInfo.text = getString(R.string.waiting_for_viewers)
        } else {
            binding.connectionInfo.text = getString(R.string.connecting_to_host)
        }
    }

    private fun setupDebugOverlay() {
        val overlay = binding.debugOverlay
        if (DebugConfig.isTestMode()) {
            overlay.visibility = View.VISIBLE
            overlay.attach()
            overlay.addInfo("Mode: ${DebugConfig.getMode().label}")
            overlay.addInfo("Role: ${if (isHost) "HOST" else "VIEWER"}")
            if (isHost) {
                overlay.addInfo("Video: ${videoPath ?: "none"}")
            } else {
                overlay.addInfo("Host: ${hostAddress ?: "unknown"}")
            }
        } else {
            overlay.visibility = View.GONE
        }
    }

    private fun validateAndStartHost() {
        val path = videoPath
        if (path == null) {
            val msg = "No video path provided"
            AppLogger.e(LogTag.STREAM_SERVER, msg)
            showError(msg)
            debugOverlayError(msg)
            return
        }

        val file = File(path)
        if (!file.exists()) {
            val msg = "Video file not found: $path"
            AppLogger.e(LogTag.STORAGE, msg)
            showError(msg)
            debugOverlayError(msg)
            return
        }

        if (!file.canRead()) {
            val msg = "Video file not readable: $path"
            AppLogger.e(LogTag.STORAGE, msg)
            showError(msg)
            debugOverlayError(msg)
            return
        }

        AppLogger.i(LogTag.STORAGE, "Video validated: ${file.name} (${file.length()} bytes)")
        debugOverlayInfo("Video OK: ${file.name} (${file.length() / 1024 / 1024}MB)")
        startStreamingService()
    }

    private fun initializePlayer() {
        try {
            player = ExoPlayer.Builder(this)
                .build()
                .also { exoPlayer ->
                    binding.playerView.player = exoPlayer

                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val stateName = when (playbackState) {
                                Player.STATE_BUFFERING -> "BUFFERING"
                                Player.STATE_READY -> "READY"
                                Player.STATE_ENDED -> "ENDED"
                                Player.STATE_IDLE -> "IDLE"
                                else -> "UNKNOWN($playbackState)"
                            }
                            AppLogger.d(LogTag.EXOPLAYER, "Playback state: $stateName")
                            debugOverlayInfo("Player: $stateName")
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    binding.bufferingIndicator.visibility = View.VISIBLE
                                }
                                Player.STATE_READY -> {
                                    binding.bufferingIndicator.visibility = View.GONE
                                    binding.errorText.visibility = View.GONE
                                }
                                Player.STATE_ENDED -> {
                                    if (isHost) {
                                        broadcastSync(SyncMessage.Stop)
                                    }
                                }
                                Player.STATE_IDLE -> {}
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            AppLogger.d(LogTag.PLAYER_SYNC, "isPlaying=$isPlaying, isSyncing=$isSyncing, isHost=$isHost")
                            if (isSyncing) return
                            if (isHost) {
                                val position = exoPlayer.currentPosition
                                if (isPlaying) {
                                    broadcastSync(SyncMessage.Play(position))
                                } else {
                                    broadcastSync(SyncMessage.Pause(position))
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val errorCode = error.errorCode
                            val errorMsg = "Player error [code=$errorCode]: ${error.message}"
                            AppLogger.e(LogTag.EXOPLAYER, errorMsg, error)
                            debugOverlayError(errorMsg)

                            val diagnosis = when (errorCode) {
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                                    "FLOW BREAK: Network unreachable - stream server may not be running"
                                }
                                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                                    "FLOW BREAK: Decoder failure - codec not supported on this device"
                                }
                                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                    "FLOW BREAK: Bad HTTP status - video may not be set on server"
                                }
                                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                                    "FLOW BREAK: Video container format not supported"
                                }
                                else -> {
                                    "FLOW BREAK: Playback error (code=$errorCode)"
                                }
                            }
                            AppLogger.e(LogTag.EXOPLAYER, diagnosis)
                            debugOverlayError(diagnosis)

                            binding.errorText.visibility = View.VISIBLE
                            if (DebugConfig.isTestMode()) {
                                binding.errorText.text = "$errorMsg\n\n$diagnosis"
                            } else {
                                binding.errorText.text = getString(R.string.playback_error, error.message ?: "Unknown error")
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            AppLogger.d(LogTag.EXOPLAYER, "Video size: ${videoSize.width}x${videoSize.height}")
                            debugOverlayInfo("Video: ${videoSize.width}x${videoSize.height}")
                        }
                    })

                    if (isHost && videoPath != null) {
                        val mediaItem = MediaItem.fromUri(videoPath!!)
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        debugOverlayInfo("Host player prepared with local video")
                    }
                }
            AppLogger.i(LogTag.EXOPLAYER, "ExoPlayer initialized")
        } catch (e: Exception) {
            AppLogger.e(LogTag.EXOPLAYER, "Failed to initialize ExoPlayer", e)
            showError("Player init failed: ${e.message}")
            debugOverlayError("ExoPlayer init failed: ${e.message}")
        }
    }

    private fun startStreamingService() {
        try {
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_START
            }
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            debugOverlayInfo("Starting streaming service...")

            mainHandler.postDelayed({
                if (!isBound) {
                    val msg = "FLOW BREAK: Service did not bind within ${SERVICE_BIND_TIMEOUT_MS}ms"
                    AppLogger.e(LogTag.STREAM_SERVER, msg)
                    debugOverlayError(msg)
                    showError("Streaming service failed to start")
                }
            }, SERVICE_BIND_TIMEOUT_MS)
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "Failed to start streaming service", e)
            showError("Cannot start streaming: ${e.message}")
            debugOverlayError("Service start failed: ${e.message}")
        }
    }

    private fun onServiceReady() {
        try {
            videoPath?.let { path ->
                streamingService?.setVideoPath(path)
                AppLogger.i(LogTag.STREAM_SERVER, "Video path set on server: $path")
                debugOverlayInfo("Video set on HTTP server")
                streamingService?.broadcastSyncMessage(
                    SyncMessage.VideoSelected(path, videoTitle ?: "Unknown")
                )
            }

            lifecycleScope.launch {
                try {
                    streamingService?.incomingMessages?.collectLatest { message ->
                        handleSyncMessage(message)
                    }
                } catch (e: Exception) {
                    AppLogger.e(LogTag.SOCKET, "Error collecting incoming messages", e)
                    debugOverlayError("Sync collection error: ${e.message}")
                }
            }

            lifecycleScope.launch {
                try {
                    streamingService?.clientConnected?.collectLatest { connected ->
                        runOnUiThread {
                            val count = streamingService?.syncClientCount ?: 0
                            binding.connectionInfo.text = if (count > 0) {
                                getString(R.string.viewers_connected, count)
                            } else {
                                getString(R.string.waiting_for_viewers)
                            }
                            debugOverlayInfo("Viewers: $count (event=$connected)")
                        }
                        if (connected && videoPath != null) {
                            streamingService?.broadcastSyncMessage(
                                SyncMessage.VideoSelected(videoPath!!, videoTitle ?: "Unknown")
                            )
                            AppLogger.i(LogTag.PLAYER_SYNC, "Re-broadcast VideoSelected to new client")
                            debugOverlayInfo("Re-sent VideoSelected to new viewer")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(LogTag.SOCKET, "Error collecting client events", e)
                    debugOverlayError("Client event error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "onServiceReady failed", e)
            showError("Service setup failed: ${e.message}")
            debugOverlayError("onServiceReady failed: ${e.message}")
        }
    }

    private fun connectToHost() {
        val address = hostAddress
        if (address == null) {
            val msg = "No host address provided"
            AppLogger.e(LogTag.SOCKET, msg)
            showError(msg)
            debugOverlayError(msg)
            return
        }

        try {
            debugOverlayInfo("Connecting to $address:${SyncServer.DEFAULT_PORT}...")

            syncClient = SyncClient().apply {
                connect(address, SyncServer.DEFAULT_PORT)
            }

            lifecycleScope.launch {
                try {
                    syncClient?.isConnected?.collect { connected ->
                        runOnUiThread {
                            binding.connectionInfo.text = if (connected) {
                                getString(R.string.connected_to_host)
                            } else {
                                getString(R.string.connecting_to_host)
                            }
                            if (connected) {
                                debugOverlayInfo("WebSocket connected to host")
                            } else {
                                debugOverlayWarning("WebSocket disconnected")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(LogTag.SOCKET, "Connection state error", e)
                    debugOverlayError("Connection error: ${e.message}")
                }
            }

            lifecycleScope.launch {
                try {
                    syncClient?.incomingMessages?.collectLatest { message ->
                        handleSyncMessage(message)
                    }
                } catch (e: Exception) {
                    AppLogger.e(LogTag.SOCKET, "Sync message error", e)
                    debugOverlayError("Sync message error: ${e.message}")
                }
            }

            // Flow breakage: detect if no VideoSelected arrives
            mainHandler.postDelayed({
                if (!videoSelectedReceived) {
                    val msg = "FLOW BREAK: No VideoSelected received within ${VIDEO_SELECTED_TIMEOUT_MS / 1000}s"
                    AppLogger.w(LogTag.PLAYER_SYNC, msg)
                    debugOverlayWarning(msg)
                    if (DebugConfig.isTestMode()) {
                        showError("Waiting for host to select video...\n\n(No VideoSelected sync received)")
                    }
                }
            }, VIDEO_SELECTED_TIMEOUT_MS)

            binding.bufferingIndicator.visibility = View.VISIBLE
        } catch (e: Exception) {
            AppLogger.e(LogTag.SOCKET, "Failed to connect to host", e)
            showError("Connection failed: ${e.message}")
            debugOverlayError("Connect failed: ${e.message}")
        }
    }

    private fun handleSyncMessage(message: SyncMessage) {
        AppLogger.d(LogTag.PLAYER_SYNC, "Received sync: ${message.javaClass.simpleName}")
        debugOverlayInfo("Sync: ${message.javaClass.simpleName}")
        runOnUiThread {
            try {
                isSyncing = true
                when (message) {
                    is SyncMessage.Play -> {
                        player?.seekTo(message.position)
                        player?.play()
                    }
                    is SyncMessage.Pause -> {
                        player?.seekTo(message.position)
                        player?.pause()
                    }
                    is SyncMessage.Seek -> {
                        player?.seekTo(message.position)
                    }
                    is SyncMessage.Stop -> {
                        player?.stop()
                    }
                    is SyncMessage.VideoSelected -> {
                        videoSelectedReceived = true
                        if (!isHost) {
                            val streamUrl = NetworkUtils.buildStreamUrl(
                                hostAddress ?: return@runOnUiThread,
                                VideoStreamServer.DEFAULT_PORT
                            )
                            debugOverlayInfo("Stream URL: $streamUrl")
                            val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                            val alreadyPlaying = currentUri == streamUrl &&
                                    player?.playbackState != Player.STATE_IDLE &&
                                    player?.playerError == null
                            if (!alreadyPlaying) {
                                binding.errorText.visibility = View.GONE
                                val mediaItem = MediaItem.fromUri(streamUrl)
                                player?.setMediaItem(mediaItem)
                                player?.prepare()
                                player?.playWhenReady = true
                                debugOverlayInfo("Viewer preparing stream...")
                            } else {
                                debugOverlayInfo("Already playing, skip re-prepare")
                            }
                            binding.videoTitle.text = message.videoTitle
                        }
                    }
                    is SyncMessage.BufferState -> {
                        binding.bufferingIndicator.visibility =
                            if (message.isBuffering) View.VISIBLE else View.GONE
                    }
                    is SyncMessage.Heartbeat -> { /* Keep-alive */ }
                }
                isSyncing = false
            } catch (e: Exception) {
                isSyncing = false
                AppLogger.e(LogTag.PLAYER_SYNC, "Sync handle error: ${message.javaClass.simpleName}", e)
                debugOverlayError("Sync error: ${e.message}")
            }
        }
    }

    private fun broadcastSync(message: SyncMessage) {
        AppLogger.d(LogTag.PLAYER_SYNC, "Broadcasting sync: ${message.javaClass.simpleName}")
        try {
            if (isHost) {
                streamingService?.broadcastSyncMessage(message)
            } else {
                syncClient?.sendMessage(message)
            }
        } catch (e: Exception) {
            AppLogger.e(LogTag.PLAYER_SYNC, "Sync broadcast failed", e)
            debugOverlayError("Sync broadcast failed: ${e.message}")
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = message
            binding.bufferingIndicator.visibility = View.GONE
        }
    }

    private fun debugOverlayInfo(msg: String) {
        if (DebugConfig.isTestMode()) binding.debugOverlay.addInfo(msg)
    }

    private fun debugOverlayWarning(msg: String) {
        if (DebugConfig.isTestMode()) binding.debugOverlay.addWarning(msg)
    }

    private fun debugOverlayError(msg: String) {
        if (DebugConfig.isTestMode()) binding.debugOverlay.addError(msg)
    }

    override fun onStop() {
        super.onStop()
        if (isHost) {
            val position = player?.currentPosition ?: 0L
            isSyncing = true
            broadcastSync(SyncMessage.Pause(position))
        }
        player?.pause()
        mainHandler.post { isSyncing = false }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.debugOverlay.detach()
        mainHandler.removeCallbacksAndMessages(null)

        player?.release()
        player = null

        syncClient?.disconnect()
        syncClient = null

        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                AppLogger.w(LogTag.STREAM_SERVER, "Error unbinding service", e)
            }
            isBound = false
        }

        if (isHost) {
            try {
                val intent = Intent(this, StreamingService::class.java).apply {
                    action = StreamingService.ACTION_STOP
                }
                startService(intent)
            } catch (e: Exception) {
                AppLogger.w(LogTag.STREAM_SERVER, "Error stopping service", e)
            }
        }

        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        private const val SERVICE_BIND_TIMEOUT_MS = 10000L
        private const val VIDEO_SELECTED_TIMEOUT_MS = 15000L
    }
}
