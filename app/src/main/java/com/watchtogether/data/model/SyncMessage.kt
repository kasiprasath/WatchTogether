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
    data object Disconnect : SyncMessage()
    data object ReturnToLobby : SyncMessage()
    data class BufferCountdown(val secondsRemaining: Int) : SyncMessage()
    data class RoleSwapRequest(val requesterName: String) : SyncMessage()
    data class RoleSwapResponse(val accepted: Boolean) : SyncMessage()

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
            is Disconnect -> {
                json.put("type", "disconnect")
            }
            is ReturnToLobby -> {
                json.put("type", "return_to_lobby")
            }
            is BufferCountdown -> {
                json.put("type", "buffer_countdown")
                json.put("seconds", secondsRemaining)
            }
            is RoleSwapRequest -> {
                json.put("type", "role_swap_request")
                json.put("requester", requesterName)
            }
            is RoleSwapResponse -> {
                json.put("type", "role_swap_response")
                json.put("accepted", accepted)
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
                    "disconnect" -> Disconnect
                    "return_to_lobby" -> ReturnToLobby
                    "buffer_countdown" -> BufferCountdown(json.getInt("seconds"))
                    "role_swap_request" -> RoleSwapRequest(json.getString("requester"))
                    "role_swap_response" -> RoleSwapResponse(json.getBoolean("accepted"))
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
