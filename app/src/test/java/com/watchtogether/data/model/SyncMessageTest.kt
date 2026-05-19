package com.watchtogether.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMessageTest {

    @Test
    fun `Play message serializes and deserializes correctly`() {
        val original = SyncMessage.Play(position = 12345L)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Play)
        assertEquals(12345L, (parsed as SyncMessage.Play).position)
    }

    @Test
    fun `Pause message serializes and deserializes correctly`() {
        val original = SyncMessage.Pause(position = 67890L)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Pause)
        assertEquals(67890L, (parsed as SyncMessage.Pause).position)
    }

    @Test
    fun `Seek message serializes and deserializes correctly`() {
        val original = SyncMessage.Seek(position = 5000L)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Seek)
        assertEquals(5000L, (parsed as SyncMessage.Seek).position)
    }

    @Test
    fun `VideoSelected message serializes and deserializes correctly`() {
        val original = SyncMessage.VideoSelected(
            videoPath = "/storage/emulated/0/Movies/test.mp4",
            videoTitle = "Test Video"
        )
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.VideoSelected)
        val vs = parsed as SyncMessage.VideoSelected
        assertEquals("/storage/emulated/0/Movies/test.mp4", vs.videoPath)
        assertEquals("Test Video", vs.videoTitle)
    }

    @Test
    fun `Stop message serializes and deserializes correctly`() {
        val original = SyncMessage.Stop
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Stop)
    }

    @Test
    fun `BufferState message serializes and deserializes correctly`() {
        val original = SyncMessage.BufferState(isBuffering = true)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.BufferState)
        assertEquals(true, (parsed as SyncMessage.BufferState).isBuffering)
    }

    @Test
    fun `Heartbeat message serializes and deserializes correctly`() {
        val ts = System.currentTimeMillis()
        val original = SyncMessage.Heartbeat(timestamp = ts)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Heartbeat)
        assertEquals(ts, (parsed as SyncMessage.Heartbeat).timestamp)
    }

    @Test
    fun `fromJson returns null for invalid JSON`() {
        assertNull(SyncMessage.fromJson("not json"))
    }

    @Test
    fun `fromJson returns null for unknown type`() {
        val json = JSONObject().apply {
            put("type", "unknown_type")
        }.toString()
        assertNull(SyncMessage.fromJson(json))
    }

    @Test
    fun `Play toJson produces correct JSON structure`() {
        val msg = SyncMessage.Play(42L)
        val json = JSONObject(msg.toJson())
        assertEquals("play", json.getString("type"))
        assertEquals(42L, json.getLong("position"))
    }

    @Test
    fun `Pause toJson produces correct JSON structure`() {
        val msg = SyncMessage.Pause(100L)
        val json = JSONObject(msg.toJson())
        assertEquals("pause", json.getString("type"))
        assertEquals(100L, json.getLong("position"))
    }

    @Test
    fun `VideoSelected toJson produces correct JSON structure`() {
        val msg = SyncMessage.VideoSelected("/path/to/video.mp4", "My Video")
        val json = JSONObject(msg.toJson())
        assertEquals("video_selected", json.getString("type"))
        assertEquals("/path/to/video.mp4", json.getString("path"))
        assertEquals("My Video", json.getString("title"))
    }

    @Test
    fun `Play message with zero position`() {
        val original = SyncMessage.Play(position = 0L)
        val parsed = SyncMessage.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(0L, (parsed as SyncMessage.Play).position)
    }

    @Test
    fun `Play message with max long position`() {
        val original = SyncMessage.Play(position = Long.MAX_VALUE)
        val parsed = SyncMessage.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(Long.MAX_VALUE, (parsed as SyncMessage.Play).position)
    }

    @Test
    fun `BufferState false roundtrip`() {
        val original = SyncMessage.BufferState(isBuffering = false)
        val parsed = SyncMessage.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(false, (parsed as SyncMessage.BufferState).isBuffering)
    }

    @Test
    fun `Disconnect message serializes and deserializes correctly`() {
        val original = SyncMessage.Disconnect
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.Disconnect)
    }

    @Test
    fun `Disconnect toJson produces correct JSON structure`() {
        val msg = SyncMessage.Disconnect
        val json = JSONObject(msg.toJson())
        assertEquals("disconnect", json.getString("type"))
    }

    @Test
    fun `ReturnToLobby message serializes and deserializes correctly`() {
        val original = SyncMessage.ReturnToLobby
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.ReturnToLobby)
    }

    @Test
    fun `ReturnToLobby toJson produces correct JSON structure`() {
        val msg = SyncMessage.ReturnToLobby
        val json = JSONObject(msg.toJson())
        assertEquals("return_to_lobby", json.getString("type"))
    }

    @Test
    fun `BufferCountdown message serializes and deserializes correctly`() {
        val original = SyncMessage.BufferCountdown(secondsRemaining = 4)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.BufferCountdown)
        assertEquals(4, (parsed as SyncMessage.BufferCountdown).secondsRemaining)
    }

    @Test
    fun `BufferCountdown toJson produces correct JSON structure`() {
        val msg = SyncMessage.BufferCountdown(3)
        val json = JSONObject(msg.toJson())
        assertEquals("buffer_countdown", json.getString("type"))
        assertEquals(3, json.getInt("seconds"))
    }

    @Test
    fun `BufferCountdown with zero seconds`() {
        val original = SyncMessage.BufferCountdown(secondsRemaining = 0)
        val parsed = SyncMessage.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(0, (parsed as SyncMessage.BufferCountdown).secondsRemaining)
    }

    @Test
    fun `RoleSwapRequest message serializes and deserializes correctly`() {
        val original = SyncMessage.RoleSwapRequest(requesterName = "Pixel 7")
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.RoleSwapRequest)
        assertEquals("Pixel 7", (parsed as SyncMessage.RoleSwapRequest).requesterName)
    }

    @Test
    fun `RoleSwapRequest toJson produces correct JSON structure`() {
        val msg = SyncMessage.RoleSwapRequest("Samsung Galaxy S24")
        val json = JSONObject(msg.toJson())
        assertEquals("role_swap_request", json.getString("type"))
        assertEquals("Samsung Galaxy S24", json.getString("requester"))
    }

    @Test
    fun `RoleSwapResponse accepted serializes and deserializes correctly`() {
        val original = SyncMessage.RoleSwapResponse(accepted = true)
        val json = original.toJson()
        val parsed = SyncMessage.fromJson(json)

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.RoleSwapResponse)
        assertEquals(true, (parsed as SyncMessage.RoleSwapResponse).accepted)
    }

    @Test
    fun `RoleSwapResponse rejected serializes and deserializes correctly`() {
        val original = SyncMessage.RoleSwapResponse(accepted = false)
        val parsed = SyncMessage.fromJson(original.toJson())

        assertNotNull(parsed)
        assertTrue(parsed is SyncMessage.RoleSwapResponse)
        assertEquals(false, (parsed as SyncMessage.RoleSwapResponse).accepted)
    }

    @Test
    fun `RoleSwapResponse toJson produces correct JSON structure`() {
        val msg = SyncMessage.RoleSwapResponse(true)
        val json = JSONObject(msg.toJson())
        assertEquals("role_swap_response", json.getString("type"))
        assertEquals(true, json.getBoolean("accepted"))
    }
}
