package com.watchtogether.network.server

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoStreamServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `valid range returns requested bounds`() {
        assertEquals(
            RangeResult.Satisfiable(0, 99),
            VideoStreamServer.parseRange("bytes=0-99", 1000)
        )
    }

    @Test
    fun `open-ended range is clamped to chunk size and file length`() {
        assertEquals(
            RangeResult.Satisfiable(10, 999),
            VideoStreamServer.parseRange("bytes=10-", 1000)
        )
        assertEquals(
            RangeResult.Satisfiable(10, 10 + VideoStreamServer.CHUNK_SIZE - 1),
            VideoStreamServer.parseRange("bytes=10-", 10_000_000)
        )
    }

    @Test
    fun `end beyond file length is clamped`() {
        assertEquals(
            RangeResult.Satisfiable(0, 999),
            VideoStreamServer.parseRange("bytes=0-5000", 1000)
        )
    }

    @Test
    fun `start beyond file length is unsatisfiable`() {
        assertEquals(
            RangeResult.Unsatisfiable,
            VideoStreamServer.parseRange("bytes=1000-1010", 1000)
        )
    }

    @Test
    fun `start greater than end is unsatisfiable`() {
        assertEquals(
            RangeResult.Unsatisfiable,
            VideoStreamServer.parseRange("bytes=500-100", 1000)
        )
    }

    @Test
    fun `empty file range is unsatisfiable`() {
        assertEquals(
            RangeResult.Unsatisfiable,
            VideoStreamServer.parseRange("bytes=0-", 0)
        )
    }

    @Test
    fun `openRangeStream serves bytes from large offset`() {
        val file = tmp.newFile("video.bin")
        val data = ByteArray(5_000_000)
        Random(1234L).nextBytes(data)
        file.writeBytes(data)

        VideoStreamServer.openRangeStream(file, 4_000_000).use { stream ->
            val actual = ByteArray(1000)
            assertEquals(1000, stream.read(actual))
            assertArrayEquals(data.copyOfRange(4_000_000, 4_001_000), actual)
        }

        VideoStreamServer.openRangeStream(file, 0).use { stream ->
            val actual = ByteArray(1000)
            assertEquals(1000, stream.read(actual))
            assertArrayEquals(data.copyOfRange(0, 1000), actual)
        }
    }
}
