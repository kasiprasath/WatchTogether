package com.watchtogether.ui.debug

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchtogether.R
import com.watchtogether.debug.LogEntry

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    class LogViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false) as TextView
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = getItem(position)
        holder.textView.text = entry.format()
        holder.textView.setTextColor(getColorForLevel(entry.level))
    }

    private fun getColorForLevel(level: LogEntry.Level): Int = when (level) {
        LogEntry.Level.DEBUG -> Color.parseColor("#8BC34A")
        LogEntry.Level.INFO -> Color.parseColor("#03A9F4")
        LogEntry.Level.WARN -> Color.parseColor("#FFC107")
        LogEntry.Level.ERROR -> Color.parseColor("#F44336")
        LogEntry.Level.CRASH -> Color.parseColor("#FF0000")
    }

    class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.message == newItem.message
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
