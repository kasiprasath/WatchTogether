package com.watchtogether.data.model

import org.json.JSONObject

sealed class SyncMessage {
    data class Play(val position: Long) : SyncMessage()
    data class Pause(val position: Long) : SyncMessage()
    data class Seek(val position: Long) : SyncMessage()
    data class VideoSelected(val videoPath: String, val videoTitle: String) : SyncMessage()
    data object Stop : SyncMessage()
    data class BufferState(val isBuffering: Boolean) : SyncMessage()
    data class Heartbeat(val timestamp: Long) : SyncMessage()

    fun toJson(): String {
        val json = JSONObject()
        when (this) {
            is Play -> {
                json.put("type", "play")
                json.put("position", position)
            }
            is Pause -> {
                json.put("type", "pause")
                json.put("position", position)
            }
            is Seek -> {
                json.put("type", "seek")
                json.put("position", position)
            }
            is VideoSelected -> {
                json.put("type", "video_selected")
                json.put("path", videoPath)
                json.put("title", videoTitle)
            }
            is Stop -> {
                json.put("type", "stop")
            }
            is BufferState -> {
                json.put("type", "buffer")
                json.put("buffering", isBuffering)
            }
            is Heartbeat -> {
                json.put("type", "heartbeat")
                json.put("timestamp", timestamp)
            }
        }
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): SyncMessage? {
            return try {
                val json = JSONObject(jsonString)
                when (json.getString("type")) {
                    "play" -> Play(json.getLong("position"))
                    "pause" -> Pause(json.getLong("position"))
                    "seek" -> Seek(json.getLong("position"))
                    "video_selected" -> VideoSelected(
                        json.getString("path"),
                        json.getString("title")
                    )
                    "stop" -> Stop
                    "buffer" -> BufferState(json.getBoolean("buffering"))
                    "heartbeat" -> Heartbeat(json.getLong("timestamp"))
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
