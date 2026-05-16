package com.watchtogether.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchtogether.data.model.VideoItem
import com.watchtogether.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allVideos: List<VideoItem> = emptyList()
    private var videoFolders: Map<String, List<VideoItem>> = emptyMap()

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                allVideos = repository.loadVideos()
                videoFolders = allVideos.groupBy { it.folderName }
                _uiState.value = LibraryUiState(
                    videos = allVideos,
                    folders = videoFolders.keys.toList(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun filterByFolder(folder: String?) {
        val filtered = if (folder == null) {
            allVideos
        } else {
            videoFolders[folder] ?: emptyList()
        }
        _uiState.value = _uiState.value.copy(
            videos = filtered,
            selectedFolder = folder
        )
    }

    fun showRecent() {
        val recent = allVideos.sortedByDescending { it.dateAdded }.take(20)
        _uiState.value = _uiState.value.copy(
            videos = recent,
            selectedFolder = "recent"
        )
    }

    data class LibraryUiState(
        val videos: List<VideoItem> = emptyList(),
        val folders: List<String> = emptyList(),
        val selectedFolder: String? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )
}
