package com.watchtogether.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ConnectionHistoryEntry(
    val deviceName: String,
    val deviceAddress: String,
    val lastConnected: Long
)

class ConnectionHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addEntry(deviceName: String, deviceAddress: String) {
        val entries = getEntries().toMutableList()
        entries.removeAll { it.deviceAddress == deviceAddress }
        entries.add(
            0,
            ConnectionHistoryEntry(deviceName, deviceAddress, System.currentTimeMillis())
        )
        if (entries.size > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size).clear()
        }
        saveEntries(entries)
    }

    fun removeEntry(deviceAddress: String) {
        val entries = getEntries().filter { it.deviceAddress != deviceAddress }
        saveEntries(entries)
    }

    fun getEntries(): List<ConnectionHistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ConnectionHistoryEntry(
                    deviceName = obj.getString("name"),
                    deviceAddress = obj.getString("address"),
                    lastConnected = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveEntries(entries: List<ConnectionHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("name", entry.deviceName)
                put("address", entry.deviceAddress)
                put("timestamp", entry.lastConnected)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "connection_history"
        private const val KEY_HISTORY = "history_entries"
        private const val MAX_ENTRIES = 50
    }
}
