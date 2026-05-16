package com.watchtogether.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun `buildStreamUrl returns correct HTTP URL`() {
        val url = NetworkUtils.buildStreamUrl("192.168.49.1", 8080)
        assertEquals("http://192.168.49.1:8080/video", url)
    }

    @Test
    fun `buildStreamUrl with default port`() {
        val url = NetworkUtils.buildStreamUrl("10.0.0.1")
        assertEquals("http://10.0.0.1:8080/video", url)
    }

    @Test
    fun `buildStreamUrl with custom port`() {
        val url = NetworkUtils.buildStreamUrl("192.168.1.100", 9090)
        assertEquals("http://192.168.1.100:9090/video", url)
    }

    @Test
    fun `buildSyncUrl returns correct WebSocket URL`() {
        val url = NetworkUtils.buildSyncUrl("192.168.49.1", 8081)
        assertEquals("ws://192.168.49.1:8081", url)
    }

    @Test
    fun `buildSyncUrl with default port`() {
        val url = NetworkUtils.buildSyncUrl("10.0.0.1")
        assertEquals("ws://10.0.0.1:8081", url)
    }

    @Test
    fun `buildSyncUrl with custom port`() {
        val url = NetworkUtils.buildSyncUrl("192.168.1.100", 9999)
        assertEquals("ws://192.168.1.100:9999", url)
    }

    @Test
    fun `buildStreamUrl with localhost`() {
        val url = NetworkUtils.buildStreamUrl("127.0.0.1", 8080)
        assertEquals("http://127.0.0.1:8080/video", url)
    }
}
