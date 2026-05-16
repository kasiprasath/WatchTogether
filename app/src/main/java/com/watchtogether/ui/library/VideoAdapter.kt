package com.watchtogether.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.watchtogether.R
import com.watchtogether.data.model.VideoItem
import com.watchtogether.databinding.ItemVideoBinding

class VideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(
        private val binding: ItemVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.videoTitle.text = video.title
            binding.videoDuration.text = video.formattedDuration
            binding.videoSize.text = video.formattedSize
            binding.videoFolder.text = video.folderName

            binding.videoThumbnail.load(video.thumbnailUri) {
                crossfade(true)
                placeholder(R.drawable.ic_video_placeholder)
                error(R.drawable.ic_video_placeholder)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
            }

            binding.root.setOnClickListener {
                onVideoClick(video)
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}
