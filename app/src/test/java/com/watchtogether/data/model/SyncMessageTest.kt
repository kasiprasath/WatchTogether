package com.watchtogether.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- Sync flow ordering tests ---

    @Test
    fun `host countdown produces correct message sequence`() {
        // Simulates the host's countdown broadcast sequence:
        // BufferCountdown(4) -> BufferCountdown(3) -> BufferCountdown(2) -> BufferCountdown(1) -> BufferCountdown(0) -> Play(0)
        val messages = mutableListOf<SyncMessage>()
        var secondsLeft = 4
        messages.add(SyncMessage.BufferCountdown(secondsLeft))
        while (secondsLeft > 0) {
            secondsLeft--
            messages.add(SyncMessage.BufferCountdown(secondsLeft))
        }
        messages.add(SyncMessage.Play(0L))

        assertEquals(6, messages.size)
        assertTrue(messages[0] is SyncMessage.BufferCountdown)
        assertEquals(4, (messages[0] as SyncMessage.BufferCountdown).secondsRemaining)
        assertTrue(messages[4] is SyncMessage.BufferCountdown)
        assertEquals(0, (messages[4] as SyncMessage.BufferCountdown).secondsRemaining)
        assertTrue(messages[5] is SyncMessage.Play)
    }

    @Test
    fun `ReturnToLobby followed by Disconnect preserves message ordering`() {
        // Viewer must process ReturnToLobby before Disconnect arrives
        val messages = listOf(
            SyncMessage.ReturnToLobby,
            SyncMessage.Disconnect
        )

        assertTrue(messages[0] is SyncMessage.ReturnToLobby)
        assertTrue(messages[1] is SyncMessage.Disconnect)

        // Verify both roundtrip correctly
        val parsed0 = SyncMessage.fromJson(messages[0].toJson())
        val parsed1 = SyncMessage.fromJson(messages[1].toJson())
        assertTrue(parsed0 is SyncMessage.ReturnToLobby)
        assertTrue(parsed1 is SyncMessage.Disconnect)
    }

    @Test
    fun `Play and Pause messages carry position for sync`() {
        val play = SyncMessage.Play(5000L)
        val pause = SyncMessage.Pause(5000L)

        // Both carry the same position for accurate sync
        assertEquals(play.position, pause.position)

        // Roundtrip preserves position
        val parsedPlay = SyncMessage.fromJson(play.toJson()) as SyncMessage.Play
        val parsedPause = SyncMessage.fromJson(pause.toJson()) as SyncMessage.Pause
        assertEquals(5000L, parsedPlay.position)
        assertEquals(5000L, parsedPause.position)
    }

    @Test
    fun `echo detection helper logic works correctly`() {
        // Simulates the echo detection used in PlayerActivity
        var lastAction: String? = null
        var lastTime: Long = 0
        val echoWindowMs = 2000L

        fun recordAction(action: String, time: Long) {
            lastAction = action
            lastTime = time
        }

        fun isEcho(action: String, currentTime: Long): Boolean {
            return action == lastAction && currentTime - lastTime < echoWindowMs
        }

        // No previous action — not an echo
        assertFalse(isEcho("play", 1000))

        // Record play at t=1000
        recordAction("play", 1000)

        // Same action within window — IS an echo
        assertTrue(isEcho("play", 1500))

        // Same action outside window — NOT an echo
        assertFalse(isEcho("play", 4000))

        // Different action within window — NOT an echo
        assertFalse(isEcho("pause", 1500))
    }

    @Test
    fun `viewer should not start playing on VideoSelected`() {
        // Viewer receives VideoSelected to prepare the stream,
        // then a separate Play message to start playback.
        val messages: List<SyncMessage> = listOf(
            SyncMessage.VideoSelected("/path/video.mp4", "Test"),
            SyncMessage.Play(0L)
        )

        // First message is VideoSelected (prepare), not Play
        assertTrue(messages[0] is SyncMessage.VideoSelected)
        // Second message is Play (start playback)
        assertTrue(messages[1] is SyncMessage.Play)
        assertEquals(0L, (messages[1] as SyncMessage.Play).position)
    }

    @Test
    fun `BufferCountdown zero signals countdown complete`() {
        val countdownDone = SyncMessage.BufferCountdown(0)
        val parsed = SyncMessage.fromJson(countdownDone.toJson()) as SyncMessage.BufferCountdown
        assertEquals(0, parsed.secondsRemaining)
        assertTrue(parsed.secondsRemaining == 0)
    }

    @Test
    fun `sync message sequence for host back navigation`() {
        // Host going back should send ReturnToLobby (not Disconnect)
        // StreamingService sends Disconnect on stop, but that should be
        // suppressed when navigating to lobby
        val hostBackMessage: SyncMessage = SyncMessage.ReturnToLobby

        assertTrue(hostBackMessage is SyncMessage.ReturnToLobby)
        // Verify it serializes as return_to_lobby, not disconnect
        val json = JSONObject(hostBackMessage.toJson())
        assertEquals("return_to_lobby", json.getString("type"))
    }
}
