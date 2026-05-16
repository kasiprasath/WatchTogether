package com.watchtogether.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun updatePlaybackState(isPlaying: Boolean, position: Long) {
        _playerState.value = _playerState.value.copy(
            isPlaying = isPlaying,
            currentPosition = position
        )
    }

    fun updateBufferingState(isBuffering: Boolean) {
        _playerState.value = _playerState.value.copy(isBuffering = isBuffering)
    }

    fun updateConnectionState(isConnected: Boolean, viewerCount: Int = 0) {
        _playerState.value = _playerState.value.copy(
            isConnected = isConnected,
            viewerCount = viewerCount
        )
    }

    data class PlayerState(
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val isBuffering: Boolean = false,
        val isConnected: Boolean = false,
        val viewerCount: Int = 0,
        val error: String? = null
    )
}
