package com.watchtogether.network.sync

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncClientTest {

    @Test
    fun `text frame is parsed as text`() {
        val json = """{"type":"stop"}"""
        val frame = SyncClient.readWebSocketFrame(ByteArrayInputStream(frameBytes(0x1, json.toByteArray())))

        assertEquals(WsFrame.Text(json), frame)
    }

    @Test
    fun `masked text frame is unmasked`() {
        val payload = "masked".toByteArray()
        val frame = SyncClient.readWebSocketFrame(
            ByteArrayInputStream(frameBytes(0x1, payload, masked = true))
        )

        assertEquals(WsFrame.Text("masked"), frame)
    }

    @Test
    fun `close frame returns Close and is not parsed as text`() {
        assertEquals(
            WsFrame.Close,
            SyncClient.readWebSocketFrame(ByteArrayInputStream(byteArrayOf(0x88.toByte(), 0)))
        )
    }

    @Test
    fun `ping frame returns Ping with payload`() {
        val payload = byteArrayOf(1, 2, 3)
        val frame = SyncClient.readWebSocketFrame(ByteArrayInputStream(frameBytes(0x9, payload)))

        assertTrue(frame is WsFrame.Ping)
        assertArrayEquals(payload, (frame as WsFrame.Ping).payload)
    }

    @Test
    fun `pong frame is ignored`() {
        assertEquals(
            WsFrame.Pong,
            SyncClient.readWebSocketFrame(ByteArrayInputStream(byteArrayOf(0x8A.toByte(), 0)))
        )
    }

    @Test
    fun `unknown opcode returns Other`() {
        assertEquals(
            WsFrame.Other(3),
            SyncClient.readWebSocketFrame(ByteArrayInputStream(byteArrayOf(0x83.toByte(), 0)))
        )
    }

    @Test
    fun `16-bit extended length is parsed`() {
        val payload = ByteArray(300) { 'a'.code.toByte() }
        val frame = SyncClient.readWebSocketFrame(ByteArrayInputStream(frameBytes(0x1, payload)))

        assertEquals(WsFrame.Text("a".repeat(300)), frame)
    }

    @Test
    fun `64-bit extended length is parsed`() {
        val payload = ByteArray(70_000) { 'b'.code.toByte() }
        val frame = SyncClient.readWebSocketFrame(ByteArrayInputStream(frameBytes(0x1, payload)))

        assertEquals(WsFrame.Text("b".repeat(70_000)), frame)
    }

    @Test
    fun `end of stream returns null`() {
        assertNull(SyncClient.readWebSocketFrame(ByteArrayInputStream(ByteArray(0))))
        assertNull(
            SyncClient.readWebSocketFrame(
                ByteArrayInputStream(byteArrayOf(0x81.toByte(), 0x7E))
            )
        )
        assertNull(
            SyncClient.readWebSocketFrame(
                ByteArrayInputStream(byteArrayOf(0x81.toByte(), 3, 'a'.code.toByte()))
            )
        )
    }

    private fun frameBytes(opcode: Int, payload: ByteArray, masked: Boolean = false): ByteArray {
        val header = ArrayList<Byte>()
        header.add((0x80 or opcode).toByte())
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val encodedPayload = if (masked) {
            ByteArray(payload.size) { index ->
                (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
        } else {
            payload
        }
        val maskBit = if (masked) 0x80 else 0
        when {
            payload.size < 126 -> header.add((maskBit or payload.size).toByte())
            payload.size < 65536 -> {
                header.add((maskBit or 126).toByte())
                header.add((payload.size shr 8).toByte())
                header.add(payload.size.toByte())
            }
            else -> {
                header.add((maskBit or 127).toByte())
                for (shift in 56 downTo 0 step 8) {
                    header.add((payload.size.toLong() shr shift).toByte())
                }
            }
        }
        if (masked) {
            mask.forEach(header::add)
        }
        encodedPayload.forEach(header::add)
        return ByteArray(header.size) { header[it] }
    }
}
