package com.watchtogether.ui.lobby

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.watchtogether.R
import com.watchtogether.data.model.SyncMessage
import com.watchtogether.databinding.ActivityLobbyBinding
import com.watchtogether.debug.AppLogger
import com.watchtogether.debug.LogTag
import com.watchtogether.network.server.StreamProxy
import com.watchtogether.network.server.VideoStreamServer
import com.watchtogether.network.sync.SyncClient
import com.watchtogether.network.sync.SyncServer
import com.watchtogether.ui.discovery.DiscoveryActivity
import com.watchtogether.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LobbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLobbyBinding
    private var syncClient: SyncClient? = null
    private var hostAddress: String? = null
    private var isHost: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownRunnable: Runnable? = null
    private var hasNavigatedToPlayer = false

    // Pre-buffering: start loading video data during countdown
    private var preBufferPlayer: ExoPlayer? = null
    private var streamProxy: StreamProxy? = null
    private var proxyPort: Int = 0
    private var preBufferedBytes: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hostAddress = intent.getStringExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS)
        isHost = intent.getBooleanExtra(DiscoveryActivity.EXTRA_IS_HOST, false)

        AppLogger.i(LogTag.UI, "LobbyActivity started: isHost=$isHost, host=$hostAddress")

        setupUI()
        connectToHost()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.roleText.text = if (isHost) {
            getString(R.string.role_host)
        } else {
            getString(R.string.role_viewer)
        }

        binding.statusText.text = if (isHost) {
            getString(R.string.lobby_host_selecting)
        } else {
            getString(R.string.lobby_waiting_for_host)
        }

        binding.btnRequestHost.setOnClickListener {
            requestRoleSwap()
        }

        // Only viewers can request to become host
        binding.btnRequestHost.visibility = if (isHost) View.GONE else View.VISIBLE

        binding.btnDisconnect.setOnClickListener {
            AppLogger.d(LogTag.UI, "User disconnected from lobby")
            finish()
        }
    }

    private fun connectToHost() {
        val address = hostAddress
        if (address == null) {
            AppLogger.e(LogTag.SOCKET, "No host address for lobby")
            Toast.makeText(this, "No host address", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        syncClient = SyncClient().apply {
            connect(address, SyncServer.DEFAULT_PORT)
        }

        scope.launch {
            syncClient?.isConnected?.collect { connected ->
                runOnUiThread {
                    binding.connectionInfo.text = if (connected) {
                        getString(R.string.connected_to_host)
                    } else {
                        getString(R.string.connecting_to_host)
                    }
                }
            }
        }

        scope.launch {
            try {
                syncClient?.incomingMessages?.collectLatest { message ->
                    runOnUiThread {
                        handleSyncMessage(message)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(LogTag.SOCKET, "Error collecting messages in lobby", e)
            }
        }
    }

    private fun handleSyncMessage(message: SyncMessage) {
        when (message) {
            is SyncMessage.VideoSelected -> {
                if (!hasNavigatedToPlayer) {
                    AppLogger.i(LogTag.PLAYER_SYNC, "Host selected video: ${message.videoTitle}")
                    startBufferCountdown(message)
                }
            }
            is SyncMessage.BufferCountdown -> {
                showCountdown(message.secondsRemaining)
            }
            is SyncMessage.Disconnect -> {
                AppLogger.i(LogTag.PLAYER_SYNC, "Host disconnected from lobby")
                Toast.makeText(this, "Host disconnected", Toast.LENGTH_SHORT).show()
                finish()
            }
            is SyncMessage.ReturnToLobby -> {
                AppLogger.i(LogTag.PLAYER_SYNC, "Returned to lobby — host is selecting new video")
                resetToWaiting()
            }
            is SyncMessage.RoleSwapRequest -> {
                if (isHost) {
                    showRoleSwapDialog(message.requesterName)
                }
            }
            is SyncMessage.RoleSwapResponse -> {
                handleRoleSwapResponse(message.accepted)
            }
            is SyncMessage.Heartbeat -> { /* Keep-alive */ }
            else -> {
                AppLogger.d(LogTag.PLAYER_SYNC, "Lobby ignoring message: ${message.javaClass.simpleName}")
            }
        }
    }

    private fun startBufferCountdown(videoMessage: SyncMessage.VideoSelected) {
        binding.waitingState.visibility = View.GONE
        binding.countdownState.visibility = View.VISIBLE
        binding.btnRequestHost.isEnabled = false

        // Start pre-buffering video data from host during countdown
        startPreBuffering(videoMessage)

        var secondsLeft = BUFFER_COUNTDOWN_SECONDS
        binding.countdownNumber.text = secondsLeft.toString()
        binding.countdownLabel.text = getString(R.string.lobby_buffering)

        countdownRunnable?.let { mainHandler.removeCallbacks(it) }

        val tick = object : Runnable {
            override fun run() {
                secondsLeft--
                if (secondsLeft > 0) {
                    binding.countdownNumber.text = secondsLeft.toString()
                    val bufferedKb = preBufferedBytes / 1024
                    binding.countdownLabel.text = getString(R.string.lobby_starting_in, secondsLeft)
                    AppLogger.d(LogTag.STREAM_SERVER, "Buffer countdown: ${secondsLeft}s, buffered=${bufferedKb}KB")
                    mainHandler.postDelayed(this, 1000)
                } else {
                    stopPreBuffering()
                    navigateToPlayer(videoMessage)
                }
            }
        }
        countdownRunnable = tick
        mainHandler.postDelayed(tick, 1000)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun startPreBuffering(videoMessage: SyncMessage.VideoSelected) {
        val address = hostAddress ?: return

        try {
            // Start TCP proxy to tunnel HTTP through Wi-Fi Direct
            streamProxy = StreamProxy(address, VideoStreamServer.DEFAULT_PORT)
            proxyPort = streamProxy!!.start()
            AppLogger.i(LogTag.STREAM_SERVER, "Lobby pre-buffer proxy started on localhost:$proxyPort")

            val streamUrl = "http://127.0.0.1:$proxyPort/video"

            // Create a lightweight ExoPlayer to pre-buffer video data
            preBufferPlayer = ExoPlayer.Builder(this)
                .build()
                .apply {
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    setMediaItem(mediaItem)
                    // Prepare but don't play — ExoPlayer downloads data into buffer
                    playWhenReady = false
                    prepare()

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    AppLogger.d(LogTag.STREAM_SERVER, "Pre-buffer: loading video data...")
                                }
                                Player.STATE_READY -> {
                                    preBufferedBytes = bufferedPosition
                                    AppLogger.i(LogTag.STREAM_SERVER, "Pre-buffer: ready, buffered=${preBufferedBytes / 1024}KB")
                                }
                                else -> {}
                            }
                        }
                    })
                }
        } catch (e: Exception) {
            AppLogger.e(LogTag.STREAM_SERVER, "Pre-buffer failed to start", e)
        }
    }

    private fun stopPreBuffering() {
        preBufferPlayer?.let { p ->
            preBufferedBytes = p.bufferedPosition
            AppLogger.i(LogTag.STREAM_SERVER, "Pre-buffer stopped: buffered=${preBufferedBytes / 1024}KB")
            p.release()
        }
        preBufferPlayer = null
        // Don't stop streamProxy here — PlayerActivity will create its own
        // (proxy is bound to specific port, would conflict)
        streamProxy?.stop()
        streamProxy = null
    }

    private fun showCountdown(seconds: Int) {
        binding.waitingState.visibility = View.GONE
        binding.countdownState.visibility = View.VISIBLE
        binding.btnRequestHost.isEnabled = false

        binding.countdownNumber.text = seconds.toString()
        binding.countdownLabel.text = if (seconds > 0) {
            getString(R.string.lobby_starting_in, seconds)
        } else {
            getString(R.string.lobby_buffering)
        }
    }

    private fun navigateToPlayer(videoMessage: SyncMessage.VideoSelected) {
        if (hasNavigatedToPlayer) return
        hasNavigatedToPlayer = true

        // Disconnect lobby sync client — PlayerActivity will create its own
        syncClient?.disconnect()
        syncClient = null

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(DiscoveryActivity.EXTRA_IS_HOST, false)
            putExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS, hostAddress)
            putExtra(PlayerActivity.EXTRA_VIDEO_PATH, videoMessage.videoPath)
            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, videoMessage.videoTitle)
        }
        startActivity(intent)
        AppLogger.i(LogTag.UI, "Navigating to player from lobby: ${videoMessage.videoTitle}")
        finish()
    }

    private fun resetToWaiting() {
        hasNavigatedToPlayer = false
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        stopPreBuffering()
        binding.waitingState.visibility = View.VISIBLE
        binding.countdownState.visibility = View.GONE
        binding.btnRequestHost.isEnabled = true
        binding.statusText.text = getString(R.string.lobby_waiting_for_host)
    }

    private fun requestRoleSwap() {
        val deviceName = android.os.Build.MODEL
        syncClient?.sendMessage(SyncMessage.RoleSwapRequest(deviceName))
        binding.btnRequestHost.isEnabled = false
        Toast.makeText(this, getString(R.string.lobby_role_swap_sent), Toast.LENGTH_SHORT).show()
        AppLogger.i(LogTag.UI, "Role swap requested by $deviceName")

        // Re-enable after timeout if no response
        mainHandler.postDelayed({
            if (!isFinishing) {
                binding.btnRequestHost.isEnabled = true
            }
        }, ROLE_SWAP_TIMEOUT_MS)
    }

    private fun showRoleSwapDialog(requesterName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lobby_role_swap_title)
            .setMessage(getString(R.string.lobby_role_swap_message, requesterName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                syncClient?.sendMessage(SyncMessage.RoleSwapResponse(accepted = true))
                AppLogger.i(LogTag.UI, "Role swap accepted for $requesterName")
                // Swap: this device becomes viewer
                isHost = false
                binding.roleText.text = getString(R.string.role_viewer)
                binding.btnRequestHost.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.lobby_waiting_for_host)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                syncClient?.sendMessage(SyncMessage.RoleSwapResponse(accepted = false))
                AppLogger.i(LogTag.UI, "Role swap rejected for $requesterName")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleRoleSwapResponse(accepted: Boolean) {
        if (accepted) {
            Toast.makeText(this, getString(R.string.lobby_role_swap_accepted), Toast.LENGTH_SHORT).show()
            AppLogger.i(LogTag.UI, "Role swap accepted — now host")
            // This viewer is now the host
            isHost = true
            binding.roleText.text = getString(R.string.role_host)
            binding.btnRequestHost.visibility = View.GONE
            binding.statusText.text = getString(R.string.lobby_host_selecting)

            // Navigate to video library to select a video
            syncClient?.disconnect()
            syncClient = null
            val intent = Intent(this, com.watchtogether.ui.library.VideoLibraryActivity::class.java).apply {
                putExtra(DiscoveryActivity.EXTRA_IS_HOST, true)
                putExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS, hostAddress)
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, getString(R.string.lobby_role_swap_rejected), Toast.LENGTH_SHORT).show()
            binding.btnRequestHost.isEnabled = true
            AppLogger.i(LogTag.UI, "Role swap rejected")
        }
    }

    override fun onDestroy() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.removeCallbacksAndMessages(null)
        stopPreBuffering()
        syncClient?.disconnect()
        syncClient = null
        scope.cancel()
        super.onDestroy()
        AppLogger.d(LogTag.UI, "LobbyActivity destroyed")
    }

    companion object {
        const val BUFFER_COUNTDOWN_SECONDS = 4
        private const val ROLE_SWAP_TIMEOUT_MS = 10000L
    }
}
