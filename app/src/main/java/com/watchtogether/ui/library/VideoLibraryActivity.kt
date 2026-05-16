package com.watchtogether.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.watchtogether.R
import com.watchtogether.data.model.VideoItem
import com.watchtogether.databinding.ActivityVideoLibraryBinding
import com.watchtogether.ui.discovery.DiscoveryActivity
import com.watchtogether.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class VideoLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoLibraryBinding
    private val viewModel: VideoLibraryViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter

    private var isHost: Boolean = false
    private var hostAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isHost = intent.getBooleanExtra(DiscoveryActivity.EXTRA_IS_HOST, false)
        hostAddress = intent.getStringExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS)

        setupUI()
        observeState()
        viewModel.loadVideos()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.video_library)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        videoAdapter = VideoAdapter { video ->
            onVideoSelected(video)
        }

        binding.recyclerVideos.apply {
            layoutManager = GridLayoutManager(this@VideoLibraryActivity, 2)
            adapter = videoAdapter
            setHasFixedSize(true)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadVideos()
        }

        binding.chipAll.setOnClickListener { viewModel.filterByFolder(null) }
        binding.chipRecent.setOnClickListener { viewModel.showRecent() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: VideoLibraryViewModel.LibraryUiState) {
        binding.swipeRefresh.isRefreshing = state.isLoading

        if (state.videos.isEmpty() && !state.isLoading) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerVideos.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerVideos.visibility = View.VISIBLE
        }

        videoAdapter.submitList(state.videos)

        // Update folder chips
        binding.chipGroupFolders.removeViews(2, binding.chipGroupFolders.childCount - 2)
        state.folders.forEach { folder ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = folder
                isCheckable = true
                setOnClickListener { viewModel.filterByFolder(folder) }
            }
            binding.chipGroupFolders.addView(chip)
        }
    }

    private fun onVideoSelected(video: VideoItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(DiscoveryActivity.EXTRA_IS_HOST, isHost)
            putExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS, hostAddress)
            putExtra(PlayerActivity.EXTRA_VIDEO_PATH, video.path)
            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.title)
        }
        startActivity(intent)
    }
}
