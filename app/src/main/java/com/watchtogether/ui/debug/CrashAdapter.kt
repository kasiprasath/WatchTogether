package com.watchtogether.ui.debug

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watchtogether.R

class CrashAdapter : ListAdapter<String, CrashAdapter.CrashViewHolder>(CrashDiffCallback()) {

    class CrashViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrashViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false) as TextView
        return CrashViewHolder(view)
    }

    override fun onBindViewHolder(holder: CrashViewHolder, position: Int) {
        holder.textView.text = getItem(position)
        holder.textView.setTextColor(Color.parseColor("#F44336"))
    }

    class CrashDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
