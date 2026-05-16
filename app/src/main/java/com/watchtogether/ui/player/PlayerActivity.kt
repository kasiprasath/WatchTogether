package com.watchtogether.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.watchtogether.R
import com.watchtogether.data.model.SyncMessage
import com.watchtogether.databinding.ActivityPlayerBinding
import com.watchtogether.network.server.VideoStreamServer
import com.watchtogether.network.sync.SyncClient
import com.watchtogether.network.sync.SyncServer
import com.watchtogether.service.StreamingService
import com.watchtogether.ui.discovery.DiscoveryActivity
import com.watchtogether.util.NetworkUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StreamingService.StreamingBinder
            streamingService = binder.getService()
            isBound = true
            onServiceReady()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
            isBound = false
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

        setupUI()
        initializePlayer()

        if (isHost) {
            startStreamingService()
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

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                binding.bufferingIndicator.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.bufferingIndicator.visibility = View.GONE
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
                        Log.e(TAG, "Player error: ${error.message}")
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = getString(R.string.playback_error, error.message ?: "Unknown error")
                    }
                })

                if (isHost && videoPath != null) {
                    val mediaItem = MediaItem.fromUri(videoPath!!)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                }
            }
    }

    private fun startStreamingService() {
        val intent = Intent(this, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
        }
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun onServiceReady() {
        videoPath?.let { path ->
            streamingService?.setVideoPath(path)
            streamingService?.broadcastSyncMessage(
                SyncMessage.VideoSelected(path, videoTitle ?: "Unknown")
            )
        }

        lifecycleScope.launch {
            streamingService?.incomingMessages?.collectLatest { message ->
                handleSyncMessage(message)
            }
        }

        lifecycleScope.launch {
            streamingService?.clientConnected?.collectLatest { connected ->
                runOnUiThread {
                    val count = streamingService?.syncClientCount ?: 0
                    binding.connectionInfo.text = if (count > 0) {
                        getString(R.string.viewers_connected, count)
                    } else {
                        getString(R.string.waiting_for_viewers)
                    }
                }
            }
        }
    }

    private fun connectToHost() {
        val address = hostAddress ?: return
        syncClient = SyncClient().apply {
            connect(address, SyncServer.DEFAULT_PORT)
        }

        lifecycleScope.launch {
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

        lifecycleScope.launch {
            syncClient?.incomingMessages?.collectLatest { message ->
                handleSyncMessage(message)
            }
        }

        val streamUrl = NetworkUtils.buildStreamUrl(address, VideoStreamServer.DEFAULT_PORT)
        val mediaItem = MediaItem.fromUri(streamUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    private fun handleSyncMessage(message: SyncMessage) {
        runOnUiThread {
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
                    if (!isHost) {
                        val streamUrl = NetworkUtils.buildStreamUrl(
                            hostAddress ?: return@runOnUiThread,
                            VideoStreamServer.DEFAULT_PORT
                        )
                        val mediaItem = MediaItem.fromUri(streamUrl)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        player?.play()
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
        }
    }

    private fun broadcastSync(message: SyncMessage) {
        if (isHost) {
            streamingService?.broadcastSyncMessage(message)
        } else {
            syncClient?.sendMessage(message)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isHost) {
            val position = player?.currentPosition ?: 0L
            broadcastSync(SyncMessage.Pause(position))
        }
        player?.pause()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player?.release()
        player = null

        syncClient?.disconnect()
        syncClient = null

        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }

        if (isHost) {
            val intent = Intent(this, StreamingService::class.java).apply {
                action = StreamingService.ACTION_STOP
            }
            startService(intent)
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }
}
