package com.watchtogether.ui.discovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchtogether.R
import com.watchtogether.data.model.DeviceInfo
import com.watchtogether.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onDeviceClick: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeviceInfo) {
            binding.deviceName.text = device.name
            binding.deviceStatus.text = device.statusText
            binding.deviceAddress.text = device.address

            val statusColor = when {
                device.isConnected -> R.color.status_connected
                device.isAvailable -> R.color.status_available
                else -> R.color.status_unavailable
            }
            binding.statusIndicator.setColorFilter(
                binding.root.context.getColor(statusColor)
            )

            val canConnect = device.isAvailable || device.isInvited || device.isConnected
            binding.root.isEnabled = canConnect
            binding.root.alpha = if (canConnect) 1.0f else 0.5f

            binding.root.setOnClickListener {
                if (canConnect) {
                    onDeviceClick(device)
                }
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem
        }
    }
}
