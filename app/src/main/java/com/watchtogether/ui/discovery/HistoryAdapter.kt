package com.watchtogether.ui.discovery

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchtogether.data.repository.ConnectionHistoryEntry
import com.watchtogether.databinding.ItemHistoryDeviceBinding

class HistoryAdapter(
    private val onItemClick: (ConnectionHistoryEntry) -> Unit,
    private val onRemoveClick: (ConnectionHistoryEntry) -> Unit
) : ListAdapter<ConnectionHistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ConnectionHistoryEntry) {
            binding.historyDeviceName.text = entry.deviceName
            binding.historyLastConnected.text = DateUtils.getRelativeTimeSpanString(
                entry.lastConnected,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            binding.root.setOnClickListener { onItemClick(entry) }
            binding.btnRemoveHistory.setOnClickListener { onRemoveClick(entry) }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<ConnectionHistoryEntry>() {
        override fun areItemsTheSame(
            oldItem: ConnectionHistoryEntry,
            newItem: ConnectionHistoryEntry
        ): Boolean = oldItem.deviceAddress == newItem.deviceAddress

        override fun areContentsTheSame(
            oldItem: ConnectionHistoryEntry,
            newItem: ConnectionHistoryEntry
        ): Boolean = oldItem == newItem
    }
}
